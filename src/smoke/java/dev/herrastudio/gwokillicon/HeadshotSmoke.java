package dev.herrastudio.gwokillicon;

import com.sgr792.gwo.entity.projectile.BulletEntity;
import com.sgr792.gwo.entity.projectile.ResolvedBallisticProfile;
import dev.herrastudio.gwokillicon.bridge.BulletHeadshotAccess;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

/** Exercises the installed GWO damage path and GD656 server/network/client renderer. */
@EventBusSubscriber(modid="gwo_gd656_headshot", value=Dist.CLIENT)
public final class HeadshotSmoke {
    private static boolean started, inputIsolated;
    private static int ticks, stage, stageTick, frames;
    private static String screenshot;
    private static double floor;
    private static UUID headVictim, bodyVictim;
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void clearUnavailableGwoAudio(ClientTickEvent.Post event) {
        // Isolated fixtures do not load GWO's protected sound pack. Keep its missing
        // SoundEvent from interrupting the GD656 network/HUD test; production excludes this.
        com.sgr792.gwo.client.HitFeedbackPlayer.clear();
    }
    @SubscribeEvent public static void isolate(RenderFrameEvent.Pre event) {
        long window=Minecraft.getInstance().getWindow().getWindow();
        if(!inputIsolated) {
            GLFW.glfwSetMouseButtonCallback(window,null); GLFW.glfwSetKeyCallback(window,null);
            GLFW.glfwSetCursorPosCallback(window,null); GLFW.glfwSetScrollCallback(window,null);
            inputIsolated=true;
        }
        if(GLFW.glfwGetWindowAttrib(window,GLFW.GLFW_VISIBLE)!=0) GLFW.glfwHideWindow(window);
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) throws Exception {
        frames++;
        if(!started && frames%240==0) {
            var mc=Minecraft.getInstance();
            log("SMOKE_STARTUP_SCREEN "+(mc.screen==null?"null":mc.screen.getClass().getName())
                    +" overlay="+(mc.getOverlay()==null?"null":mc.getOverlay().getClass().getName()));
            screenshot="smoke-startup.png";
        }
        if(screenshot==null) return;
        try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(Path.of(screenshot));
        }
        screenshot=null;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        var mc=Minecraft.getInstance();
        if(!started && mc.screen!=null
                && mc.screen.getClass().getName().equals("tailorworks.betterbloodoverlay.PresetSetupScreen")) {
            // Complete only this isolated client's first-run prompt using its visible default button.
            for(var child:mc.screen.children()) if(child instanceof net.minecraft.client.gui.components.Button button
                    && button.getMessage().getString().equals("Skip (keep defaults)")) {
                button.onPress(); break;
            }
        }
        if(!started && mc.screen instanceof TitleScreen && mc.getOverlay()==null) {
            started=true; mc.getWindow().setWindowed(1440,900); mc.options.guiScale().set(2);
            mc.options.renderDistance().set(3); mc.options.pauseOnLostFocus=false; mc.resizeDisplay();
            var rules=new GameRules(); rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            var settings=new LevelSettings("GWO GD656 compatibility QA",GameType.SURVIVAL,false,
                    Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("headshot-smoke-"+System.currentTimeMillis(),settings,
                    new WorldOptions(9,false,false), registry->registry.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
        }
        if(!started || mc.player==null || ticks==0 && mc.screen!=null) return;
        ticks++;
        if(ticks>700) throw new IllegalStateException("Headshot native test timed out at "+stage);
        if(ticks==20) {
            server(player -> {
                floor=player.getY(); player.teleportTo(player.serverLevel(),0,floor,0,0,0);
                player.getInventory().clearContent();
                var definitions=new java.util.LinkedHashMap<ResourceLocation,com.sgr792.gwo.content.WeaponDefinition>(
                        com.sgr792.gwo.content.WeaponContentRegistry.weaponDefinitions());
                var id=ResourceLocation.fromNamespaceAndPath("headshot_smoke","test_rifle");
                var definition=com.sgr792.gwo.content.GunDefinition.fromJson(com.google.gson.JsonParser.parseString("""
                        {"display_name":"兼容测试枪","weapon_type":"firearm","magazine_size":30,
                         "damage":40,"range":250,"fire_modes":["semi"],
                         "mechanics":{"rpm":600,"fire_interval_ms":100},
                         "ballistics":{"muzzle_velocity":400,"gravity":0,"life_seconds":3},
                         "bullet_tracer":{"enabled":false},"first_person_arms":false}
                        """).getAsJsonObject());
                definitions.put(id,new com.sgr792.gwo.content.FirearmDefinition(definition));
                com.sgr792.gwo.content.WeaponContentRegistry.installBootstrapDefinitions(definitions);
                var weapon=com.sgr792.gwo.GwoMod.weaponStack(id);
                com.sgr792.gwo.item.GunData.initialize(weapon,id,com.sgr792.gwo.content.WeaponContentRegistry.get(id));
                weapon.set(DataComponents.CUSTOM_NAME,Component.literal("兼容测试枪"));
                player.getInventory().setItem(0,weapon); player.getInventory().selected=0;
            });
            stage=1; stageTick=ticks;
        }
        if(stage==1 && ticks-stageTick==15) {
            GuiProbe.clearScrollingIcons();
            server(player -> {
                clearTargets(player);
                var target=target(player,5,0,"爆头测试"); headVictim=target.getUUID();
                prepareLethal(target);
                var bullet=fire(player,1.7,40,1,0);
                assertPending(headVictim,2);
                require(!target.isAlive(),"Real headshot did not kill");
                require(!((BulletHeadshotAccess)bullet).wasHeadshot(headVictim),"Completed impact kept stale headshot state");
            });
            stage=2; stageTick=ticks;
        }
        if(stage==2 && ticks-stageTick>=10 && GuiProbe.hasActiveScrollingIcon(1)) {
            require(GuiProbe.scrollingKillTypes().equals(List.of(1)),"Head kill produced duplicate or wrong icon: "+GuiProbe.scrollingKillTypes());
            require(GuiProbe.scrollingTextureKey(1).equals("headshot"),"Head kill selected default texture");
            log("GD_GWO_HEADSHOT_PASS: real bullet kill damageType2 -> actual S2C -> one headshot icon/texture");
            screenshot="gd-gwo-headshot.png"; stage=3; stageTick=ticks;
        }
        if(stage==3 && ticks-stageTick==30) {
            GuiProbe.clearScrollingIcons();
            server(player -> {
                clearTargets(player); var target=target(player,5,0,"身体测试"); bodyVictim=target.getUUID();
                prepareLethal(target);
                fire(player,.85,40,1,0); assertPending(bodyVictim,0); require(!target.isAlive(),"Body shot did not kill");
            });
            stage=4; stageTick=ticks;
        }
        if(stage==4 && ticks-stageTick>=10 && GuiProbe.hasActiveScrollingIcon(0)) {
            require(GuiProbe.scrollingKillTypes().equals(List.of(0)),"Body kill was mislabelled/duplicated: "+GuiProbe.scrollingKillTypes());
            require(GuiProbe.scrollingTextureKey(0).equals("default"),"Body kill selected head texture");
            log("GD_GWO_BODYSHOT_PASS: original normal kill flow, one default texture/icon");
            screenshot="gd-gwo-bodyshot.png"; stage=5; stageTick=ticks;
        }
        if(stage==5 && ticks-stageTick==30) {
            GuiProbe.clearScrollingIcons();
            server(player -> {
                clearTargets(player); var target=target(player,5,0,"连续命中测试"); bodyVictim=target.getUUID();
                var first=fire(player,1.7,2,1,0);
                require(target.isAlive() && target.getHealth()<20,"Nonlethal fixture damaged incorrectly");
                if(!net.neoforged.fml.ModList.get().isLoaded("bodydamage"))
                    require(Math.abs(target.getHealth()-16)<.001,"Bridge changed original headshot damage");
                require(Integer.valueOf(2).equals(recordedType(bodyVictim)),"Nonlethal head classification missing");
                require(!((BulletHeadshotAccess)first).wasHeadshot(bodyVictim),"Nonlethal head left state active");
                target.invulnerableTime=0;
                prepareLethal(target);
                fire(player,.85,40,1,0); assertPending(bodyVictim,0);
            });
            stage=6; stageTick=ticks;
        }
        if(stage==6 && ticks-stageTick>=10 && GuiProbe.hasActiveScrollingIcon(0)) {
            require(!GuiProbe.scrollingKillTypes().contains(1),"Old nonlethal headshot leaked into body kill");
            log("GD_GWO_STALE_HIT_PASS: original nonlethal damage, next body fatal uses default icon");
            stage=7; stageTick=ticks;
        }
        if(stage==7 && ticks-stageTick==30) {
            GuiProbe.clearScrollingIcons();
            server(player -> {
                clearTargets(player);
                var first=target(player,4,0,"穿透爆头"); var second=target(player,8,.75,"穿透身体");
                prepareLethal(first); prepareLethal(second);
                headVictim=first.getUUID(); bodyVictim=second.getUUID();
                var bullet=fire(player,1.7,40,2,0);
                require(!first.isAlive() && !second.isAlive(),"Piercing did not hit both entities");
                assertPending(headVictim,2); assertPending(bodyVictim,0);
                require(!((BulletHeadshotAccess)bullet).wasHeadshot(headVictim),"Piercing context leaked first target");
            });
            stage=8; stageTick=ticks;
        }
        if(stage==8 && ticks-stageTick>=15 && GuiProbe.scrollingKillTypes().size()>=2) {
            var types=GuiProbe.scrollingKillTypes();
            require(types.size()==2 && types.contains(1) && types.contains(0),"Piercing icons mixed/duplicated: "+types);
            log("GD_GWO_PIERCING_PASS: same bullet first head/second body, distinct victims, exactly two matching icons");
            stage=9; stageTick=ticks;
        }
        if(stage==9 && ticks-stageTick==30) {
            GuiProbe.clearScrollingIcons();
            server(player -> {
                clearTargets(player); var target=target(player,5,0,"穿甲爆头"); headVictim=target.getUUID();
                prepareLethal(target);
                fire(player,1.7,40,1,.5f); assertPending(headVictim,2);
            });
            stage=10; stageTick=ticks;
        }
        if(stage==10 && ticks-stageTick>=10 && GuiProbe.hasActiveScrollingIcon(1)) {
            log("GD_GWO_AP_PASS: armor-piercing GWO DamageSource follows same original head result");
            stage=11; stageTick=ticks;
        }
        if(stage==11 && ticks-stageTick==30) {
            server(player -> {
                clearTargets(player);
                try { BodyCompatChecks.verify(player); }
                catch(Exception error) { throw new IllegalStateException("Body compatibility test failed",error); }
            });
            log("GD_GWO_NATIVE_SMOKE_PASS: real GWO head/body/AP/piercing/nonlethal, GD server+client, no damage mutation/no duplicate feed");
            mc.stop();
        }
    }
    private static Mob target(ServerPlayer player,double z,double height,String name) {
        var target=EntityType.VILLAGER.create(player.serverLevel()); require(target!=null,"Villager fixture absent");
        target.moveTo(0,floor+height,z,180,0); target.setNoAi(true); target.setNoGravity(true);
        target.setCustomName(Component.literal(name)); player.serverLevel().addFreshEntity(target); return target;
    }
    private static void prepareLethal(Mob target) {
        // BodyDamage uses its fixed 8/4 table instead of the fixture's damage=40.
        if(net.neoforged.fml.ModList.get().isLoaded("bodydamage")) target.setHealth(2);
    }
    private static BulletEntity fire(ServerPlayer player,double height,float damage,int targets,float armor) {
        var type=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("gwo","bullet"));
        @SuppressWarnings("unchecked") var bullet=new BulletEntity((EntityType<? extends BulletEntity>)type,player.serverLevel());
        var profile=new ResolvedBallisticProfile(damage,20,0,1,1,60,250,armor,0,2,.2f,null,null,targets,1,1);
        bullet.setPos(0,floor+height,0); bullet.configure(player,profile,new Vec3(0,0,1),false,17,null,1,1,0xffffff);
        player.serverLevel().addFreshEntity(bullet);
        // GWO continues a penetrating projectile on subsequent flight ticks.
        for(int tick=0;tick<targets+2 && !bullet.isRemoved();tick++) bullet.tick();
        return bullet;
    }
    private static void assertPending(UUID victim,int type) {
        try {
            var matches=GuiProbe.pendingKills().stream().filter(kill->kill.victimId().equals(victim)).toList();
            require(matches.size()==1 && matches.getFirst().damageType()==type,"Wrong pending kill: "+matches+" wanted "+type);
        } catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static Integer recordedType(UUID victim) {
        try { return GuiProbe.recordedDamageType(victim); }
        catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static void clearTargets(ServerPlayer player) {
        player.serverLevel().getEntitiesOfClass(Entity.class,new AABB(-16,floor-8,-16,16,floor+8,32),
                entity->entity!=player).forEach(Entity::discard);
    }
    private static void server(Consumer<ServerPlayer> work) {
        var mc=Minecraft.getInstance(); var id=mc.player.getUUID();
        mc.getSingleplayerServer().submit(()->work.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id))).join();
    }
    private static void require(boolean okay,String message) { if(!okay) throw new IllegalStateException(message); }
    private static void log(String message) { LoggerFactory.getLogger("HeadshotSmoke").info(message); }
}
