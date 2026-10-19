package ru.vetclinic.ui;

import ru.vetclinic.model.AppointmentInfo;
import ru.vetclinic.model.MedicalService;
import ru.vetclinic.model.ServiceLine;
import ru.vetclinic.service.ClinicService;
import ru.vetclinic.service.InvoiceFormatter;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Проведение приёма врачом: жалоба, диагноз и оказанные услуги.
 * «Сохранить» — записать промежуточный результат, «Завершить приём» — то же
 * плюс перевод приёма в статус «Проведён». Всё сохраняется одной транзакцией.
 */
public class VisitDialog extends JDialog {

    private final ClinicService service;
    private final int appointmentId;
    private final JTextField complaint = new JTextField(40);
    private final JTextArea diagnosis = new JTextArea(3, 40);
    private final JComboBox<MedicalService> serviceBox = new JComboBox<>();
    private final JSpinner quantity = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
    private final JLabel totalLabel = new JLabel();
    private final List<ServiceLine> lines = new ArrayList<>();
    private final BeanTableModel<ServiceLine> model = new BeanTableModel<ServiceLine>()
            .column("Услуга", ServiceLine::serviceName)
            .column("Кол-во", Integer.class, ServiceLine::quantity)
            .column("Цена, ₽", ServiceLine::price)
            .column("Сумма, ₽", BigDecimal.class, ServiceLine::amount);
    private final JTable table = UiSupport.table(model);
    private boolean changed;

    public VisitDialog(Component parent, ClinicService service, int appointmentId) {
        super(UiSupport.windowOf(parent), "Приём № " + appointmentId, ModalityType.APPLICATION_MODAL);
        this.service = service;
        this.appointmentId = appointmentId;
        // у новых строк цены ещё нет — её возьмёт база данных из прайса
        table.getColumnModel().getColumn(2).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                setHorizontalAlignment(RIGHT);
                setText(value == null ? "по прайсу" : InvoiceFormatter.money((BigDecimal) value));
            }
        });

        UiSupport.Form header = new UiSupport.Form();
        UiSupport.attempt(parent, () -> {
            AppointmentInfo a = service.appointment(appointmentId);
            header.add("Дата и время", new JLabel(UiSupport.format(a.startTime())))
                    .add("Врач", new JLabel(a.vetName() + " (" + a.specializationName() + ")"))
                    .add("Питомец", new JLabel(a.petName() + " (" + a.speciesName() + ")"))
                    .add("Владелец", new JLabel(a.ownerName() + ", " + a.ownerPhone()));
            complaint.setText(a.complaint() == null ? "" : a.complaint());
            diagnosis.setText(a.diagnosis() == null ? "" : a.diagnosis());
            lines.addAll(service.lines(appointmentId));
            serviceBox.setModel(new DefaultComboBoxModel<>(service.services(true).toArray(new MedicalService[0])));
        });
        diagnosis.setLineWrap(true);
        diagnosis.setWrapStyleWord(true);
        header.add("Жалоба", complaint).add("Диагноз*", UiSupport.scroll(diagnosis));

        JPanel servicesPanel = new JPanel(new BorderLayout());
        servicesPanel.setBorder(BorderFactory.createTitledBorder("Оказанные услуги"));
        servicesPanel.add(UiSupport.buttons(serviceBox, new JLabel("кол-во"), quantity,
                UiSupport.button("Добавить", e -> addLine()),
                UiSupport.button("Убрать", e -> removeLine())), BorderLayout.NORTH);
        JComponentScroll scroll = new JComponentScroll(table);
        servicesPanel.add(scroll, BorderLayout.CENTER);
        servicesPanel.add(totalLabel, BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout());
        center.add(header, BorderLayout.NORTH);
        center.add(servicesPanel, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
        add(UiSupport.buttons(
                UiSupport.button("Сохранить", e -> save(false)),
                UiSupport.button("Завершить приём", e -> save(true)),
                UiSupport.button("Закрыть", e -> dispose())), BorderLayout.SOUTH);
        refreshLines();
        pack();
        setLocationRelativeTo(parent);
    }

    /** true, если что-то было сохранено (нужно обновить список приёмов). */
    public boolean changed() {
        return changed;
    }

    private void addLine() {
        MedicalService s = (MedicalService) serviceBox.getSelectedItem();
        if (s == null) {
            return;
        }
        int qty = (Integer) quantity.getValue();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).serviceId() == s.id()) {
                lines.set(i, lines.get(i).withQuantity(Math.min(100, lines.get(i).quantity() + qty)));
                refreshLines();
                return;
            }
        }
        lines.add(new ServiceLine(s.id(), s.name(), qty, null));
        refreshLines();
    }

    private void removeLine() {
        ServiceLine l = UiSupport.selected(table, model);
        if (l != null) {
            lines.removeIf(x -> x.serviceId() == l.serviceId());
            refreshLines();
        }
    }

    private void refreshLines() {
        model.setRows(lines);
        // для новых строк показываем ожидаемую сумму по текущей цене прайса
        BigDecimal total = BigDecimal.ZERO;
        for (ServiceLine l : lines) {
            BigDecimal price = l.price();
            if (price == null) {
                for (int i = 0; i < serviceBox.getItemCount(); i++) {
                    if (serviceBox.getItemAt(i).id() == l.serviceId()) {
                        price = serviceBox.getItemAt(i).price();
                    }
                }
            }
            if (price != null) {
                total = total.add(price.multiply(BigDecimal.valueOf(l.quantity())));
            }
        }
        totalLabel.setText("  Итого: " + InvoiceFormatter.money(total) + " ₽");
    }

    private void save(boolean complete) {
        if (complete && !UiSupport.confirm(this, "Завершить приём? После этого изменить его будет нельзя.")) {
            return;
        }
        boolean ok = UiSupport.attempt(this, () ->
                service.saveVisit(appointmentId, complaint.getText(), diagnosis.getText(), lines, complete));
        if (ok) {
            changed = true;
            if (complete) {
                dispose();
            } else {
                // перечитываем строки, чтобы у новых появилась зафиксированная цена
                UiSupport.attempt(this, () -> {
                    lines.clear();
                    lines.addAll(service.lines(appointmentId));
                });
                refreshLines();
                UiSupport.info(this, "Сохранено.");
            }
        }
    }

    /** Прокрутка с разумным начальным размером таблицы услуг. */
    private static final class JComponentScroll extends javax.swing.JScrollPane {
        JComponentScroll(Component view) {
            super(view);
            setPreferredSize(new Dimension(620, 180));
        }
    }
}
