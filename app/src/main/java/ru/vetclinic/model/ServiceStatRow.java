package ru.vetclinic.model;

import java.math.BigDecimal;

/** Строка отчёта «Популярные услуги». */
public record ServiceStatRow(String serviceName, int times, BigDecimal revenue) {
}
