package ru.vetclinic.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.vetclinic.config.AppConfig;
import ru.vetclinic.dao.AppointmentDao;
import ru.vetclinic.dao.OwnerDao;
import ru.vetclinic.dao.PetDao;
import ru.vetclinic.dao.ReferenceDao;
import ru.vetclinic.dao.ReportDao;
import ru.vetclinic.dao.ServiceDao;
import ru.vetclinic.db.Database;
import ru.vetclinic.db.DbException;
import ru.vetclinic.db.SqlFunction;
import ru.vetclinic.model.AppointmentInfo;
import ru.vetclinic.model.AppointmentStatus;
import ru.vetclinic.model.Owner;
import ru.vetclinic.model.Pet;
import ru.vetclinic.model.RefItem;
import ru.vetclinic.model.ServiceLine;
import ru.vetclinic.model.ServiceStatRow;
import ru.vetclinic.model.VetRevenueRow;
import ru.vetclinic.service.ClinicService;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Интеграционные тесты DAO и ограничений базы данных. Один и тот же набор
 * выполняется на каждой СУБД (наследники задают профиль из config/db.properties).
 *
 * <p>Требования: база создана скриптами 00–02 из каталога sql/. Каждый тест работает
 * в транзакции, которая в конце откатывается, поэтому тестовые данные не меняются.
 * Если СУБД недоступна или исключена параметром -Dtest.databases, тесты пропускаются.</p>
 */
abstract class AbstractIntegrationTest {

    protected Database db;
    protected Connection c;

    private final OwnerDao owners = new OwnerDao();
    private final PetDao pets = new PetDao();
    private final ServiceDao services = new ServiceDao();
    private final AppointmentDao appointments = new AppointmentDao();
    private final ReportDao reports = new ReportDao();
    private final ReferenceDao species = ReferenceDao.species();

    /** Код профиля СУБД: postgresql или mssql. */
    protected abstract String profile();

    @BeforeEach
    void connect() throws Exception {
        List<String> enabled = Arrays.asList(System.getProperty("test.databases", "postgresql,mssql").split(","));
        assumeTrue(enabled.contains(profile()), "СУБД " + profile() + " исключена из прогона (-Dtest.databases)");
        db = new Database(AppConfig.load().dbSettings(profile()));
        try {
            c = db.openConnection();
        } catch (SQLException e) {
            assumeTrue(false, "СУБД " + profile() + " недоступна: " + e.getMessage());
        }
        c.setAutoCommit(false);
    }

    @AfterEach
    void rollback() throws SQLException {
        if (c != null) {
            try {
                c.rollback();
            } catch (SQLException ignored) {
                // SQL Server сам откатывает транзакцию после ошибки в триггере
            }
            c.close();
        }
    }

    /** Выполняет действие, ожидая ошибку СУБД, и возвращает сообщение для пользователя. */
    private String errorOf(SqlFunction<Connection, ?> action) {
        try {
            action.apply(c);
        } catch (SQLException e) {
            return db.translate(e).getMessage();
        } catch (DbException e) {
            return e.getMessage();
        }
        return fail("Ожидалась ошибка базы данных");
    }

    // ================================================================ справочники и владельцы

    @Test
    void referenceCrud() throws SQLException {
        int id = species.insert(c, "Черепаха");
        assertTrue(species.findAll(c).stream().anyMatch(s -> s.id() == id && s.name().equals("Черепаха")));
        species.update(c, new RefItem(id, "Сухопутная черепаха"));
        assertTrue(species.findAll(c).stream().anyMatch(s -> s.name().equals("Сухопутная черепаха")));
        assertEquals(1, species.delete(c, id));
    }

    @Test
    void ownerGetsGeneratedIdAndCanBeFound() throws SQLException {
        int id = owners.insert(c, new Owner(null, "Тестов", "Тест", null, "+79990001122", null, null, null));
        assertTrue(id > 10, "id выдаёт IDENTITY после тестовых данных");
        Owner o = owners.findById(c, id);
        assertEquals("Тестов", o.lastName());
        assertNotNull(o.registeredOn(), "дата регистрации по умолчанию");
        assertEquals(id, owners.find(c, "0001122").get(0).id(), "поиск по части телефона");
        assertEquals(id, owners.find(c, "тестов").get(0).id(), "поиск по фамилии без учёта регистра");
    }

