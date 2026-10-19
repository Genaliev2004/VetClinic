package ru.vetclinic.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Модульные тесты проверки ввода (повторяют ограничения CHECK базы данных). */
class ValidatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    @ParameterizedTest
    @ValueSource(strings = {"+79031112233", "89031112233", "8 (903) 111-22-33", "79031112233", " +7 903 111 22 33 "})
    void phoneIsNormalized(String input) {
        assertEquals("+79031112233", Validator.phone(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "12345", "+7903111223", "+790311122334", "+19031112233", "телефон"})
    void wrongPhoneIsRejected(String input) {
        assertThrows(ValidationException.class, () -> Validator.phone(input));
    }

    @Test
    void requiredTextIsTrimmedAndChecked() {
        assertEquals("Иванов", Validator.required("  Иванов ", "Фамилия", 50));
        assertThrows(ValidationException.class, () -> Validator.required("   ", "Фамилия", 50));
        assertThrows(ValidationException.class, () -> Validator.required("x".repeat(51), "Фамилия", 50));
        assertNull(Validator.optional("  ", "Отчество", 50));
    }

    @Test
    void emailIsOptionalButMustLookLikeAddress() {
        assertNull(Validator.email(""));
        assertEquals("a.b@mail.ru", Validator.email(" a.b@mail.ru "));
        assertThrows(ValidationException.class, () -> Validator.email("mail.ru"));
        assertThrows(ValidationException.class, () -> Validator.email("a@b"));
    }

    @Test
    void chipIsExactly15Digits() {
        assertNull(Validator.chip(""));
        assertEquals("643094100000001", Validator.chip("643094100000001"));
        assertThrows(ValidationException.class, () -> Validator.chip("64309410000000"));
        assertThrows(ValidationException.class, () -> Validator.chip("64309410000000A"));
    }

    @Test
    void weightAcceptsCommaAndChecksRange() {
        assertEquals(new BigDecimal("4.5"), Validator.weight("4,5"));
        assertNull(Validator.weight(" "));
        assertThrows(ValidationException.class, () -> Validator.weight("0"));
        assertThrows(ValidationException.class, () -> Validator.weight("1000"));
        assertThrows(ValidationException.class, () -> Validator.weight("1,234"));
        assertThrows(ValidationException.class, () -> Validator.weight("тяжёлый"));
    }

    @Test
    void priceIsNonNegativeWithKopecks() {
        assertEquals(new BigDecimal("1200.00"), Validator.price("1200"));
        assertEquals(new BigDecimal("1200.50"), Validator.price("1 200,5"));
        assertEquals(new BigDecimal("0.00"), Validator.price("0"));
        assertThrows(ValidationException.class, () -> Validator.price("-1"));
        assertThrows(ValidationException.class, () -> Validator.price("10,001"));
        assertThrows(ValidationException.class, () -> Validator.price(""));
    }

    @Test
    void pastDateRules() {
        assertEquals(TODAY, Validator.pastDate(TODAY, "Дата", true, TODAY));
        assertNull(Validator.pastDate(null, "Дата", false, TODAY));
        assertThrows(ValidationException.class, () -> Validator.pastDate(null, "Дата", true, TODAY));
        assertThrows(ValidationException.class, () -> Validator.pastDate(TODAY.plusDays(1), "Дата", true, TODAY));
        assertThrows(ValidationException.class, () -> Validator.pastDate(LocalDate.of(1979, 12, 31), "Дата", true, TODAY));
    }

    @Test
    void sexAndQuantity() {
        assertEquals("M", Validator.sex("M"));
        assertThrows(ValidationException.class, () -> Validator.sex("X"));
        assertEquals(100, Validator.quantity(100));
        assertThrows(ValidationException.class, () -> Validator.quantity(0));
        assertThrows(ValidationException.class, () -> Validator.quantity(101));
    }
}
