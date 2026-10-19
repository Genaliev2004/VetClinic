package ru.vetclinic.ui;

import ru.vetclinic.config.AppConfig;
import ru.vetclinic.config.DbSettings;
import ru.vetclinic.db.Database;
import ru.vetclinic.model.Vet;
import ru.vetclinic.service.ClinicService;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Стартовое окно: выбор СУБД (из профилей файла настроек), проверка подключения,
 * выбор роли пользователя и — для роли «Врач» — самого врача.
 */
public class RoleDialog extends JDialog {

    /** Роли пользователей приложения. */
    public enum Role {
        ADMIN("Администратор"), REGISTRAR("Регистратор"), DOCTOR("Врач");

        private final String title;

        Role(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    /** Результат выбора. */
    public record Choice(ClinicService service, String serverVersion, Role role, Vet doctor) {
    }

    private final AppConfig config;
    private final JComboBox<DbSettings> dbBox = new JComboBox<>();
    private final JLabel status = new JLabel("Нет подключения");
    private final JRadioButton admin = new JRadioButton(Role.ADMIN.title());
    private final JRadioButton registrar = new JRadioButton(Role.REGISTRAR.title(), true);
    private final JRadioButton doctorRole = new JRadioButton(Role.DOCTOR.title());
    private final JComboBox<Vet> vetBox = new JComboBox<>();
    private final JButton enter = new JButton("Войти");
    private ClinicService service;
    private String version;
    private Choice choice;

    public RoleDialog(AppConfig config, String preferredProfile) {
        super((java.awt.Frame) null, "ВетКлиник — вход", true);
        this.config = config;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        List<DbSettings> profiles = new ArrayList<>();
        for (String p : config.profiles()) {
            profiles.add(config.dbSettings(p));
        }
        dbBox.setModel(new DefaultComboBoxModel<>(profiles.toArray(new DbSettings[0])));
        profiles.stream().filter(s -> s.profile().equals(preferredProfile)).findFirst().ifPresent(dbBox::setSelectedItem);
        dbBox.addActionListener(e -> connect());

        JPanel db = new JPanel(new BorderLayout(6, 6));
        db.setBorder(BorderFactory.createTitledBorder("1. База данных (config/db.properties)"));
        db.add(dbBox, BorderLayout.CENTER);
        db.add(UiSupport.button("Подключиться", e -> connect()), BorderLayout.EAST);
        db.add(status, BorderLayout.SOUTH);

        JPanel roles = new JPanel(new GridLayout(0, 1, 4, 4));
        roles.setBorder(BorderFactory.createTitledBorder("2. Роль"));
        ButtonGroup g = new ButtonGroup();
        for (JRadioButton b : new JRadioButton[]{admin, registrar, doctorRole}) {
            g.add(b);
            roles.add(b);
            b.addActionListener(e -> vetBox.setEnabled(doctorRole.isSelected()));
        }
        roles.add(vetBox);
        vetBox.setEnabled(false);

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        center.add(db, BorderLayout.NORTH);
        center.add(roles, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        enter.addActionListener(e -> enter());
        enter.setEnabled(false);
        add(UiSupport.buttons(enter, UiSupport.button("Выход", e -> dispose())), BorderLayout.SOUTH);
        getRootPane().setDefaultButton(enter);

        setSize(560, 380);
        setLocationRelativeTo(null);
        connect();
    }

    /** Показывает окно и возвращает выбор пользователя или null, если окно закрыли. */
    public Choice ask() {
        setVisible(true);
        return choice;
    }

    private void connect() {
        DbSettings s = (DbSettings) dbBox.getSelectedItem();
        if (s == null) {
            return;
        }
        service = null;
        enter.setEnabled(false);
        vetBox.setModel(new DefaultComboBoxModel<>());
        status.setText("Подключение к " + s.title() + "…");
        boolean ok = UiSupport.attempt(this, () -> {
            ClinicService candidate = new ClinicService(new Database(s));
            version = candidate.database().serverVersion();
            vetBox.setModel(new DefaultComboBoxModel<>(candidate.vets(true).toArray(new Vet[0])));
            service = candidate;
        });
        status.setText(ok ? "Подключено: " + shorten(version) : "Нет подключения — проверьте настройки и сервер");
        enter.setEnabled(ok);
    }

    private static String shorten(String v) {
        return v.length() > 70 ? v.substring(0, 70) + "…" : v;
    }

    private void enter() {
        if (service == null) {
            return;
        }
        Role role = admin.isSelected() ? Role.ADMIN : registrar.isSelected() ? Role.REGISTRAR : Role.DOCTOR;
        Vet doctor = null;
        if (role == Role.DOCTOR) {
            doctor = (Vet) vetBox.getSelectedItem();
            if (doctor == null) {
                UiSupport.info(this, "Выберите врача.");
                return;
            }
        }
        choice = new Choice(service, version, role, doctor);
        dispose();
    }
}
