package ru.vetclinic.model;

import java.math.BigDecimal;

/** Строка отчёта «Выручка по врачам». */
public record VetRevenueRow(String vetName, String specializationName, int visits, BigDecimal revenue) {
}
