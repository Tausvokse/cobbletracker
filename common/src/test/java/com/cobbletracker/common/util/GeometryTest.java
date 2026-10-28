package com.cobbletracker.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GeometryTest {

    @Test
    void bearingFollowsMinecraftYaw() {
        
        assertEquals(0.0, Geometry.bearingDegrees(0, 10), 1e-6);
        
        assertEquals(-90.0, Geometry.bearingDegrees(10, 0), 1e-6);
        
        assertEquals(90.0, Geometry.bearingDegrees(-10, 0), 1e-6);
    }

    @Test
    void relativeAngleIsZeroWhenFacingTarget() {
        
        assertEquals(0.0, Geometry.relativeAngle(0, 10, 0f), 1e-6);
    }

    @Test
    void relativeAngleWrapsTo180Range() {
        double a = Geometry.relativeAngle(0, -10, 0f); 
        assertEquals(180.0, Math.abs(a), 1e-6);
    }

    @Test
    void normalizeWraps() {
        assertEquals(-90.0, Geometry.normalize180(270), 1e-6);
        assertEquals(10.0, Geometry.normalize180(370), 1e-6);
    }
}
