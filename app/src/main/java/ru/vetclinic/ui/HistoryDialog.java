package ru.vetclinic.ui;

import ru.vetclinic.model.AppointmentInfo;
import ru.vetclinic.model.AppointmentStatus;
import ru.vetclinic.model.Pet;
import ru.vetclinic.service.ClinicService;

import javax.swing.JDialog;
import javax.swing.JTable;
import java.awt.BorderLayout;
import java.awt.Component;
import java.math.BigDecimal;

/** История болезни питомца: все его приёмы с диагнозами и суммами. */
public class HistoryDialog extends JDialog {

    public HistoryDialog(Component parent, ClinicService service, Pet pet) {
        super(UiSupport.windowOf(parent), "История болезни: " + pet, ModalityType.APPLICATION_MODAL);
        BeanTableModel<AppointmentInfo> model = new BeanTableModel<AppointmentInfo>()
                .column("Дата и время", a -> UiSupport.format(a.startTime()))
                .column("Врач", a -> a.vetName() + " (" + a.specializationName() + ")")
                .column("Статус", AppointmentInfo::status)
                .column("Жалоба", a -> a.complaint() == null ? "" : a.complaint())
                .column("Диагноз", a -> a.diagnosis() == null ? "" : a.diagnosis())
                .column("Сумма, ₽", BigDecimal.class, AppointmentInfo::total);
        JTable table = UiSupport.table(model);
        UiSupport.attempt(parent, () -> model.setRows(service.history(pet.id())));

        add(UiSupport.scroll(table), BorderLayout.CENTER);
        add(UiSupport.buttons(
                UiSupport.button("Счёт по приёму", e -> {
                    AppointmentInfo a = UiSupport.selected(table, model);
                    if (a != null && a.status() == AppointmentStatus.DONE) {
                        new InvoiceDialog(this, service, a.id()).setVisible(true);
                    } else {
                        UiSupport.info(this, "Выберите проведённый приём.");
                    }
                }),
                UiSupport.button("Закрыть", e -> dispose())), BorderLayout.SOUTH);
        setSize(900, 400);
        setLocationRelativeTo(parent);
    }
}