    @Test
    void duplicatePhoneHasFriendlyMessage() {
        String msg = errorOf(cn -> owners.insert(cn, new Owner(null, "Тест", "Тест", null, "+79031112201", null, null, null)));
        assertEquals("Владелец с таким телефоном уже зарегистрирован.", msg);
    }

    @Test
    void ownerWithPetsCannotBeDeleted() {
        assertEquals("Нельзя удалить владельца: у него есть питомцы.", errorOf(cn -> owners.delete(cn, 1)));
    }

    @Test
    void severalPetsWithoutChip() throws SQLException {
        // SQL Server: проверяет фильтрованный уникальный индекс (несколько NULL допустимы)
        pets.insert(c, new Pet(null, 3, 1, null, "Первый", null, "M", null, null, null));
        pets.insert(c, new Pet(null, 3, 1, null, "Второй", null, "F", null, null, null));
        assertEquals(3, pets.findByOwner(c, 3).size());
    }

    @Test
    void petFieldsRoundTrip() throws SQLException {
        Pet p = pets.findById(c, 1);
        assertEquals("Мурка", p.name());
        assertEquals("Кошка", p.speciesName());
        assertEquals("F", p.sex());
        assertEquals(LocalDate.of(2019, 4, 12), p.birthDate());
        assertEquals(0, new BigDecimal("4.20").compareTo(p.weightKg()));
        assertEquals("643094100000001", p.chipNumber(), "CHAR(15) без хвостовых пробелов");
    }

    // ================================================================ запись на приём

    @Test
    void vetCannotHaveTwoAppointmentsAtOnce() {
        String msg = errorOf(cn -> appointments.create(cn, 2, 1, LocalDateTime.of(2026, 10, 21, 10, 0), null));
        assertEquals("У врача уже есть приём на это время.", msg);
    }

    @Test
    void cancelledSlotIsFree() throws SQLException {
        assertTrue(appointments.busySlots(c, 3, LocalDate.of(2026, 10, 16)).isEmpty());
        int id = appointments.create(c, 13, 3, LocalDateTime.of(2026, 10, 16, 8, 0), "повторно");
        assertTrue(id > 22);
        assertEquals(List.of(LocalTime.of(8, 0)), List.copyOf(appointments.busySlots(c, 3, LocalDate.of(2026, 10, 16))));
    }

    @Test
    void inactiveVetIsRejectedByTrigger() {
        String msg = errorOf(cn -> appointments.create(cn, 1, 5, LocalDateTime.of(2026, 12, 1, 10, 0), null));
        assertEquals("Врач не работает в клинике: запись к нему невозможна", msg);
    }

    @Test
    void slotOutsideWorkingHoursIsRejected() {
        String msg = errorOf(cn -> appointments.create(cn, 1, 1, LocalDateTime.of(2026, 12, 1, 7, 30), null));
        assertEquals("Приём возможен с 08:00 до 19:30, в начале или в середине часа.", msg);
    }

    @Test
    void scheduleForDay() throws SQLException {
        List<AppointmentInfo> day = appointments.schedule(c, LocalDate.of(2026, 10, 21), null);
        assertEquals(2, day.size());
        assertEquals(LocalTime.of(10, 0), day.get(0).startTime().toLocalTime());
        assertEquals("Иванов С. П.", day.get(0).ownerName());
        assertEquals(1, appointments.schedule(c, LocalDate.of(2026, 10, 22), 7).size());
    }

    @Test
    void historyNewestFirst() throws SQLException {
        List<AppointmentInfo> h = appointments.history(c, 2);
        assertEquals(List.of(15, 2), h.stream().map(AppointmentInfo::id).toList());
        assertEquals(0, new BigDecimal("3200.00").compareTo(h.get(1).total()));
    }

    // ================================================================ проведение приёма

    @Test
    void visitIsCompletedWithPricesFromPriceList() throws SQLException {
        appointments.saveVisit(c, 17, "Контроль", "Гастрит в ремиссии",
                List.of(new ServiceLine(1, null, 1, null), new ServiceLine(7, null, 2, null)), true);
        AppointmentInfo a = appointments.findInfo(c, 17);
        assertEquals(AppointmentStatus.DONE, a.status());
        assertEquals("Гастрит в ремиссии", a.diagnosis());
        assertEquals(0, new BigDecimal("3400.00").compareTo(a.total()), "1200 + 2 × 1100");
    }

