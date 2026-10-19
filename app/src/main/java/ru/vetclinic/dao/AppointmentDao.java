package ru.vetclinic.dao;

import ru.vetclinic.db.DbException;
import ru.vetclinic.db.Jdbc;
import ru.vetclinic.model.AppointmentInfo;
import ru.vetclinic.model.AppointmentStatus;
import ru.vetclinic.model.ServiceLine;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** DAO приёмов (таблицы appointment и appointment_service, представление v_appointment_total). */
public class AppointmentDao {

    private static final String SELECT_INFO = """
            SELECT a.appointment_id, a.start_time, a.status, a.complaint, a.diagnosis,
                   a.vet_id, v.last_name AS vet_last, v.first_name AS vet_first, v.middle_name AS vet_middle,
                   sp.specialization_name,
                   a.pet_id, p.pet_name, s.species_name,
                   o.owner_id, o.last_name AS owner_last, o.first_name AS owner_first,
                   o.middle_name AS owner_middle, o.phone AS owner_phone,
                   t.total_amount
            FROM appointment a
            JOIN vet v                 ON v.vet_id = a.vet_id
            JOIN specialization sp     ON sp.specialization_id = v.specialization_id
            JOIN pet p                 ON p.pet_id = a.pet_id
            JOIN species s             ON s.species_id = p.species_id
            JOIN owner o               ON o.owner_id = p.owner_id
            JOIN v_appointment_total t ON t.appointment_id = a.appointment_id
            """;

    /** Расписание на день: все врачи (vetId = null) или один врач. */
    public List<AppointmentInfo> schedule(Connection c, LocalDate day, Integer vetId) throws SQLException {
        String sql = SELECT_INFO + " WHERE a.start_time >= ? AND a.start_time < ?"
                + (vetId == null ? "" : " AND a.vet_id = ?")
                + " ORDER BY a.start_time, v.last_name";
        return Jdbc.list(c, sql, ps -> {
            Jdbc.setTimestamp(ps, 1, day.atStartOfDay());
            Jdbc.setTimestamp(ps, 2, day.plusDays(1).atStartOfDay());
            if (vetId != null) {
                ps.setInt(3, vetId);
            }
        }, AppointmentDao::mapInfo);
    }

    /** История приёмов питомца, новые сверху. */
    public List<AppointmentInfo> history(Connection c, int petId) throws SQLException {
        return Jdbc.list(c, SELECT_INFO + " WHERE a.pet_id = ? ORDER BY a.start_time DESC",
                ps -> ps.setInt(1, petId), AppointmentDao::mapInfo);
    }

    public AppointmentInfo findInfo(Connection c, int appointmentId) throws SQLException {
        return Jdbc.one(c, SELECT_INFO + " WHERE a.appointment_id = ?",
                ps -> ps.setInt(1, appointmentId), AppointmentDao::mapInfo);
    }

    /** Занятые слоты врача на день (неотменённые приёмы). */
    public Set<LocalTime> busySlots(Connection c, int vetId, LocalDate day) throws SQLException {
        List<LocalDateTime> times = Jdbc.list(c, """
                SELECT start_time FROM appointment
                WHERE vet_id = ? AND start_time >= ? AND start_time < ? AND status IN ('SCHEDULED', 'DONE')
                """, ps -> {
            ps.setInt(1, vetId);
            Jdbc.setTimestamp(ps, 2, day.atStartOfDay());
            Jdbc.setTimestamp(ps, 3, day.plusDays(1).atStartOfDay());
        }, rs -> Jdbc.getTimestamp(rs, "start_time"));
        Set<LocalTime> result = new TreeSet<>();
        times.forEach(t -> result.add(t.toLocalTime()));
        return result;
    }

    /** Запись на приём; возвращает код нового приёма. */
    public int create(Connection c, int petId, int vetId, LocalDateTime start, String complaint) throws SQLException {
        return Jdbc.insertReturningId(c, """
                INSERT INTO appointment (pet_id, vet_id, start_time, status, complaint)
                VALUES (?, ?, ?, 'SCHEDULED', ?)
                """, ps -> {
            ps.setInt(1, petId);
            ps.setInt(2, vetId);
            Jdbc.setTimestamp(ps, 3, start);
            Jdbc.setString(ps, 4, complaint);
        });
    }

    /** Отмена записи. Возвращает 0, если приём не в статусе «запланирован». */
    public int cancel(Connection c, int appointmentId) throws SQLException {
        return Jdbc.update(c, "UPDATE appointment SET status = 'CANCELLED' WHERE appointment_id = ? AND status = 'SCHEDULED'",
                ps -> ps.setInt(1, appointmentId));
    }

    public int delete(Connection c, int appointmentId) throws SQLException {
        // строки appointment_service удалит ON DELETE CASCADE
        return Jdbc.update(c, "DELETE FROM appointment WHERE appointment_id = ?", ps -> ps.setInt(1, appointmentId));
    }

