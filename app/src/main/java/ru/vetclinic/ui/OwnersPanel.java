package ru.vetclinic.ui;

import ru.vetclinic.model.Owner;
import ru.vetclinic.model.Pet;
import ru.vetclinic.model.RefItem;
import ru.vetclinic.service.ClinicService;
import ru.vetclinic.service.Validator;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Вкладка регистратора «Владельцы и питомцы»: поиск владельца, его питомцы,
 * история болезни и запись питомца на приём.
 */
public class OwnersPanel extends JPanel {

    private final ClinicService service;
    private final Runnable onBooked;
    private final JTextField search = new JTextField(20);

    private final BeanTableModel<Owner> ownerModel = new BeanTableModel<Owner>()
            .column("Код", Integer.class, Owner::id)
            .column("ФИО", Owner::fullName)
            .column("Телефон", Owner::phone)
            .column("E-mail", o -> o.email() == null ? "" : o.email())
            .column("Адрес", o -> o.address() == null ? "" : o.address())
            .column("Зарегистрирован", o -> UiSupport.format(o.registeredOn()));
    private final JTable ownerTable = UiSupport.table(ownerModel);

    private final BeanTableModel<Pet> petModel;
    private final JTable petTable;
    private final JLabel petsTitle = new JLabel("Питомцы");

