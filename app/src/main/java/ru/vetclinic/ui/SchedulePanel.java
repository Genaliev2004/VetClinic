package ru.vetclinic.ui;

import ru.vetclinic.model.AppointmentInfo;
import ru.vetclinic.model.AppointmentStatus;
import ru.vetclinic.model.Vet;
import ru.vetclinic.service.ClinicService;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Расписание приёмов на день. Используется регистратором (все врачи, запись
 * и отмена) и врачом (только свои приёмы, проведение приёма).
 */
public class SchedulePanel extends JPanel {

    /** Пункт «Все врачи» в фильтре. */
    private static final String ALL_VETS = "Все врачи";

    private final ClinicService service;
    private final Vet doctor;
    private final JSpinner date;
    private final JComboBox<Object> vetFilter = new JComboBox<>();
    private final JLabel summary = new JLabel(" ");
    private final BeanTableModel<AppointmentInfo> model = new BeanTableModel<AppointmentInfo>()
            .column("Время", a -> a.startTime().toLocalTime().toString())
            .column("Врач", AppointmentInfo::vetName)
            .column("Питомец", a -> a.petName() + " (" + a.speciesName() + ")")
            .column("Владелец", AppointmentInfo::ownerName)
            .column("Телефон", AppointmentInfo::ownerPhone)
            .column("Жалоба", a -> a.complaint() == null ? "" : a.complaint())
            .column("Статус", AppointmentInfo::status)
            .column("Сумма, ₽", BigDecimal.class, AppointmentInfo::total);
    private final JTable table = UiSupport.table(model);

    /**
     * @param doctor врач, если панель открыта в роли «Врач» (показываются только его приёмы);
     *               null — режим регистратора
     */
    public SchedulePanel(ClinicService service, Vet doctor) {
        super(new BorderLayout());
        this.service = service;
        this.doctor = doctor;
        date = UiSupport.dateSpinner(service.today());
        date.addChangeListener(e -> reload());

        JPanel top = UiSupport.buttons(
                UiSupport.button("<", e -> shiftDay(-1)), date, UiSupport.button(">", e -> shiftDay(1)),
                UiSupport.button("Сегодня", e -> UiSupport.setDate(date, service.today())));
        if (doctor == null) {
            top.add(new JLabel("  Врач:"));
            top.add(vetFilter);
            loadVetFilter();
            vetFilter.addActionListener(e -> reload());
            top.add(UiSupport.button("Новая запись", e -> newAppointment()));
            top.add(UiSupport.button("Отменить запись", e -> cancel()));
        } else {
            top.add(new JLabel("  Врач: " + doctor));
            top.add(UiSupport.button("Провести приём", e -> conduct()));
            top.add(UiSupport.button("История питомца", e -> history()));
        }
        top.add(UiSupport.button("Счёт", e -> invoice()));
        top.add(UiSupport.button("Обновить", e -> reload()));

        add(top, BorderLayout.NORTH);
        add(UiSupport.scroll(table), BorderLayout.CENTER);
        add(summary, BorderLayout.SOUTH);
        if (doctor != null) {
            table.getColumnModel().removeColumn(table.getColumnModel().getColumn(1));   // столбец «Врач» не нужен
        }
        reload();
    }

    private void loadVetFilter() {
        List<Object> items = new ArrayList<>();
        items.add(ALL_VETS);
        UiSupport.attempt(this, () -> items.addAll(service.vets(false)));
        vetFilter.setModel(new DefaultComboBoxModel<>(items.toArray()));
    }

    private void shiftDay(int days) {
        UiSupport.setDate(date, UiSupport.dateOf(date).plusDays(days));
    }

    public void reload() {
        Integer vetId = doctor != null ? doctor.id()
                : vetFilter.getSelectedItem() instanceof Vet v ? v.id() : null;
        UiSupport.attempt(this, () -> {
            List<AppointmentInfo> rows = service.schedule(UiSupport.dateOf(date), vetId);
            model.setRows(rows);
            long planned = rows.stream().filter(a -> a.status() == AppointmentStatus.SCHEDULED).count();
            long done = rows.stream().filter(a -> a.status() == AppointmentStatus.DONE).count();
            summary.setText("  Приёмов: " + rows.size() + " (запланировано " + planned + ", проведено " + done + ")");
        });
    }

    private AppointmentInfo selected() {
        AppointmentInfo a = UiSupport.selected(table, model);
        if (a == null) {
            UiSupport.info(this, "Выберите приём в таблице.");
        }
        return a;
    }

    private void newAppointment() {
        AppointmentDialog d = new AppointmentDialog(this, service, null, null);
        d.setVisible(true);
        if (d.bookedId() != null) {
            reload();
        }
    }

    private void cancel() {
        AppointmentInfo a = selected();
        if (a != null && UiSupport.confirm(this, "Отменить запись " + a.petName() + " на "
                + UiSupport.format(a.startTime()) + "?")
                && UiSupport.attempt(this, () -> service.cancel(a.id()))) {
            reload();
        }
    }

    private void conduct() {
        AppointmentInfo a = selected();
        if (a == null) {
            return;
        }
        if (a.status() != AppointmentStatus.SCHEDULED) {
            UiSupport.info(this, "Приём уже " + a.status().title().toLowerCase() + ". Счёт можно посмотреть кнопкой «Счёт».");
            return;
        }
        VisitDialog d = new VisitDialog(this, service, a.id());
        d.setVisible(true);
        if (d.changed()) {
            reload();
        }
    }

    private void history() {
        AppointmentInfo a = selected();
        if (a != null) {
            UiSupport.attempt(this, () -> {
                var pet = service.petsOf(a.ownerId()).stream().filter(p -> p.id() == a.petId()).findFirst().orElseThrow();
                new HistoryDialog(this, service, pet).setVisible(true);
            });
        }
    }

    private void invoice() {
        AppointmentInfo a = selected();
        if (a == null) {
            return;
        }
        if (a.status() != AppointmentStatus.DONE) {
            UiSupport.info(this, "Счёт формируется по проведённому приёму.");
            return;
        }
        new InvoiceDialog(this, service, a.id()).setVisible(true);
    }
}
