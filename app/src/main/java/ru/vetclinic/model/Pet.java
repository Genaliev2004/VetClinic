package ru.vetclinic.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;

/**
 * Питомец (таблица pet). speciesName заполняется при чтении
 * соединением с таблицей species и при сохранении не используется.
 */
public record Pet(Integer id, int ownerId, int speciesId, String speciesName, String name, String breed,
                  String sex, LocalDate birthDate, BigDecimal weightKg, String chipNumber) {

    public static final String MALE = "M";
    public static final String FEMALE = "F";

    public String sexTitle() {
        return MALE.equals(sex) ? "самец" : "самка";
    }

    /** Возраст в виде «3 г. 2 мес.»; пустая строка, если дата рождения неизвестна. */
    public String ageText(LocalDate today) {
        if (birthDate == null) {
            return "";
        }
        Period p = Period.between(birthDate, today);
        if (p.getYears() > 0) {
            return p.getYears() + " г. " + p.getMonths() + " мес.";
        }
        return p.getMonths() + " мес.";
    }

    @Override
    public String toString() {
        return name + " (" + speciesName + ")";
    }
}