    @Test
    void savedLineKeepsItsPriceAfterPriceListChange() throws SQLException {
        appointments.saveVisit(c, 17, null, null, List.of(new ServiceLine(1, null, 1, null)), false);
        services.update(c, new ru.vetclinic.model.MedicalService(1, "Первичный осмотр", new BigDecimal("1500.00"), true));
        appointments.saveVisit(c, 17, null, null, List.of(new ServiceLine(1, null, 3, null)), false);
        ServiceLine line = appointments.lines(c, 17).get(0);
        assertEquals(3, line.quantity());
        assertEquals(0, new BigDecimal("1200.00").compareTo(line.price()), "цена на момент оказания не меняется");
    }

    @Test
    void historicalPricesDifferFromCurrent() throws SQLException {
        ServiceLine old = appointments.lines(c, 1).stream().filter(l -> l.serviceId() == 1).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("1000.00").compareTo(old.price()));
        assertEquals(0, new BigDecimal("1200.00").compareTo(services.findById(c, 1).price()));
    }

    @Test
    void inactiveServiceCannotBeAdded() {
        String msg = errorOf(cn -> {
            appointments.saveVisit(cn, 17, null, null, List.of(new ServiceLine(11, null, 1, null)), false);
            return null;
        });
        assertEquals("Услуга больше не оказывается: добавить её нельзя", msg);
    }

    @Test
    void doneVisitNeedsDiagnosis() {
        String msg = errorOf(cn -> {
            appointments.saveVisit(cn, 18, null, null, List.of(), true);
            return null;
        });
        assertEquals("Чтобы завершить приём, укажите диагноз.", msg);
    }

    @Test
    void appointmentWithServicesCannotBeCancelled() {
        String msg = errorOf(cn -> {
            appointments.saveVisit(cn, 17, null, null, List.of(new ServiceLine(2, null, 1, null)), false);
            return appointments.cancel(cn, 17);
        });
        assertEquals("По приёму уже оказаны услуги: отменить его нельзя", msg);
    }

    @Test
    void cancelOnlyScheduled() throws SQLException {
        assertEquals(1, appointments.cancel(c, 22));
        assertEquals(AppointmentStatus.CANCELLED, appointments.findInfo(c, 22).status());
        assertEquals(0, appointments.cancel(c, 1), "проведённый приём не отменяется");
    }

    @Test
    void cancelledAppointmentIsFinal() {
        String msg = errorOf(cn -> ru.vetclinic.db.Jdbc.update(cn,
                "UPDATE appointment SET status = 'SCHEDULED' WHERE appointment_id = 12", ps -> { }));
        assertEquals("Приём уже проведён или отменён: изменить его статус нельзя", msg);
    }

    @Test
    void deleteAppointmentCascadesToLines() throws SQLException {
        assertFalse(appointments.lines(c, 10).isEmpty());
        assertEquals(1, appointments.delete(c, 10));
        assertTrue(appointments.lines(c, 10).isEmpty());
        assertNull(appointments.findInfo(c, 10));
    }

    // ================================================================ отчёты

    @Test
    void vetRevenueReport() throws SQLException {
        List<VetRevenueRow> rows = reports.vetRevenue(c, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertEquals(7, rows.size(), "все врачи, даже без приёмов");
        BigDecimal total = rows.stream().map(VetRevenueRow::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("30550.00").compareTo(total));
        assertEquals("Соколова А. В.", rows.get(0).vetName());
        assertEquals(5, rows.get(0).visits());
    }

    @Test
    void popularServicesReport() throws SQLException {
        List<ServiceStatRow> rows = reports.serviceStats(c, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertEquals("Первичный осмотр", rows.get(0).serviceName());
        assertEquals(6, rows.get(0).times());
    }

    // ================================================================ транзакция прикладного слоя

    /**
     * ClinicService.saveVisit работает в своей транзакции (не в транзакции теста).
     * Вторая услуга снята с прайса, триггер отклоняет её — откатиться должно всё,
     * включая уже записанный диагноз и первую услугу. Данные при этом не меняются.
     */
    @Test
    void failedVisitIsRolledBackCompletely() throws SQLException {
        ClinicService service = new ClinicService(db);
        DbException e = assertThrows(DbException.class, () -> service.saveVisit(17, "жалоба", "диагноз",
                List.of(new ServiceLine(1, null, 1, null), new ServiceLine(11, null, 1, null)), true));
        assertEquals("Услуга больше не оказывается: добавить её нельзя", e.getMessage());

        AppointmentInfo after = service.appointment(17);
        assertEquals(AppointmentStatus.SCHEDULED, after.status());
        assertNull(after.diagnosis());
        assertTrue(service.lines(17).isEmpty());
    }
}
