package com.cobbletracker.mod.minimap;

import com.cobbletracker.mod.config.MinimapSettings;
import com.cobbletracker.mod.net.C2S_MinimapSupportPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Emits a temporary minimap waypoint for a tracked spawn onto whichever supported minimap mod the target
 * player actually runs. All three supported mods create a waypoint from a specially-formatted chat line
 * they parse on the client, so this stays a pure server-side, version-stable, dependency-free integration
 * - no reflection into the minimap mods and nothing to break when they update.
 *
 * <p>Each mod's format lives in its own small builder below, so adding another minimap (or fixing a
 * format) is a localized change:
 * <ul>
 *   <li><b>Xaero's Minimap</b> - {@code xaero-waypoint:name:initials:x:y:z:color:…}, which Xaero detects
 *       and turns into a temporary waypoint (the raw line is hidden when "detect server waypoints" is on).</li>
 *   <li><b>VoxelMap</b> - the {@code [name:…, x:…, y:…, z:…, dim:…]} share format.</li>
 *   <li><b>JourneyMap</b> - reads the same VoxelMap share format, so one line covers both when present.</li>
 * </ul>
 */
public final class MinimapWaypoints {

    private MinimapWaypoints() {
    }

    /**
     * Sends the waypoint line(s) for {@code player} given the minimap mods they advertised. The position
     * has already been through hide-exact-position upstream. Returns whether anything was emitted, so the
     * caller can say something rather than leave the player wondering why nothing happened. Never throws.
     */
    public static boolean send(ServerPlayer player, C2S_MinimapSupportPacket caps, MinimapSettings settings,
            String species, int color, int x, int y, int z, String dimension) {
        if (caps == null || !caps.any() || settings == null || !settings.enabled()) {
            return false;
        }
        boolean sent = false;
        try {
            if (caps.xaero() && settings.xaero()) {
                // Colour the waypoint dot to match the beam (category tier colour) unless disabled in config.
                int markerColor = settings.useBeamColor() ? color : 0xFFFFFFFF;
                player.sendSystemMessage(Component.literal(xaero(species, markerColor, x, y, z)));
                sent = true;
            }
            // VoxelMap and JourneyMap read the same share format - one line serves whichever is present.
            boolean voxel = caps.voxelmap() && settings.voxelmap();
            boolean journey = caps.journeymap() && settings.journeymap();
            if (voxel || journey) {
                player.sendSystemMessage(Component.literal(voxelShare(species, x, y, z, dimension)));
                sent = true;
            }
        } catch (Throwable ignored) {
            // A minimap line is a best-effort convenience; never let it disturb the spawn flow.
        }
        return sent;
    }

    /** Xaero's Minimap server-waypoint line. {@code color} is Xaero's 0-15 palette index. */
    private static String xaero(String species, int argb, int x, int y, int z) {
        String name = sanitize(species);
        String initials = name.isEmpty() ? "?" : name.substring(0, Math.min(2, name.length()));
        int idx = nearestXaeroColor(argb);
        // name:initials:x:y:z:color:disabled:type:set
        return "xaero-waypoint:" + name + ":" + initials + ":" + x + ":" + y + ":" + z
                + ":" + idx + ":false:0:gui.xaero_default";
    }

    /** VoxelMap / JourneyMap share format. */
    private static String voxelShare(String species, int x, int y, int z, String dimension) {
        return "[name:" + sanitize(species) + ", x:" + x + ", y:" + y + ", z:" + z
                + ", dim:" + dimension + "]";
    }

    /** Both formats are delimited by punctuation, so a name carrying any of it has to be flattened first. */
    private static String sanitize(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            out.append(c == ':' || c == ',' || c == '[' || c == ']' || c < ' ' ? ' ' : c);
        }
        return out.toString().trim();
    }

    /** Xaero's 16-colour waypoint palette (approximate RGB), for nearest-match from an ARGB tier colour. */
    private static final int[] XAERO_PALETTE = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private static int nearestXaeroColor(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int best = 0;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < XAERO_PALETTE.length; i++) {
            int pr = (XAERO_PALETTE[i] >> 16) & 0xFF;
            int pg = (XAERO_PALETTE[i] >> 8) & 0xFF;
            int pb = XAERO_PALETTE[i] & 0xFF;
            long d = (long) (r - pr) * (r - pr) + (long) (g - pg) * (g - pg) + (long) (b - pb) * (b - pb);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }
}
