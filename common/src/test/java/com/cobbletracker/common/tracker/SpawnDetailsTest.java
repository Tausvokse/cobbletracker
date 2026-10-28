package com.cobbletracker.common.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpawnDetailsTest {

    @Test
    void countsPerfectIvsAndTheirTotal() {
        SpawnDetails d = new SpawnDetails(50, "female", "Adamant", "Levitate", "31/20/15/31/8/31", "");
        assertEquals(3, d.perfectIvs());
        assertEquals(136, d.ivTotal());
    }

    @Test
    void absentIvsReportMinusOneRatherThanZero() {
        
        assertEquals(-1, SpawnDetails.EMPTY.perfectIvs());
        assertEquals(-1, SpawnDetails.EMPTY.ivTotal());
    }

    @Test
    void aHandEditedFileDoesNotThrow() {
        SpawnDetails d = new SpawnDetails(5, "", "", "", "31/oops/15/31/8/31", "");
        assertEquals(3, d.perfectIvs());
        assertEquals(116, d.ivTotal());
    }

    @Test
    void emptyMeansNothingWorthShowing() {
        assertTrue(SpawnDetails.EMPTY.isEmpty());
        assertTrue(new SpawnDetails(0, "", "", "", "", "").isEmpty());
        assertFalse(new SpawnDetails(50, "", "", "", "", "").isEmpty());
        assertFalse(new SpawnDetails(0, "male", "", "", "", "").isEmpty());
    }

    @Test
    void textFieldsAreNormalisedAndNullSafe() {
        SpawnDetails d = new SpawnDetails(-5, "  FEMALE ", " Adamant", null, null, null);
        assertEquals(0, d.level());
        assertEquals("female", d.gender());
        assertEquals("adamant", d.nature());
        assertEquals("", d.ability());
        assertEquals("", d.ivs());
        assertEquals("", d.evs());
    }

    @Test
    void genderRendersAsASymbolOrNothing() {
        assertEquals("♀", new SpawnDetails(1, "female", "", "", "", "").genderSymbol());
        assertEquals("♂", new SpawnDetails(1, "male", "", "", "", "").genderSymbol());
        assertEquals("", new SpawnDetails(1, "genderless", "", "", "", "").genderSymbol());
        assertEquals("", new SpawnDetails(1, "", "", "", "", "").genderSymbol());
    }
}
