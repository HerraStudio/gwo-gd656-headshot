package dev.herrastudio.gwokillicon.mixin;

import dev.herrastudio.gwokillicon.bridge.BulletHeadshotAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(targets = "org.mods.gd656killicon.server.event.ServerEventLogic", remap = false)
public abstract class GdKillIconMixin {
    @Inject(
            method = "determineDamageType(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/UUID;Lnet/minecraft/world/damagesource/DamageSource;)I",
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            remap = false
    )
    private static void gwoKillicon$headshotDamage(
            ServerPlayer attacker, UUID victimId, DamageSource source,
            CallbackInfoReturnable<Integer> result
    ) {
        if (source.getDirectEntity() instanceof BulletHeadshotAccess bullet
                && bullet.wasHeadshot(victimId)) {
            // GD656's internal damage type 2 feeds its existing headshot kill flow.
            result.setReturnValue(2);
        }
    }
}
