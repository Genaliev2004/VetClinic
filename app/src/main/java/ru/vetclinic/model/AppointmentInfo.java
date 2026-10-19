package ru.vetclinic.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Приём со сведениями о враче, питомце и владельце — строка расписания,
 * истории болезни и заголовок счёта (соединение appointment, vet, pet,
 * species, owner и представления v_appointment_total).
 */
public record AppointmentInfo(int id, LocalDateTime startTime, AppointmentStatus status,
                              int vetId, String vetName, String specializationName,
                              int petId, String petName, String speciesName,
                              int ownerId, String ownerName, String ownerPhone,
                              String complaint, String diagnosis, BigDecimal total) {
}
