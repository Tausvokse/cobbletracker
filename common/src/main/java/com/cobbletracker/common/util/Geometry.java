package com.cobbletracker.common.util;

/** Minecraft yaw: 0 faces +Z; 90 faces -X. Bearings use the same convention. */
public final class Geometry {

    private Geometry() {
    }

    /** Horizontal distance (ignores Y), which is what the HUD label shows. */
    public static double horizontalDistance(double dx, double dz) {
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Compass bearing (degrees, MC yaw convention) from an origin to a target offset. */
    public static double bearingDegrees(double dx, double dz) {
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Camera-relative bearing in [-180, 180], positive to the right. */
    public static double relativeAngle(double dx, double dz, float cameraYaw) {
        return normalize180(bearingDegrees(dx, dz) - cameraYaw);
    }

    /** Wraps an angle into [−180, 180]. */
    public static double normalize180(double degrees) {
        double a = (degrees + 180) % 360;
        if (a < 0) {
            a += 360;
        }
        return a - 180;
    }
}
