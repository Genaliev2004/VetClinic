package ru.vetclinic.model;

import java.math.BigDecimal;

/** Услуга клиники из прайса (таблица service). */
public record MedicalService(Integer id, String name, BigDecimal price, boolean active) {

    @Override
    public String toString() {
        return name + " — " + price.toPlainString() + " ₽";
    }
}
