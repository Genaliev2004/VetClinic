package ru.vetclinic.service;

import org.junit.jupiter.api.Test;
import ru.vetclinic.config.DbSettings;
import ru.vetclinic.db.Database;
import ru.vetclinic.model.Owner;
import ru.vetclinic.model.Pet;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Проверки прикладного слоя, которые срабатывают до обращения к БД:
 * сервер для этих тестов не нужен (адрес заведомо недоступен).
 */
class ClinicServiceValidationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 12, 0);

    private final ClinicService service = new ClinicService(
            new Database(new DbSettings("postgresql", "PostgreSQL", "", "jdbc:postgresql://127.0.0.1:1/none",
                    "nobody", "", false)),
            Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));

    @Test
    void cannotBookInThePast() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.book(1, 1, NOW.minusDays(1).withHour(10).withMinute(0), null));
        assertEquals("Нельзя записать на прошедшее время.", e.getMessage());
    }

    @Test
    void cannotBookOutsideSlots() {
        assertThrows(ValidationException.class, () -> service.book(1, 1, NOW.plusDays(1).withHour(10).withMinute(15), null));
        assertThrows(ValidationException.class, () -> service.book(1, 1, NOW.plusDays(1).withHour(20).withMinute(0), null));
    }

    @Test
    void ownerWithBadPhoneIsRejectedBeforeDatabase() {
        Owner o = new Owner(null, "Тест", "Тест", null, "123", null, null, null);
        assertThrows(ValidationException.class, () -> service.saveOwner(o));
    }

    @Test
    void petBornInFutureIsRejected() {
        Pet p = new Pet(null, 1, 1, null, "Тест", null, "M", LocalDate.of(2026, 10, 20), null, null);
        assertThrows(ValidationException.class, () -> service.savePet(p));
    }

    @Test
    void reportPeriodMustBeOrdered() {
        assertThrows(ValidationException.class,
                () -> service.vetRevenue(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1)));
    }
}
