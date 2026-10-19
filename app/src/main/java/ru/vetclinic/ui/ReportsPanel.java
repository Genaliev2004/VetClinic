package ru.vetclinic.ui;

import ru.vetclinic.model.ServiceStatRow;
import ru.vetclinic.model.VetRevenueRow;
import ru.vetclinic.service.ClinicService;
import ru.vetclinic.service.InvoiceFormatter;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Вкладка администратора «Отчёты»: выручка по врачам и популярные услуги за период. */
public class ReportsPanel extends JPanel {

    private final ClinicService service;
    private final JSpinner from;
    private final JSpinner to;
    private final JLabel totalLabel = new JLabel(" ");

    private final BeanTableModel<VetRevenueRow> revenueModel = new BeanTableModel<VetRevenueRow>()
            .column("Врач", VetRevenueRow::vetName)
            .column("Специализация", VetRevenueRow::specializationName)
            .column("Приёмов", Integer.class, VetRevenueRow::visits)
            .column("Выручка, ₽", BigDecimal.class, VetRevenueRow::revenue);
    private final BeanTableModel<ServiceStatRow> statsModel = new BeanTableModel<ServiceStatRow>()
            .column("Услуга", ServiceStatRow::serviceName)
            .column("Оказано раз", Integer.class, ServiceStatRow::times)
            .column("Сумма, ₽", BigDecimal.class, ServiceStatRow::revenue);

    public ReportsPanel(ClinicService service) {
        super(new BorderLayout());
        this.service = service;
        LocalDate today = service.today();
        from = UiSupport.dateSpinner(today.withDayOfYear(1));
        to = UiSupport.dateSpinner(today);

        add(UiSupport.buttons(new JLabel("Период с"), from, new JLabel("по"), to,
                UiSupport.button("Сформировать", e -> build()), totalLabel), BorderLayout.NORTH);

        JPanel left = new JPanel(new BorderLayout());
        left.setBorder(BorderFactory.createTitledBorder("Выручка по врачам (проведённые приёмы)"));
        left.add(UiSupport.scroll(UiSupport.table(revenueModel)));
        JPanel right = new JPanel(new BorderLayout());
        right.setBorder(BorderFactory.createTitledBorder("Популярные услуги"));
        JTable statsTable = UiSupport.table(statsModel);
        right.add(UiSupport.scroll(statsTable));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.5);
        add(split, BorderLayout.CENTER);
        build();
    }

    private void build() {
        LocalDate f = UiSupport.dateOf(from);
        LocalDate t = UiSupport.dateOf(to);
        UiSupport.attempt(this, () -> {
            List<VetRevenueRow> revenue = service.vetRevenue(f, t);
            revenueModel.setRows(revenue);
            statsModel.setRows(service.serviceStats(f, t));
            BigDecimal total = revenue.stream().map(VetRevenueRow::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
            int visits = revenue.stream().mapToInt(VetRevenueRow::visits).sum();
            totalLabel.setText("   Итого: " + visits + " приёмов на " + InvoiceFormatter.money(total) + " ₽");
        });
    }
}
