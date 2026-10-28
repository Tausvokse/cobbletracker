package com.cobbletracker.mod.client.minimap.mixin;

import com.cobbletracker.mod.client.minimap.MinimapMarkers;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.mamiyaotaru.voxelmap.WaypointManager", remap = false)
public abstract class VoxelMarkersMixin {
    @Inject(method = "getWaypoints", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void cobbletracker$temporaryPoints(CallbackInfoReturnable<ArrayList<?>> callback) {
        callback.setReturnValue(MinimapMarkers.voxelWaypoints(callback.getReturnValue()));
    }
}
