package com.petey.addon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.petey.addon.modules.PeteysEspBypass;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Intercepts BlockEntityRenderDispatcher#isTooFar so that storage blocks
 * tracked by PeteysEspBypass are always rendered regardless of the server's
 * block-entity render distance cutoff.
 *
 * Intermediary reference:
 *   class_824  = BlockEntityRenderDispatcher
 *   class_827  = BlockEntityRenderer
 *   class_2586 = BlockEntity
 *   class_243  = Camera
 *   method_74348 = render (the dispatcher method that calls isTooFar)
 *   method_33892 = BlockEntityRenderer#isTooFar
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {

    @WrapOperation(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/block/entity/BlockEntityRenderer;isTooFar(Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/client/render/Camera;)Z"
        )
    )
    private <E extends BlockEntity> boolean petey$farStorage(
            BlockEntityRenderer<E> renderer,
            E be,
            Camera camera,
            Operation<Boolean> original) {
        // If the vanilla check says "too far", defer to our ESP bypass before hiding it.
        return original.call(renderer, be, camera) || PeteysEspBypass.rendersFar(be);
    }
}
