package ru.vetclinic.service;

import ru.vetclinic.dao.AppointmentDao;
import ru.vetclinic.dao.OwnerDao;
import ru.vetclinic.dao.PetDao;
import ru.vetclinic.dao.ReferenceDao;
import ru.vetclinic.dao.ReportDao;
import ru.vetclinic.dao.ServiceDao;
import ru.vetclinic.dao.VetDao;
import ru.vetclinic.db.Database;
import ru.vetclinic.db.DbException;
import ru.vetclinic.model.AppointmentInfo;
import ru.vetclinic.model.AppointmentStatus;
import ru.vetclinic.model.MedicalService;
import ru.vetclinic.model.Owner;
import ru.vetclinic.model.Pet;
import ru.vetclinic.model.RefItem;
import ru.vetclinic.model.ServiceLine;
import ru.vetclinic.model.ServiceStatRow;
import ru.vetclinic.model.Vet;
import ru.vetclinic.model.VetRevenueRow;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Прикладной слой: всё, что делает пользовательский интерфейс, проходит через
 * этот класс. Здесь проверяются введённые данные, открываются соединения и
 * транзакции, вызываются DAO. Интерфейс не знает ни про JDBC, ни про SQL.
 */
public class ClinicService {

    private final Database db;
    private final Clock clock;

    private final ReferenceDao speciesDao = ReferenceDao.species();
    private final ReferenceDao specializationDao = ReferenceDao.specializations();
    private final OwnerDao ownerDao = new OwnerDao();
    private final PetDao petDao = new PetDao();
    private final VetDao vetDao = new VetDao();
    private final ServiceDao serviceDao = new ServiceDao();
    private final AppointmentDao appointmentDao = new AppointmentDao();
    private final ReportDao reportDao = new ReportDao();

    public ClinicService(Database db) {
        this(db, Clock.systemDefaultZone());
    }

