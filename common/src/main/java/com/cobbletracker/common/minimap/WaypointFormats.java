package com.cobbletracker.common.minimap;

public final class WaypointFormats {
    private static final int[] XAERO_PALETTE = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private WaypointFormats() {
    }

    public static String xaero(String species, int color, int x, int y, int z, String dimension) {
        String name = sanitize(species);
        if (name.isEmpty()) {
            name = "Pokemon";
        }
        name = name.substring(0, Math.min(32, name.length()));
        String initials = name.substring(0, Math.min(2, name.length()));
        String destination = switch (dimension) {
            case "minecraft:overworld" -> "overworld";
            case "minecraft:the_nether" -> "the_nether";
            case "minecraft:the_end" -> "the_end";
            default -> {
                String[] id = dimension.split(":", 2);
                if (id.length != 2) {
                    throw new IllegalArgumentException("Dimension must be namespaced: " + dimension);
                }
                String path = id[1].replace('/', '%');
                int last = path.length();
                while (last > 0 && path.charAt(last - 1) == '.') {
                    last--;
                }
                yield "dim%" + id[0] + "$" + path.substring(0, last) + ",".repeat(path.length() - last);
            }
        };
        return "xaero-waypoint:" + escape(name) + ":" + escape(initials) + ":" + x + ":" + y + ":" + z
                + ":" + nearestXaeroColor(color) + ":false:0:Internal-" + escape(destination) + "-waypoints";
    }

    public static String shared(String species, int x, int y, int z, String dimension) {
        return "[name:" + sanitize(species) + ", x:" + x + ", y:" + y + ", z:" + z + ", dim:" + dimension + "]";
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            result.append(c == ':' || c == ',' || c == '[' || c == ']' || c == '§' || c < ' ' ? ' ' : c);
        }
        return result.toString().trim();
    }

    private static String escape(String value) {
        return value.replace("-", "^min^").replace("_", "-").replace("*", "^ast^");
    }

    private static int nearestXaeroColor(int argb) {
        int best = 0;
        long nearest = Long.MAX_VALUE;
        for (int i = 0; i < XAERO_PALETTE.length; i++) {
            int candidate = XAERO_PALETTE[i];
            int r = ((argb >>> 16) & 255) - ((candidate >>> 16) & 255);
            int g = ((argb >>> 8) & 255) - ((candidate >>> 8) & 255);
            int b = (argb & 255) - (candidate & 255);
            long distance = (long) r * r + (long) g * g + (long) b * b;
            if (distance < nearest) {
                nearest = distance;
                best = i;
            }
        }
        return best;
    }
}
