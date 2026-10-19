package ru.vetclinic.model;

import java.time.LocalDate;

/** Владелец питомца (таблица owner). */
public record Owner(Integer id, String lastName, String firstName, String middleName,
                    String phone, String email, String address, LocalDate registeredOn) {

    /** Фамилия Имя Отчество. */
    public String fullName() {
        return lastName + " " + firstName + (middleName == null || middleName.isBlank() ? "" : " " + middleName);
    }

    @Override
    public String toString() {
        return fullName() + ", " + phone;
    }
}
