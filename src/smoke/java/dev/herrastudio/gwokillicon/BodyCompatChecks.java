package dev.herrastudio.gwokillicon;

import dev.herrastudio.gwokillicon.bridge.BulletHeadshotAccess;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Installed-mod integration checks, compiled only into the isolated smoke client. */
public final class BodyCompatChecks {
    private static final float HEALTH = 100;
    private static final double TANGENT = 5.5 / 16.0;
    private static final double GAP_HEIGHT = 9.5 / 16.0;
    private static final double HEAD_HEIGHT = 1.72;
    private static final double BODY_HEIGHT = 1.0;
    private final ServerPlayer shooter;
    private final ServerLevel level;
    private final BlockPos origin;
    private final Map<BlockPos, BlockState> originalBlocks = new HashMap<>();
    private final List<Entity> fixtures = new ArrayList<>();
    private final Class<?> bulletClass;
    private final Class<?> profileClass;
    private final Constructor<?> profileConstructor;
    private final Constructor<?> pointConstructor;
    private final Constructor<?> boxConstructor;
    private final Constructor<?> bonesConstructor;
    private final Class<? extends Enum> partClass;
    private final Method acceptBones;
    private final Method getBones;
    private final Method removeBones;
    private final Field trackedDamage;
    private Entity trackedBullet;
    private int incomingCount;
    private boolean canceled;
    private boolean precise;
    private String observedPart;
    private int assertions;
    private int shots;
    private final java.util.function.Consumer<LivingIncomingDamageEvent> incomingObserver = this::observe;

    private BodyCompatChecks(ServerPlayer shooter) throws ReflectiveOperationException {
        this.shooter = shooter;
        this.level = shooter.serverLevel();
        int height = Math.min(level.getMaxBuildHeight() - 8,
                Math.max(level.getMinBuildHeight() + 4, (int) Math.floor(shooter.getY()) + 12));
        origin = BlockPos.containing(shooter.getX() + 8, height, shooter.getZ() + 8);
        bulletClass = Class.forName("com.sgr792.gwo.entity.projectile.BulletEntity");
        profileClass = Class.forName("com.sgr792.gwo.entity.projectile.ResolvedBallisticProfile");
        Constructor<?> foundProfile = null;
        for (Constructor<?> candidate : profileClass.getConstructors()) {
            if (candidate.getParameterCount() == 16) foundProfile = candidate;
        }
        if (foundProfile == null) throw new IllegalStateException("GWO native ballistic fixture interface changed");
        profileConstructor = foundProfile;
        Class<?> point = Class.forName("dev.bodydamage.geometry.Point");
        pointConstructor = point.getConstructor(double.class, double.class, double.class);
        partClass = Class.forName("dev.bodydamage.BodyPart").asSubclass(Enum.class);
        boxConstructor = Class.forName("dev.bodydamage.geometry.BoneBox").getConstructor(
                partClass, point, point, point, point);
        Class<?> bones = Class.forName("dev.bodydamage.network.Messages$Bones");
        bonesConstructor = bones.getConstructor(int.class, java.util.UUID.class, List.class);
        Class<?> store = Class.forName("dev.bodydamage.geometry.BoneStore");
        acceptBones = store.getMethod("accept", ServerPlayer.class, bones);
        getBones = store.getMethod("get", Entity.class, Entity.class);
        removeBones = store.getMethod("remove", java.util.UUID.class);
        trackedDamage = Class.forName("dev.bodydamage.damage.DamageEngine").getDeclaredField("FIXED");
        trackedDamage.setAccessible(true);
    }

    /** Must be invoked on the server thread in the isolated run-smoke world. */
    public static void verify(ServerPlayer player) throws Exception {
        try {
            Class.forName("dev.bodydamage.damage.DamageEngine");
        } catch (ClassNotFoundException absent) {
            System.out.println("GWO_KILLICON_BODY_COMPAT_SKIP bodydamage_not_installed=true");
            return;
        }
        new BodyCompatChecks(player).run();
    }

