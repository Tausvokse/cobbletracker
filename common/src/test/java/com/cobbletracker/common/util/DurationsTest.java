package com.cobbletracker.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DurationsTest {

    @Test
    void countdownUsesAtMostTwoUnits() {
        assertEquals("45s", Durations.countdown(45));
        assertEquals("1m", Durations.countdown(60));
        assertEquals("12m 30s", Durations.countdown(750));
        assertEquals("2h", Durations.countdown(7200));
        assertEquals("2h 5m", Durations.countdown(7500));
        assertEquals("1d 2h", Durations.countdown(93600));
    }

    @Test
    void elapsedCountdownReadsAsNow() {
        
        assertEquals("now", Durations.countdown(0));
        assertEquals("now", Durations.countdown(-10));
    }

    @Test
    void percentKeepsPrecisionWhereItMatters() {
        assertEquals("50%", Durations.percent(0.5));
        assertEquals("5.0%", Durations.percent(0.05));
        assertEquals("0.50%", Durations.percent(0.005));
        assertEquals("0.04%", Durations.percent(0.0004));
        
        assertEquals("0.0004%", Durations.percent(0.000004));
        assertEquals("0%", Durations.percent(0));
    }
}