    /** Конструктор с часами — чтобы в тестах можно было «зафиксировать» текущее время. */
    public ClinicService(Database db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    public Database database() {
        return db;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    // ================================================================ справочники

    public List<RefItem> species() {
        return db.run(speciesDao::findAll);
    }

    public int saveSpecies(RefItem item) {
        return saveRef(speciesDao, item, "Название вида", 40);
    }

    public void deleteSpecies(int id) {
        db.run(c -> speciesDao.delete(c, id));
    }

    public List<RefItem> specializations() {
        return db.run(specializationDao::findAll);
    }

    public int saveSpecialization(RefItem item) {
        return saveRef(specializationDao, item, "Название специализации", 60);
    }

    public void deleteSpecialization(int id) {
        db.run(c -> specializationDao.delete(c, id));
    }

    private int saveRef(ReferenceDao dao, RefItem item, String field, int maxLength) {
        String name = Validator.required(item.name(), field, maxLength);
        if (item.id() == null) {
            return db.run(c -> dao.insert(c, name));
        }
        db.run(c -> dao.update(c, new RefItem(item.id(), name)));
        return item.id();
    }

    // ================================================================ владельцы и питомцы

    public List<Owner> findOwners(String search) {
        return db.run(c -> ownerDao.find(c, search));
    }

    /** Добавляет (id == null) или изменяет владельца; возвращает его код. */
    public int saveOwner(Owner o) {
        Owner clean = new Owner(o.id(),
                Validator.required(o.lastName(), "Фамилия", 50),
                Validator.required(o.firstName(), "Имя", 50),
                Validator.optional(o.middleName(), "Отчество", 50),
                Validator.phone(o.phone()),
                Validator.email(o.email()),
                Validator.optional(o.address(), "Адрес", 200),
                o.registeredOn());
        if (clean.id() == null) {
            return db.run(c -> ownerDao.insert(c, clean));
        }
        db.run(c -> ownerDao.update(c, clean));
        return clean.id();
    }

    public void deleteOwner(int id) {
        db.run(c -> ownerDao.delete(c, id));
    }

    public List<Pet> petsOf(int ownerId) {
        return db.run(c -> petDao.findByOwner(c, ownerId));
    }

    public int savePet(Pet p) {
        if (p.speciesId() <= 0) {
            throw new ValidationException("Выберите вид животного.");
        }
        Pet clean = new Pet(p.id(), p.ownerId(), p.speciesId(), p.speciesName(),
                Validator.required(p.name(), "Кличка", 40),
                Validator.optional(p.breed(), "Порода", 60),
                Validator.sex(p.sex()),
                Validator.pastDate(p.birthDate(), "Дата рождения", false, today()),
                p.weightKg(),
                Validator.chip(p.chipNumber()));
        if (clean.id() == null) {
            return db.run(c -> petDao.insert(c, clean));
        }
        db.run(c -> petDao.update(c, clean));
        return clean.id();
    }

    public void deletePet(int id) {
        db.run(c -> petDao.delete(c, id));
    }

    // ================================================================ врачи и услуги

    public List<Vet> vets(boolean onlyActive) {
        return db.run(c -> vetDao.findAll(c, onlyActive));
    }

    public int saveVet(Vet v) {
        if (v.specializationId() <= 0) {
            throw new ValidationException("Выберите специализацию.");
        }
        Vet clean = new Vet(v.id(), v.specializationId(), v.specializationName(),
                Validator.required(v.lastName(), "Фамилия", 50),
                Validator.required(v.firstName(), "Имя", 50),
                Validator.optional(v.middleName(), "Отчество", 50),
                Validator.phone(v.phone()),
                Validator.pastDate(v.hireDate(), "Дата приёма на работу", true, today()),
                v.active());
        if (clean.id() == null) {
            return db.run(c -> vetDao.insert(c, clean));
        }
        db.run(c -> vetDao.update(c, clean));
        return clean.id();
    }

    public void deleteVet(int id) {
        db.run(c -> vetDao.delete(c, id));
    }

    public List<MedicalService> services(boolean onlyActive) {
        return db.run(c -> serviceDao.findAll(c, onlyActive));
    }

    public int saveService(MedicalService s) {
        MedicalService clean = new MedicalService(s.id(),
                Validator.required(s.name(), "Название услуги", 100), s.price(), s.active());
        if (clean.price() == null || clean.price().signum() < 0) {
            throw new ValidationException("Цена не может быть отрицательной.");
        }
        if (clean.id() == null) {
            return db.run(c -> serviceDao.insert(c, clean));
        }
        db.run(c -> serviceDao.update(c, clean));
        return clean.id();
    }

    public void deleteService(int id) {
        db.run(c -> serviceDao.delete(c, id));
    }

    // ================================================================ приёмы

    public List<AppointmentInfo> schedule(LocalDate day, Integer vetId) {
        return db.run(c -> appointmentDao.schedule(c, day, vetId));
    }

    public List<AppointmentInfo> history(int petId) {
        return db.run(c -> appointmentDao.history(c, petId));
    }

    public AppointmentInfo appointment(int id) {
        AppointmentInfo a = db.run(c -> appointmentDao.findInfo(c, id));
        if (a == null) {
            throw new ValidationException("Приём № " + id + " не найден.");
        }
        return a;
    }

    /** Свободные слоты врача на день с учётом текущего времени. */
    public List<LocalTime> freeSlots(int vetId, LocalDate day) {
        return SlotCalculator.freeSlots(day, db.run(c -> appointmentDao.busySlots(c, vetId, day)),
                LocalDateTime.now(clock));
    }

    /** Запись питомца на приём (BR-22: не в прошлом; остальные правила проверит СУБД). */
    public int book(int petId, int vetId, LocalDateTime start, String complaint) {
        if (start == null) {
            throw new ValidationException("Выберите дату и время приёма.");
        }
        if (!SlotCalculator.isValidSlot(start.toLocalTime())) {
            throw new ValidationException("Приём возможен с 08:00 до 19:30, в начале или в середине часа.");
        }
        if (!start.isAfter(LocalDateTime.now(clock))) {
            throw new ValidationException("Нельзя записать на прошедшее время.");
        }
        String text = Validator.optional(complaint, "Жалоба", 500);
        return db.run(c -> appointmentDao.create(c, petId, vetId, start, text));
    }

    public void cancel(int appointmentId) {
        int rows = db.run(c -> appointmentDao.cancel(c, appointmentId));
        if (rows == 0) {
            throw new ValidationException("Отменить можно только запланированный приём.");
        }
    }

    public List<ServiceLine> lines(int appointmentId) {
        return db.run(c -> appointmentDao.lines(c, appointmentId));
    }

    /**
     * Сохранение результатов приёма в одной транзакции: диагноз, услуги и (если complete)
     * перевод в статус «Проведён». Если СУБД отклонит любой шаг (например, услуга
     * снята с прайса), не сохранится ничего.
     */
    public void saveVisit(int appointmentId, String complaint, String diagnosis,
                          List<ServiceLine> lines, boolean complete) {
        AppointmentInfo a = appointment(appointmentId);
        if (a.status() != AppointmentStatus.SCHEDULED) {
            throw new ValidationException("Приём уже " + a.status().title().toLowerCase() + " — изменить его нельзя.");
        }
        String cleanComplaint = Validator.optional(complaint, "Жалоба", 500);
        String cleanDiagnosis = Validator.optional(diagnosis, "Диагноз", 500);
        if (complete && cleanDiagnosis == null) {
            throw new ValidationException("Чтобы завершить приём, укажите диагноз.");
        }
        lines.forEach(l -> Validator.quantity(l.quantity()));
        db.inTransaction(c -> {
            appointmentDao.saveVisit(c, appointmentId, cleanComplaint, cleanDiagnosis, lines, complete);
            return null;
        });
    }

    // ================================================================ отчёты

    public List<VetRevenueRow> vetRevenue(LocalDate from, LocalDate to) {
        checkPeriod(from, to);
        return db.run(c -> reportDao.vetRevenue(c, from, to));
    }

    public List<ServiceStatRow> serviceStats(LocalDate from, LocalDate to) {
        checkPeriod(from, to);
        return db.run(c -> reportDao.serviceStats(c, from, to));
    }

    private static void checkPeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ValidationException("Укажите начало и конец периода.");
        }
        if (from.isAfter(to)) {
            throw new ValidationException("Начало периода позже его конца.");
        }
    }

    /** Для сообщений об ошибках вне Swing (например, в консоли). */
    public static String messageOf(RuntimeException e) {
        return e instanceof DbException || e instanceof ValidationException ? e.getMessage() : e.toString();
    }
}
