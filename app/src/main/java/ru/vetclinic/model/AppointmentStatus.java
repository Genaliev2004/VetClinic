package ru.vetclinic.model;

/** Статус приёма; код совпадает со значением столбца appointment.status. */
public enum AppointmentStatus {
    SCHEDULED("Запланирован"),
    DONE("Проведён"),
    CANCELLED("Отменён");

    private final String title;

    AppointmentStatus(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }

    @Override
    public String toString() {
        return title;
    }
}
