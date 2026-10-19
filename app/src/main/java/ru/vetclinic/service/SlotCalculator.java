package ru.vetclinic.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Расчёт слотов записи: приёмы идут по 30 минут с 08:00, последний — в 19:30
 * (то же правило проверяет ограничение ck_appointment_slot в базе данных).
 */
public final class SlotCalculator {

    public static final LocalTime FIRST_SLOT = LocalTime.of(8, 0);
    public static final LocalTime LAST_SLOT = LocalTime.of(19, 30);
    public static final int SLOT_MINUTES = 30;

    private SlotCalculator() {
    }

    /** Все слоты рабочего дня. */
    public static List<LocalTime> allSlots() {
        List<LocalTime> slots = new ArrayList<>();
        for (LocalTime t = FIRST_SLOT; !t.isAfter(LAST_SLOT); t = t.plusMinutes(SLOT_MINUTES)) {
            slots.add(t);
        }
        return slots;
    }

    /** Корректно ли время начала приёма (08:00–19:30, ровно :00 или :30). */
    public static boolean isValidSlot(LocalTime t) {
        return !t.isBefore(FIRST_SLOT) && !t.isAfter(LAST_SLOT)
                && (t.getMinute() == 0 || t.getMinute() == 30) && t.getSecond() == 0 && t.getNano() == 0;
    }

    /**
     * Свободные слоты врача на день: все слоты минус занятые; на сегодня — только
     * ещё не наступившие; на прошедшие дни записи нет.
     */
    public static List<LocalTime> freeSlots(LocalDate day, Set<LocalTime> busy, LocalDateTime now) {
        List<LocalTime> result = new ArrayList<>();
        if (day.isBefore(now.toLocalDate())) {
            return result;
        }
        boolean today = day.equals(now.toLocalDate());
        for (LocalTime t : allSlots()) {
            if (busy.contains(t)) {
                continue;
            }
            if (today && !t.isAfter(now.toLocalTime())) {
                continue;
            }
            result.add(t);
        }
        return result;
    }
}
