package ru.vetclinic.dao;

import ru.vetclinic.db.Jdbc;
import ru.vetclinic.model.ServiceStatRow;
import ru.vetclinic.model.VetRevenueRow;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** Отчёты с агрегатными запросами (GROUP BY, SUM, COUNT). Период: [from; to] включительно. */
public class ReportDao {

    /** Выручка и число проведённых приёмов по каждому врачу (врачи без приёмов — с нулями). */
    public List<VetRevenueRow> vetRevenue(Connection c, LocalDate from, LocalDate to) throws SQLException {
        return Jdbc.list(c, """
                SELECT v.last_name, v.first_name, v.middle_name, sp.specialization_name,
                       COUNT(DISTINCT a.appointment_id)           AS visits,
                       COALESCE(SUM(aps.price * aps.quantity), 0) AS revenue
                FROM vet v
                JOIN specialization sp            ON sp.specialization_id = v.specialization_id
                LEFT JOIN appointment a           ON a.vet_id = v.vet_id AND a.status = 'DONE'
                                                 AND a.start_time >= ? AND a.start_time < ?
                LEFT JOIN appointment_service aps ON aps.appointment_id = a.appointment_id
                GROUP BY v.vet_id, v.last_name, v.first_name, v.middle_name, sp.specialization_name
                ORDER BY revenue DESC, v.last_name
                """, ps -> {
            Jdbc.setTimestamp(ps, 1, from.atStartOfDay());
            Jdbc.setTimestamp(ps, 2, to.plusDays(1).atStartOfDay());
        }, rs -> new VetRevenueRow(
                AppointmentDao.shortName(rs.getString("last_name"), rs.getString("first_name"), rs.getString("middle_name")),
                rs.getString("specialization_name"), rs.getInt("visits"), rs.getBigDecimal("revenue")));
    }

    /** Сколько раз оказана каждая услуга за период и на какую сумму. */
    public List<ServiceStatRow> serviceStats(Connection c, LocalDate from, LocalDate to) throws SQLException {
        return Jdbc.list(c, """
                SELECT sv.service_name, SUM(aps.quantity) AS times, SUM(aps.price * aps.quantity) AS revenue
                FROM appointment_service aps
                JOIN service sv    ON sv.service_id = aps.service_id
                JOIN appointment a ON a.appointment_id = aps.appointment_id
                WHERE a.status = 'DONE' AND a.start_time >= ? AND a.start_time < ?
                GROUP BY sv.service_id, sv.service_name
                ORDER BY times DESC, revenue DESC, sv.service_name
                """, ps -> {
            Jdbc.setTimestamp(ps, 1, from.atStartOfDay());
            Jdbc.setTimestamp(ps, 2, to.plusDays(1).atStartOfDay());
        }, rs -> new ServiceStatRow(rs.getString("service_name"), rs.getInt("times"), rs.getBigDecimal("revenue")));
    }
}
