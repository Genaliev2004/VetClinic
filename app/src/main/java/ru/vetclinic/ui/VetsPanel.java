package ru.vetclinic.ui;

import ru.vetclinic.model.RefItem;
import ru.vetclinic.model.Vet;
import ru.vetclinic.service.ClinicService;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Вкладка администратора «Врачи». */
public class VetsPanel extends JPanel {

    private final ClinicService service;
    private final BeanTableModel<Vet> model = new BeanTableModel<Vet>()
            .column("Код", Integer.class, Vet::id)
            .column("Фамилия", Vet::lastName)
            .column("Имя", Vet::firstName)
            .column("Отчество", v -> v.middleName() == null ? "" : v.middleName())
            .column("Специализация", Vet::specializationName)
            .column("Телефон", Vet::phone)
            .column("Принят", v -> UiSupport.format(v.hireDate()))
            .column("Работает", Boolean.class, Vet::active);
    private final JTable table = UiSupport.table(model);

    public VetsPanel(ClinicService service) {
        super(new BorderLayout());
        this.service = service;
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        add(UiSupport.buttons(
                UiSupport.button("Добавить", e -> edit(null)),
                UiSupport.button("Изменить", e -> edit(UiSupport.selected(table, model))),
                UiSupport.button("Уволить / вернуть", e -> toggleActive()),
                UiSupport.button("Удалить", e -> delete()),
                UiSupport.button("Обновить", e -> reload())), BorderLayout.NORTH);
        add(UiSupport.scroll(table), BorderLayout.CENTER);
        reload();
    }

    public void reload() {
        UiSupport.attempt(this, () -> model.setRows(service.vets(false)));
    }

    private void edit(Vet vet) {
        List<RefItem> specs = new ArrayList<>();
        if (!UiSupport.attempt(this, () -> specs.addAll(service.specializations()))) {
            return;
        }
        JTextField last = new JTextField(vet == null ? "" : vet.lastName(), 25);
        JTextField first = new JTextField(vet == null ? "" : vet.firstName());
        JTextField middle = new JTextField(vet == null || vet.middleName() == null ? "" : vet.middleName());
        JTextField phone = new JTextField(vet == null ? "+7" : vet.phone());
        JComboBox<RefItem> spec = new JComboBox<>(specs.toArray(new RefItem[0]));
        if (vet != null) {
            specs.stream().filter(s -> s.id() == vet.specializationId()).findFirst().ifPresent(spec::setSelectedItem);
        }
        JSpinner hire = UiSupport.dateSpinner(vet == null ? LocalDate.now() : vet.hireDate());
        JCheckBox active = new JCheckBox("работает в клинике", vet == null || vet.active());

        UiSupport.Form form = new UiSupport.Form()
                .add("Фамилия*", last).add("Имя*", first).add("Отчество", middle)
                .add("Телефон*", phone).add("Специализация*", spec)
                .add("Дата приёма на работу*", hire).add("", active);
        while (JOptionPane.showConfirmDialog(this, form, vet == null ? "Новый врач" : "Изменение врача",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            RefItem s = (RefItem) spec.getSelectedItem();
            Vet edited = new Vet(vet == null ? null : vet.id(), s == null ? 0 : s.id(), null,
                    last.getText(), first.getText(), middle.getText(), phone.getText(),
                    UiSupport.dateOf(hire), active.isSelected());
            if (UiSupport.attempt(this, () -> service.saveVet(edited))) {
                reload();
                return;
            }
            // при ошибке форма открывается снова с уже введёнными данными
        }
    }

    private void toggleActive() {
        Vet v = UiSupport.selected(table, model);
        if (v == null) {
            return;
        }
        String q = v.active() ? "Отметить, что " + v.shortName() + " больше не работает в клинике?"
                : "Вернуть " + v.shortName() + " в список работающих врачей?";
        if (UiSupport.confirm(this, q) && UiSupport.attempt(this, () -> service.saveVet(new Vet(v.id(),
                v.specializationId(), v.specializationName(), v.lastName(), v.firstName(), v.middleName(),
                v.phone(), v.hireDate(), !v.active())))) {
            reload();
        }
    }

    private void delete() {
        Vet v = UiSupport.selected(table, model);
        if (v != null && UiSupport.confirm(this, "Удалить врача " + v.shortName() + "?")
                && UiSupport.attempt(this, () -> service.deleteVet(v.id()))) {
            reload();
        }
    }
}