    /** Строки счёта (оказанные услуги) приёма. */
    public List<ServiceLine> lines(Connection c, int appointmentId) throws SQLException {
        return Jdbc.list(c, """
                SELECT aps.service_id, sv.service_name, aps.quantity, aps.price
                FROM appointment_service aps
                JOIN service sv ON sv.service_id = aps.service_id
                WHERE aps.appointment_id = ?
                ORDER BY sv.service_name
                """, ps -> ps.setInt(1, appointmentId),
                rs -> new ServiceLine(rs.getInt("service_id"), rs.getString("service_name"),
                        rs.getInt("quantity"), rs.getBigDecimal("price")));
    }

    /**
     * Сохраняет результаты приёма: жалобу, диагноз и список оказанных услуг, а при
     * complete = true переводит приём в статус DONE. Вызывать внутри транзакции
     * (см. ClinicService.saveVisit) — при ошибке на любом шаге всё откатывается.
     * Строки услуг сравниваются со старыми: удалённые удаляются, у оставшихся меняется
     * только количество (цена на момент оказания сохраняется), новые вставляются
     * по текущей цене прайса.
     */
    public void saveVisit(Connection c, int appointmentId, String complaint, String diagnosis,
                          List<ServiceLine> newLines, boolean complete) throws SQLException {
        Jdbc.update(c, "UPDATE appointment SET complaint = ?, diagnosis = ? WHERE appointment_id = ?", ps -> {
            Jdbc.setString(ps, 1, complaint);
            Jdbc.setString(ps, 2, diagnosis);
            ps.setInt(3, appointmentId);
        });

        Map<Integer, ServiceLine> old = new HashMap<>();
        for (ServiceLine l : lines(c, appointmentId)) {
            old.put(l.serviceId(), l);
        }
        Map<Integer, ServiceLine> fresh = new HashMap<>();
        for (ServiceLine l : newLines) {
            fresh.merge(l.serviceId(), l, (a, b) -> a.withQuantity(a.quantity() + b.quantity()));
        }

        for (Integer serviceId : new ArrayList<>(old.keySet())) {
            if (!fresh.containsKey(serviceId)) {
                Jdbc.update(c, "DELETE FROM appointment_service WHERE appointment_id = ? AND service_id = ?", ps -> {
                    ps.setInt(1, appointmentId);
                    ps.setInt(2, serviceId);
                });
            }
        }
        for (ServiceLine l : fresh.values()) {
            ServiceLine before = old.get(l.serviceId());
            if (before == null) {
                // цену берём из прайса в момент оказания услуги
                int rows = Jdbc.update(c, """
                        INSERT INTO appointment_service (appointment_id, service_id, quantity, price)
                        SELECT ?, service_id, ?, price FROM service WHERE service_id = ?
                        """, ps -> {
                    ps.setInt(1, appointmentId);
                    ps.setInt(2, l.quantity());
                    ps.setInt(3, l.serviceId());
                });
                if (rows != 1) {
                    throw new DbException("Услуга с кодом " + l.serviceId() + " не найдена.", null);
                }
            } else if (before.quantity() != l.quantity()) {
                Jdbc.update(c, "UPDATE appointment_service SET quantity = ? WHERE appointment_id = ? AND service_id = ?",
                        ps -> {
                            ps.setInt(1, l.quantity());
                            ps.setInt(2, appointmentId);
                            ps.setInt(3, l.serviceId());
                        });
            }
        }

        if (complete) {
            int rows = Jdbc.update(c, "UPDATE appointment SET status = 'DONE' WHERE appointment_id = ? AND status = 'SCHEDULED'",
                    ps -> ps.setInt(1, appointmentId));
            if (rows != 1) {
                throw new DbException("Завершить можно только запланированный приём.", null);
            }
        }
    }

    // ------------------------------------------------------------ отображение строк

    private static AppointmentInfo mapInfo(ResultSet rs) throws SQLException {
        return new AppointmentInfo(
                rs.getInt("appointment_id"),
                Jdbc.getTimestamp(rs, "start_time"),
                AppointmentStatus.valueOf(rs.getString("status").trim()),
                rs.getInt("vet_id"),
                shortName(rs.getString("vet_last"), rs.getString("vet_first"), rs.getString("vet_middle")),
                rs.getString("specialization_name"),
                rs.getInt("pet_id"),
                rs.getString("pet_name"),
                rs.getString("species_name"),
                rs.getInt("owner_id"),
                shortName(rs.getString("owner_last"), rs.getString("owner_first"), rs.getString("owner_middle")),
                rs.getString("owner_phone"),
                rs.getString("complaint"),
                rs.getString("diagnosis"),
                rs.getBigDecimal("total_amount"));
    }

    /** «Иванов С. П.» */
    static String shortName(String last, String first, String middle) {
        StringBuilder sb = new StringBuilder(last).append(' ').append(first.charAt(0)).append('.');
        if (middle != null && !middle.isBlank()) {
            sb.append(' ').append(middle.charAt(0)).append('.');
        }
        return sb.toString();
    }
}