    /** @param onBooked вызывается после записи на приём (чтобы обновить расписание) */
    public OwnersPanel(ClinicService service, Runnable onBooked) {
        super(new BorderLayout());
        this.service = service;
        this.onBooked = onBooked;
        LocalDate today = service.today();
        petModel = new BeanTableModel<Pet>()
                .column("Кличка", Pet::name)
                .column("Вид", Pet::speciesName)
                .column("Порода", p -> p.breed() == null ? "" : p.breed())
                .column("Пол", Pet::sexTitle)
                .column("Дата рождения", p -> UiSupport.format(p.birthDate()))
                .column("Возраст", p -> p.ageText(today))
                .column("Вес, кг", p -> p.weightKg() == null ? "" : p.weightKg().toPlainString())
                .column("Чип", p -> p.chipNumber() == null ? "" : p.chipNumber());
        petTable = UiSupport.table(petModel);
        ownerTable.getColumnModel().getColumn(0).setMaxWidth(60);

        search.addActionListener(e -> reload());
        JPanel top = new JPanel(new BorderLayout());
        top.add(UiSupport.buttons(new JLabel("Фамилия или телефон:"), search,
                UiSupport.button("Найти", e -> reload()),
                UiSupport.button("Новый владелец", e -> editOwner(null)),
                UiSupport.button("Изменить", e -> editOwner(UiSupport.selected(ownerTable, ownerModel))),
                UiSupport.button("Удалить", e -> deleteOwner())), BorderLayout.NORTH);
        top.add(UiSupport.scroll(ownerTable), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        JPanel petButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        petButtons.add(petsTitle);
        petButtons.add(UiSupport.button("Добавить питомца", e -> editPet(null)));
        petButtons.add(UiSupport.button("Изменить", e -> editPet(UiSupport.selected(petTable, petModel))));
        petButtons.add(UiSupport.button("Удалить", e -> deletePet()));
        petButtons.add(UiSupport.button("История болезни", e -> showHistory()));
        petButtons.add(UiSupport.button("Записать на приём", e -> book()));
        bottom.add(petButtons, BorderLayout.NORTH);
        bottom.add(UiSupport.scroll(petTable), BorderLayout.CENTER);
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        ownerTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                reloadPets();
            }
        });

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        split.setResizeWeight(0.55);
        add(split, BorderLayout.CENTER);
        reload();
    }

    public void reload() {
        UiSupport.attempt(this, () -> ownerModel.setRows(service.findOwners(search.getText())));
        reloadPets();
    }

    private Owner selectedOwner() {
        return UiSupport.selected(ownerTable, ownerModel);
    }

    private void reloadPets() {
        Owner o = selectedOwner();
        if (o == null) {
            petModel.setRows(List.of());
            petsTitle.setText("Питомцы (выберите владельца)");
            return;
        }
        petsTitle.setText("Питомцы: " + o.fullName());
        UiSupport.attempt(this, () -> petModel.setRows(service.petsOf(o.id())));
    }

    // ------------------------------------------------------------ владельцы

    private void editOwner(Owner o) {
        JTextField last = new JTextField(o == null ? "" : o.lastName(), 25);
        JTextField first = new JTextField(o == null ? "" : o.firstName());
        JTextField middle = new JTextField(o == null || o.middleName() == null ? "" : o.middleName());
        JTextField phone = new JTextField(o == null ? "+7" : o.phone());
        JTextField email = new JTextField(o == null || o.email() == null ? "" : o.email());
        JTextField address = new JTextField(o == null || o.address() == null ? "" : o.address());
        UiSupport.Form form = new UiSupport.Form()
                .add("Фамилия*", last).add("Имя*", first).add("Отчество", middle)
                .add("Телефон*", phone).add("E-mail", email).add("Адрес", address);
        while (JOptionPane.showConfirmDialog(this, form, o == null ? "Новый владелец" : "Изменение владельца",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            Owner edited = new Owner(o == null ? null : o.id(), last.getText(), first.getText(), middle.getText(),
                    phone.getText(), email.getText(), address.getText(), o == null ? null : o.registeredOn());
            if (UiSupport.attempt(this, () -> service.saveOwner(edited))) {
                if (o == null) {
                    search.setText(edited.lastName().trim());   // сразу показываем нового владельца
                }
                reload();
                return;
            }
        }
    }

    private void deleteOwner() {
        Owner o = selectedOwner();
        if (o != null && UiSupport.confirm(this, "Удалить владельца " + o.fullName() + "?")
                && UiSupport.attempt(this, () -> service.deleteOwner(o.id()))) {
            reload();
        }
    }

    // ------------------------------------------------------------ питомцы

    private void editPet(Pet p) {
        Owner owner = selectedOwner();
        if (owner == null) {
            UiSupport.info(this, "Сначала выберите владельца.");
            return;
        }
        List<RefItem> species = new ArrayList<>();
        if (!UiSupport.attempt(this, () -> species.addAll(service.species()))) {
            return;
        }
        JTextField name = new JTextField(p == null ? "" : p.name(), 20);
        JComboBox<RefItem> kind = new JComboBox<>(species.toArray(new RefItem[0]));
        if (p != null) {
            species.stream().filter(s -> s.id() == p.speciesId()).findFirst().ifPresent(kind::setSelectedItem);
        }
        JTextField breed = new JTextField(p == null || p.breed() == null ? "" : p.breed());
        JRadioButton male = new JRadioButton("самец", p == null || Pet.MALE.equals(p.sex()));
        JRadioButton female = new JRadioButton("самка", p != null && Pet.FEMALE.equals(p.sex()));
        ButtonGroup sexGroup = new ButtonGroup();
        sexGroup.add(male);
        sexGroup.add(female);
        JPanel sex = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        sex.add(male);
        sex.add(female);
        javax.swing.JCheckBox birthKnown = new javax.swing.JCheckBox("известна", p == null || p.birthDate() != null);
        JSpinner birth = UiSupport.dateSpinner(p == null || p.birthDate() == null ? service.today() : p.birthDate());
        JPanel birthPanel = new JPanel(new BorderLayout(6, 0));
        birthPanel.add(birth, BorderLayout.CENTER);
        birthPanel.add(birthKnown, BorderLayout.EAST);
        birthKnown.addActionListener(e -> birth.setEnabled(birthKnown.isSelected()));
        birth.setEnabled(birthKnown.isSelected());
        JTextField weight = new JTextField(p == null || p.weightKg() == null ? "" : p.weightKg().toPlainString());
        JTextField chip = new JTextField(p == null || p.chipNumber() == null ? "" : p.chipNumber());

        UiSupport.Form form = new UiSupport.Form()
                .add("Владелец", new JLabel(owner.fullName()))
                .add("Кличка*", name).add("Вид*", kind).add("Порода", breed).add("Пол*", sex)
                .add("Дата рождения", birthPanel).add("Вес, кг", weight).add("Номер чипа (15 цифр)", chip);
        while (JOptionPane.showConfirmDialog(this, form, p == null ? "Новый питомец" : "Изменение питомца",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            RefItem k = (RefItem) kind.getSelectedItem();
            boolean ok = UiSupport.attempt(this, () -> service.savePet(new Pet(p == null ? null : p.id(), owner.id(),
                    k == null ? 0 : k.id(), null, name.getText(), breed.getText(), male.isSelected() ? Pet.MALE : Pet.FEMALE,
                    birthKnown.isSelected() ? UiSupport.dateOf(birth) : null,
                    Validator.weight(weight.getText()), chip.getText())));
            if (ok) {
                reloadPets();
                return;
            }
        }
    }

    private void deletePet() {
        Pet p = UiSupport.selected(petTable, petModel);
        if (p != null && UiSupport.confirm(this, "Удалить питомца «" + p.name() + "»?")
                && UiSupport.attempt(this, () -> service.deletePet(p.id()))) {
            reloadPets();
        }
    }

    private void showHistory() {
        Pet p = UiSupport.selected(petTable, petModel);
        if (p == null) {
            UiSupport.info(this, "Выберите питомца.");
            return;
        }
        new HistoryDialog(this, service, p).setVisible(true);
    }

    private void book() {
        Owner o = selectedOwner();
        Pet p = UiSupport.selected(petTable, petModel);
        if (o == null || p == null) {
            UiSupport.info(this, "Выберите владельца и питомца.");
            return;
        }
        AppointmentDialog d = new AppointmentDialog(this, service, o, p);
        d.setVisible(true);
        if (d.bookedId() != null) {
            onBooked.run();
        }
    }
}
