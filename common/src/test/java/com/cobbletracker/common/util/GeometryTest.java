package com.cobbletracker.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GeometryTest {

    @Test
    void bearingFollowsMinecraftYaw() {
        // +Z is south / yaw 0
        assertEquals(0.0, Geometry.bearingDegrees(0, 10), 1e-6);
        // +X is east / yaw -90
        assertEquals(-90.0, Geometry.bearingDegrees(10, 0), 1e-6);
        // -X is west / yaw 90
        assertEquals(90.0, Geometry.bearingDegrees(-10, 0), 1e-6);
    }

    @Test
    void relativeAngleIsZeroWhenFacingTarget() {
        // Target due south (dz>0), camera facing south (yaw 0) → dead ahead.
        assertEquals(0.0, Geometry.relativeAngle(0, 10, 0f), 1e-6);
    }

    @Test
    void relativeAngleWrapsTo180Range() {
        double a = Geometry.relativeAngle(0, -10, 0f); // target north, facing south
        assertEquals(180.0, Math.abs(a), 1e-6);
    }

    @Test
    void normalizeWraps() {
        assertEquals(-90.0, Geometry.normalize180(270), 1e-6);
        assertEquals(10.0, Geometry.normalize180(370), 1e-6);
    }
}
