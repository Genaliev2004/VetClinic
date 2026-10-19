package ru.vetclinic.model;

import java.time.LocalDate;

/** Врач (таблица vet). specializationName — из соединения с specialization. */
public record Vet(Integer id, int specializationId, String specializationName, String lastName, String firstName,
                  String middleName, String phone, LocalDate hireDate, boolean active) {

    /** Фамилия И. О. */
    public String shortName() {
        StringBuilder sb = new StringBuilder(lastName).append(' ').append(firstName.charAt(0)).append('.');
        if (middleName != null && !middleName.isBlank()) {
            sb.append(' ').append(middleName.charAt(0)).append('.');
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return shortName() + " — " + specializationName;
    }
}
