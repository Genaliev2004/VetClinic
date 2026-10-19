package ru.vetclinic.service;

import org.junit.jupiter.api.Test;
import ru.vetclinic.model.AppointmentInfo;
import ru.vetclinic.model.AppointmentStatus;
import ru.vetclinic.model.ServiceLine;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Модульные тесты формирования счёта. */
class InvoiceFormatterTest {

    private final AppointmentInfo appointment = new AppointmentInfo(10, LocalDateTime.of(2026, 8, 14, 9, 30),
            AppointmentStatus.DONE, 2, "Петров И. С.", "Хирург", 3, "Барсик", "Кошка", 2,
            "Смирнова Е. А.", "+79031112202", "Плановая кастрация", "Операция проведена", new BigDecimal("7200.00"));
    private final List<ServiceLine> lines = List.of(
            new ServiceLine(8, "Кастрация кота", 1, new BigDecimal("4500.00")),
            new ServiceLine(7, "Общий анализ крови", 1, new BigDecimal("1100.00")),
            new ServiceLine(2, "Повторный осмотр", 2, new BigDecimal("800.00")));

    @Test
    void totalIsSumOfPriceTimesQuantity() {
        assertEquals(new BigDecimal("7200.00"), InvoiceFormatter.total(lines));
    }

    @Test
    void moneyFormatUsesRussianStyle() {
        assertEquals("1 200,00", InvoiceFormatter.money(new BigDecimal("1200")));
        assertEquals("0,00", InvoiceFormatter.money(null));
    }

    @Test
    void windowsLineSeparatorIsUsedEverywhere() {
        String text = InvoiceFormatter.format(appointment, lines, "\r\n");
        assertTrue(text.contains("ИТОГО"));
        assertTrue(text.contains("7 200,00"));
        assertFalse(text.replace("\r\n", "").contains("\n"), "все переводы строк должны быть CRLF");
    }

    @Test
    void linuxLineSeparator() {
        String text = InvoiceFormatter.format(appointment, lines, "\n");
        assertFalse(text.contains("\r"));
        assertEquals("Счёт_10_2026-08-14.txt", InvoiceFormatter.fileName(appointment));
    }
}
