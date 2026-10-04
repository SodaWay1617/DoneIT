package com.doneit.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class WorkloadSettingsTest {
    private final WorkloadSettings settings = new WorkloadSettings(480, 720, 840);

    @Test
    void assignsColorByConfiguredThresholds() {
        assertEquals("green", settings.level(480));
        assertEquals("yellow", settings.level(481));
        assertEquals("yellow", settings.level(720));
        assertEquals("orange", settings.level(721));
        assertEquals("orange", settings.level(840));
        assertEquals("red", settings.level(841));
    }

    @Test
    void requiresIncreasingPositiveThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new WorkloadSettings(480, 480, 840));
        assertThrows(IllegalArgumentException.class, () -> new WorkloadSettings(0, 720, 840));
    }
}
