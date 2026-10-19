package ru.vetclinic.ui;

import ru.vetclinic.model.MedicalService;
import ru.vetclinic.service.ClinicService;
import ru.vetclinic.service.Validator;

import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.math.BigDecimal;

/** Вкладка администратора «Услуги и цены». */
public class ServicesPanel extends JPanel {

    private final ClinicService service;
    private final BeanTableModel<MedicalService> model = new BeanTableModel<MedicalService>()
            .column("Код", Integer.class, MedicalService::id)
            .column("Услуга", MedicalService::name)
            .column("Цена, ₽", BigDecimal.class, MedicalService::price)
            .column("Оказывается", Boolean.class, MedicalService::active);
    private final JTable table = UiSupport.table(model);

    public ServicesPanel(ClinicService service) {
        super(new BorderLayout());
        this.service = service;
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        add(UiSupport.buttons(
                UiSupport.button("Добавить", e -> edit(null)),
                UiSupport.button("Изменить", e -> edit(UiSupport.selected(table, model))),
                UiSupport.button("Удалить", e -> delete()),
                UiSupport.button("Обновить", e -> reload())), BorderLayout.NORTH);
        add(UiSupport.scroll(table), BorderLayout.CENTER);
        reload();
    }

    public void reload() {
        UiSupport.attempt(this, () -> model.setRows(service.services(false)));
    }

    private void edit(MedicalService s) {
        JTextField name = new JTextField(s == null ? "" : s.name(), 30);
        JTextField price = new JTextField(s == null ? "" : s.price().toPlainString());
        JCheckBox active = new JCheckBox("услуга оказывается (есть в прайсе)", s == null || s.active());
        UiSupport.Form form = new UiSupport.Form().add("Название*", name).add("Цена, ₽*", price).add("", active);
        while (JOptionPane.showConfirmDialog(this, form, s == null ? "Новая услуга" : "Изменение услуги",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            boolean ok = UiSupport.attempt(this, () -> service.saveService(new MedicalService(
                    s == null ? null : s.id(), name.getText(), Validator.price(price.getText()), active.isSelected())));
            if (ok) {
                reload();
                return;
            }
        }
    }

    private void delete() {
        MedicalService s = UiSupport.selected(table, model);
        if (s != null && UiSupport.confirm(this, "Удалить услугу «" + s.name() + "»?")
                && UiSupport.attempt(this, () -> service.deleteService(s.id()))) {
            reload();
        }
    }
}
