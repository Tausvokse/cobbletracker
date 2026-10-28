package com.cobbletracker.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import kotlin.reflect.KCallable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.pokemon.SpeciesAdditions$AdditionParameterAdapter", remap = false)
public abstract class SpeciesAdditionsAdapterMixin {

    @WrapOperation(
        method = "<clinit>",
        at =@At(
            value = "INVOKE",
            target = "Lkotlin/reflect/jvm/KCallablesJvm;isAccessible(Lkotlin/reflect/KCallable;)Z"
        ),
        remap = false,
        require = 0
    )
    private static boolean cobbletracker$safeIsAccessible(KCallable<?> callable, Operation<Boolean> original) {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()) return original.call(callable);
        try {
            return original.call(callable);
        } catch (Throwable t) {
            return false;
        }
    }

    @WrapOperation(
        method = "<clinit>",
        at = @At(
            value = "INVOKE",
            target = "Lkotlin/reflect/jvm/KCallablesJvm;setAccessible(Lkotlin/reflect/KCallable;Z)V"
        ),
        remap = false,
        require = 0
    )
    private static void cobbletracker$safeSetAccessible(KCallable<?> callable, boolean accessible, Operation<Void> original) {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()) {
            original.call(callable, accessible);
            return;
        }
        try {
            original.call(callable, accessible);
        } catch (Throwable t) {
            // Development mappings may leave intermediary names in Kotlin metadata.
        }
    }
}
