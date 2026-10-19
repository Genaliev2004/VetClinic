package ru.vetclinic.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Модульные тесты расчёта слотов записи (правило BR-11). */
class SlotCalculatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 21);

    @Test
    void workingDayHas24Slots() {
        List<LocalTime> slots = SlotCalculator.allSlots();
        assertEquals(24, slots.size());
        assertEquals(LocalTime.of(8, 0), slots.get(0));
        assertEquals(LocalTime.of(19, 30), slots.get(23));
    }

    @Test
    void validSlotBoundaries() {
        assertTrue(SlotCalculator.isValidSlot(LocalTime.of(8, 0)));
        assertTrue(SlotCalculator.isValidSlot(LocalTime.of(19, 30)));
        assertFalse(SlotCalculator.isValidSlot(LocalTime.of(7, 30)));
        assertFalse(SlotCalculator.isValidSlot(LocalTime.of(20, 0)));
        assertFalse(SlotCalculator.isValidSlot(LocalTime.of(10, 15)));
        assertFalse(SlotCalculator.isValidSlot(LocalTime.of(10, 0, 5)));
    }

    @Test
    void busySlotsAreExcluded() {
        Set<LocalTime> busy = Set.of(LocalTime.of(10, 0), LocalTime.of(10, 30));
        List<LocalTime> free = SlotCalculator.freeSlots(DAY, busy, DAY.minusDays(1).atTime(12, 0));
        assertEquals(22, free.size());
        assertFalse(free.contains(LocalTime.of(10, 0)));
        assertTrue(free.contains(LocalTime.of(11, 0)));
    }

    @Test
    void todayOnlyFutureSlots() {
        List<LocalTime> free = SlotCalculator.freeSlots(DAY, Set.of(), LocalDateTime.of(DAY, LocalTime.of(18, 10)));
        assertEquals(List.of(LocalTime.of(18, 30), LocalTime.of(19, 0), LocalTime.of(19, 30)), free);
    }

    @Test
    void pastDayHasNoSlots() {
        assertTrue(SlotCalculator.freeSlots(DAY, Set.of(), DAY.plusDays(1).atStartOfDay()).isEmpty());
    }
}
