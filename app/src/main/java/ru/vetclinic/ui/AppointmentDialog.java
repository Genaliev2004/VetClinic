package ru.vetclinic.ui;

import ru.vetclinic.model.Owner;
import ru.vetclinic.model.Pet;
import ru.vetclinic.model.Vet;
import ru.vetclinic.service.ClinicService;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Запись питомца на приём: выбор врача, даты и свободного слота.
 * Если владелец и питомец не переданы, их можно найти прямо в окне.
 */
public class AppointmentDialog extends JDialog {

    private final ClinicService service;
    private final JComboBox<Owner> ownerBox = new JComboBox<>();
    private final JComboBox<Pet> petBox = new JComboBox<>();
    private final JComboBox<Vet> vetBox = new JComboBox<>();
    private final JSpinner date;
    private final JComboBox<LocalTime> slotBox = new JComboBox<>();
    private final JTextField complaint = new JTextField(30);
    private final JLabel slotInfo = new JLabel(" ");
    private Integer bookedId;

    public AppointmentDialog(Component parent, ClinicService service, Owner owner, Pet pet) {
        super(UiSupport.windowOf(parent), "Запись на приём", ModalityType.APPLICATION_MODAL);
        this.service = service;
        date = UiSupport.dateSpinner(service.today().plusDays(1));

        UiSupport.Form form = new UiSupport.Form();
        if (owner == null) {
            JTextField search = new JTextField(15);
            JPanel searchPanel = new JPanel(new BorderLayout(6, 0));
            searchPanel.add(search, BorderLayout.CENTER);
            searchPanel.add(UiSupport.button("Найти", e -> findOwners(search.getText())), BorderLayout.EAST);
            search.addActionListener(e -> findOwners(search.getText()));
            form.add("Поиск владельца", searchPanel);
            form.add("Владелец*", ownerBox);
            ownerBox.addActionListener(e -> loadPets());
        } else {
            ownerBox.addItem(owner);
            form.add("Владелец", new JLabel(owner.fullName() + ", " + owner.phone()));
        }
        if (pet == null) {
            form.add("Питомец*", petBox);
        } else {
            petBox.addItem(pet);
            form.add("Питомец", new JLabel(pet.toString()));
        }
        form.add("Врач*", vetBox).add("Дата*", date).add("Время*", slotBox).add("", slotInfo).add("Жалоба", complaint);

        add(form, BorderLayout.CENTER);
        add(UiSupport.buttons(UiSupport.button("Записать", e -> save()),
                UiSupport.button("Отмена", e -> dispose())), BorderLayout.SOUTH);

        List<Vet> vets = new ArrayList<>();
        UiSupport.attempt(parent, () -> vets.addAll(service.vets(true)));
        vetBox.setModel(new DefaultComboBoxModel<>(vets.toArray(new Vet[0])));
        vetBox.addActionListener(e -> loadSlots());
        date.addChangeListener(e -> loadSlots());
        if (owner == null) {
            findOwners("");
        }
        loadSlots();

        pack();
        setLocationRelativeTo(parent);
    }

    /** Код созданного приёма или null, если запись не состоялась. */
    public Integer bookedId() {
        return bookedId;
    }

    private void findOwners(String text) {
        UiSupport.attempt(this, () -> ownerBox.setModel(
                new DefaultComboBoxModel<>(service.findOwners(text).toArray(new Owner[0]))));
        loadPets();
    }

    private void loadPets() {
        Owner o = (Owner) ownerBox.getSelectedItem();
        if (o == null) {
            petBox.setModel(new DefaultComboBoxModel<>());
            return;
        }
        UiSupport.attempt(this, () -> petBox.setModel(
                new DefaultComboBoxModel<>(service.petsOf(o.id()).toArray(new Pet[0]))));
    }

    private void loadSlots() {
        Vet v = (Vet) vetBox.getSelectedItem();
        LocalDate day = UiSupport.dateOf(date);
        if (v == null) {
            slotBox.setModel(new DefaultComboBoxModel<>());
            return;
        }
        UiSupport.attempt(this, () -> {
            List<LocalTime> free = service.freeSlots(v.id(), day);
            slotBox.setModel(new DefaultComboBoxModel<>(free.toArray(new LocalTime[0])));
            slotInfo.setText(free.isEmpty() ? "Свободных слотов нет — выберите другой день или врача"
                    : "Свободно слотов: " + free.size());
        });
    }

    private void save() {
        Pet p = (Pet) petBox.getSelectedItem();
        Vet v = (Vet) vetBox.getSelectedItem();
        LocalTime t = (LocalTime) slotBox.getSelectedItem();
        if (p == null || v == null || t == null) {
            UiSupport.info(this, "Выберите питомца, врача и время.");
            return;
        }
        LocalDate day = UiSupport.dateOf(date);
        boolean ok = UiSupport.attempt(this, () -> bookedId = service.book(p.id(), v.id(), day.atTime(t), complaint.getText()));
        if (ok) {
            UiSupport.info(this, "Питомец " + p.name() + " записан к врачу " + v.shortName() + " на "
                    + UiSupport.format(day) + " " + t + ".");
            dispose();
        } else {
            loadSlots();   // слот мог занять кто-то другой — обновляем список
        }
    }
}