    private void run() throws Exception {
        // BodyDamage registers its LOWEST observer during mod construction, before this one.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true,
                LivingIncomingDamageEvent.class, incomingObserver);
        try {
            clearCorridor();

            Mob repeated = target(4, HEAD_HEIGHT, HEALTH);
            shoot("head_nonlethal", repeated, "HEAD", HEAD_HEIGHT, 4, false, false);
            check(Math.abs(repeated.getHealth() - (HEALTH - 8)) < 0.001f,
                    "installed BodyDamage keeps its original eight HEAD damage");
            check(Integer.valueOf(2).equals(GuiProbe.recordedDamageType(repeated.getUUID())),
                    "nonlethal GWO head impact reaches GD656 damage type 2");
            check(pending(repeated) == null, "nonlethal HEAD does not generate a kill");

            // Keep the same victim and its GD656 last-damage state; only the next actual hit changes.
            repeated.setHealth(2);
            repeated.setPos(point(4, GAP_HEIGHT - BODY_HEIGHT, TANGENT));
            shoot("head_then_body_lethal", repeated, "BODY", BODY_HEIGHT, 4, false, true);
            check(pending(repeated) != null && pending(repeated).damageType() == 0,
                    "a later BODY lethal impact replaces the old nonlethal head classification");
            repeated.discard();

            Mob body = target(4, BODY_HEIGHT, HEALTH);
            shoot("body_nonlethal", body, "BODY", BODY_HEIGHT, 4, false, false);
            check(Math.abs(body.getHealth() - (HEALTH - 4)) < 0.001f,
                    "installed BodyDamage keeps its original four BODY damage");
            check(Integer.valueOf(0).equals(GuiProbe.recordedDamageType(body.getUUID())),
                    "nonlethal BODY retains GD656 normal damage classification");
            check(pending(body) == null, "nonlethal BODY does not generate a kill");
            body.discard();

            for (String part : List.of("HEAD", "BODY")) {
                double height = part.equals("HEAD") ? HEAD_HEIGHT : BODY_HEIGHT;
                Mob distant = target(19, height, 2);
                shoot("bars_gap_19m_" + part.toLowerCase(java.util.Locale.ROOT), distant,
                        part, height, 18, true, true);
                var kill = pending(distant);
                int expected = part.equals("HEAD") ? 2 : 0;
                check(kill != null && kill.damageType() == expected,
                        "19m precise-bars " + part + " kill has GD656 damage type " + expected);
                distant.discard();
            }
            System.out.println("GWO_KILLICON_BODY_COMPAT_PASS shots=" + shots + " assertions=" + assertions
                    + " native_bullet_ticks=true exact_bone_parts=true head_damage=8 body_damage=4"
                    + " bars_19m_multi_tick=true stale_head_then_body_cleared=true");
        } finally {
            NeoForge.EVENT_BUS.unregister(incomingObserver);
            for (Entity fixture : fixtures) fixture.discard();
            removeBones.invoke(null, shooter.getUUID());
            for (var original : originalBlocks.entrySet()) {
                level.setBlock(original.getKey(), original.getValue(), 2);
            }
        }
    }

    private Mob target(double distance, double hitHeight, float health) {
        Mob target = EntityType.VILLAGER.create(level);
        check(target != null, "native living target fixture exists");
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setSilent(true);
        target.setPos(point(distance, GAP_HEIGHT - hitHeight, TANGENT));
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH);
        target.setHealth(health);
        check(level.addFreshEntity(target), "target is registered in native server entity lookup");
        fixtures.add(target);
        return target;
    }

    private void shoot(String label, Mob target, String part, double hitHeight, double speed,
                       boolean bars, boolean lethal) throws Exception {
        removeBones.invoke(null, shooter.getUUID());
        Object box = bone(part, hitHeight);
        Object packet = bonesConstructor.newInstance(target.getId(), target.getUUID(), List.of(box));
        check((boolean) acceptBones.invoke(null, shooter, packet), label + " server accepts native bone snapshot");
        check(getBones.invoke(null, target, shooter) instanceof List<?> bones && bones.size() == 1,
                label + " snapshot is available for the actual shooter");
        BlockPos barrier = origin.offset(17, 0, 0);
        BlockState obstruction = bars ? Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true)
                : Blocks.AIR.defaultBlockState();
        // Keep the authored NORTH connection exactly like the original geometry regression.
        level.setBlock(barrier, obstruction, 2);
        Object profile = profileConstructor.newInstance(4.0f, speed, 0.0, 1.0, 1.0, 60, 250.0,
                0.0f, 0.0, 1.0f, 0.2f, null, null, 1, 1.0f, 1.0f);
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("gwo:bullet"));
        Entity bullet = (Entity) bulletClass.getConstructor(EntityType.class, Level.class).newInstance(type, level);
        fixtures.add(bullet);
        bullet.setPos(point(0, GAP_HEIGHT, TANGENT));
        bulletClass.getMethod("configure", LivingEntity.class, profileClass, Vec3.class, boolean.class,
                long.class, ResourceLocation.class, float.class, float.class, int.class)
                .invoke(bullet, shooter, profile, new Vec3(1, 0, 0), false, 0L, null, 1.0f, 1.0f, 0xffffff);
        check(level.addFreshEntity(bullet), label + " native bullet is registered");
        trackedBullet = bullet;
        incomingCount = 0;
        canceled = precise = false;
        observedPart = "";
        int ticks = 0;
        try {
            while (!bullet.isRemoved() && ticks < 30) {
                bullet.tick();
                ticks++;
            }
            shots++;
            check(bullet.isRemoved(), label + " actual flight terminated");
            check(incomingCount == 1 && !canceled && precise && part.equals(observedPart),
                    label + " real BodyDamage engine accepts exactly one precise " + part + " impact");
            check(lethal == target.isDeadOrDying(), label + " actual HP matches intended lethality");
            if (bars) check(ticks >= 2, label + " native flight crosses multiple ticks at 19m");
            check(bullet instanceof BulletHeadshotAccess access && !access.wasHeadshot(target.getUUID()),
                    label + " bridge impact flag is cleared after native tick returns");
            System.out.println("GWO_KILLICON_BODY_CASE " + label + " hp=" + target.getHealth()
                    + " ticks=" + ticks + " precisePart=" + observedPart + " canceled=" + canceled);
        } finally {
            trackedBullet = null;
            bullet.discard();
            level.setBlock(barrier, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object bone(String part, double height) throws ReflectiveOperationException {
        Object bodyPart = Enum.valueOf((Class) partClass, part);
        double halfHeight = part.equals("HEAD") ? 0.14 : 0.45;
        return boxConstructor.newInstance(bodyPart, pointConstructor.newInstance(0.0, height, 0.0),
                pointConstructor.newInstance(0.24, 0.0, 0.0), pointConstructor.newInstance(0.0, halfHeight, 0.0),
                pointConstructor.newInstance(0.0, 0.0, 0.24));
    }

    private void observe(LivingIncomingDamageEvent event) {
        if (event.getSource().getDirectEntity() != trackedBullet) return;
        incomingCount++;
        canceled = event.isCanceled();
        try {
            Object tracked = ((Map<?, ?>) trackedDamage.get(null)).get(event.getContainer());
            if (tracked == null) return;
            Method part = tracked.getClass().getDeclaredMethod("part");
            Method exact = tracked.getClass().getDeclaredMethod("precise");
            part.setAccessible(true);
            exact.setAccessible(true);
            observedPart = ((Enum<?>) part.invoke(tracked)).name();
            precise = (boolean) exact.invoke(tracked);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("BodyDamage integration probe interface changed", failure);
        }
    }

    private GuiProbe.PendingKill pending(Mob victim) throws ReflectiveOperationException {
        return GuiProbe.pendingKills().stream().filter(kill -> kill.victimId().equals(victim.getUUID()))
                .findFirst().orElse(null);
    }

    private void clearCorridor() {
        for (int x = -1; x <= 21; x++) {
            for (int y = -2; y <= 3; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos block = origin.offset(x, y, z);
                    originalBlocks.put(block, level.getBlockState(block));
                    if (!level.getBlockState(block).isAir()) level.setBlock(block, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private Vec3 point(double x, double y, double z) {
        return new Vec3(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
    }

    private void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        assertions++;
    }
}
