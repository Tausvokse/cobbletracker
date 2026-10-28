package com.cobbletracker.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ObfuscationTest {

    @Test
    void roundsToChunkCenter() {
        assertEquals(8, Obfuscation.toChunkCenter(0));
        assertEquals(8, Obfuscation.toChunkCenter(15));
        assertEquals(24, Obfuscation.toChunkCenter(16));
        assertEquals(-8, Obfuscation.toChunkCenter(-1));
        assertEquals(-8, Obfuscation.toChunkCenter(-16));
    }

    @Test
    void maybeObfuscateRespectsFlag() {
        assertEquals(1450, Obfuscation.maybeObfuscate(1450, true));
        assertEquals(Obfuscation.toChunkCenter(1450), Obfuscation.maybeObfuscate(1450, false));
    }
}
