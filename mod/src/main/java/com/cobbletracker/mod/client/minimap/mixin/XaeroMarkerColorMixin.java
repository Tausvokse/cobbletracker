package com.cobbletracker.mod.client.minimap.mixin;

import com.cobbletracker.mod.client.minimap.MinimapMarkers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "xaero.hud.minimap.waypoint.render.WaypointMapRenderer", remap = false)
public abstract class XaeroMarkerColorMixin {
    @WrapOperation(method = "drawIconOnGUI*", at = @At(value = "INVOKE",
            target = "Lxaero/hud/minimap/waypoint/WaypointColor;getHex()I"), require = 0, remap = false)
    private int cobbletracker$beamColor(@Coerce Object palette, Operation<Integer> original,
            @Coerce Object graphics, @Coerce Object helper, @Coerce Object waypoint) {
        return MinimapMarkers.xaeroColor(waypoint, original.call(palette));
    }

    @WrapOperation(method = "drawIconOnGUI*", at = @At(value = "INVOKE",
            target = "Lxaero/common/misc/Misc;drawNormalText"), require = 0, remap = false)
    private void cobbletracker$symbolColor(@Coerce Object pose, String text, float x, float y, int color,
            boolean shadow, @Coerce Object buffers, Operation<Void> original,
            @Coerce Object graphics, @Coerce Object helper, @Coerce Object waypoint) {
        int tint = MinimapMarkers.xaeroColor(waypoint, color);
        original.call(pose, text, x, y, tint | 0xFF000000, shadow, buffers);
    }
}
