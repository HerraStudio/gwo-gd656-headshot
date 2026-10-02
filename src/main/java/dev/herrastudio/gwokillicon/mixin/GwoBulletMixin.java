package dev.herrastudio.gwokillicon.mixin;

import dev.herrastudio.gwokillicon.HeadshotContext;
import dev.herrastudio.gwokillicon.bridge.BulletHeadshotAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(targets = "com.sgr792.gwo.entity.projectile.BulletEntity", remap = false)
public abstract class GwoBulletMixin implements BulletHeadshotAccess {
    @Unique
    private final HeadshotContext gwoKillicon$impact = new HeadshotContext();

    @Inject(
            method = "onHitEntity(Lcom/sgr792/gwo/entity/projectile/BulletEntity$EntityImpact;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"),
            require = 1)
    private void gwoKillicon$beginImpact(CallbackInfo callback) {
        gwoKillicon$impact.begin();
    }

    @Inject(
            method = "isHeadshot(Lnet/minecraft/world/entity/Entity;Lcom/sgr792/gwo/entity/projectile/PlayerBodyHitboxResolver$BodyPart;Lnet/minecraft/world/phys/Vec3;)Z",
            at = @At("RETURN"),
            require = 1)
    private void gwoKillicon$captureOriginalResult(Entity target, @Coerce Object bodyPart, Vec3 location,
                                                  CallbackInfoReturnable<Boolean> callback) {
        gwoKillicon$impact.capture(target.getUUID(), callback.getReturnValueZ());
    }

    @Inject(
            method = "onHitEntity(Lcom/sgr792/gwo/entity/projectile/BulletEntity$EntityImpact;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("RETURN"),
            require = 1)
    private void gwoKillicon$endImpact(CallbackInfo callback) {
        gwoKillicon$impact.end();
    }

    @Override
    public boolean wasHeadshot(UUID victim) {
        return gwoKillicon$impact.wasHeadshot(victim);
    }
}
