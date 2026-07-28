package com.cobbletracker.common.util;

/**
 * Geometry helpers for the on-screen arrows: bearing to a target and the signed angle of that bearing
 * relative to where the camera is facing. No Minecraft types, so it's fully unit-testable.
 *
 * <p>Minecraft yaw convention: 0° faces +Z (south) and increases clockwise (90° = −X / west). Bearing
 * here is computed the same way so the two are directly comparable.
 */
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

    /**
     * Signed angle in degrees of a target's bearing relative to the camera yaw, normalized to
     * [−180, 180]. 0 means dead ahead, +90 to the right, −90 to the left - exactly what the arrow
     * widget rotates by.
     */
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
