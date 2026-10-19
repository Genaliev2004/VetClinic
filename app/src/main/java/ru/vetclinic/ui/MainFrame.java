package ru.vetclinic.ui;

import ru.vetclinic.config.AppConfig;
import ru.vetclinic.service.ClinicService;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;

/** Главное окно: набор вкладок зависит от роли пользователя. */
public class MainFrame extends JFrame {

    private final AppConfig config;
    private final RoleDialog.Choice choice;

    public MainFrame(AppConfig config, RoleDialog.Choice choice) {
        super("ВетКлиник — " + choice.role().title()
                + (choice.doctor() != null ? " " + choice.doctor().shortName() : "")
                + " [" + choice.service().database().dialect().name() + "]");
        this.config = config;
        this.choice = choice;
        InvoiceDialog.setConfig(config);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        ClinicService service = choice.service();
        JTabbedPane tabs = new JTabbedPane();
        switch (choice.role()) {
            case ADMIN -> {
                tabs.addTab("Врачи", new VetsPanel(service));
                tabs.addTab("Услуги и цены", new ServicesPanel(service));
                tabs.addTab("Виды животных", new ReferencePanel("Название вида",
                        service::species, service::saveSpecies, service::deleteSpecies));
                tabs.addTab("Специализации", new ReferencePanel("Название специализации",
                        service::specializations, service::saveSpecialization, service::deleteSpecialization));
                tabs.addTab("Отчёты", new ReportsPanel(service));
            }
            case REGISTRAR -> {
                SchedulePanel schedule = new SchedulePanel(service, null);
                tabs.addTab("Расписание", schedule);
                tabs.addTab("Владельцы и питомцы", new OwnersPanel(service, schedule::reload));
            }
            case DOCTOR -> tabs.addTab("Мои приёмы", new SchedulePanel(service, choice.doctor()));
        }
        add(tabs, BorderLayout.CENTER);

        JLabel statusBar = new JLabel(" СУБД: " + choice.serverVersion()
                + "   |   ОС: " + System.getProperty("os.name") + " (настройки os/" + config.os().fileName() + ".properties)");
        statusBar.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
        add(statusBar, BorderLayout.SOUTH);

        setJMenuBar(menu());
        setSize(1150, 700);
        setLocationRelativeTo(null);
    }

    private JMenuBar menu() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("Файл");
        JMenuItem relogin = new JMenuItem("Сменить роль или СУБД…");
        relogin.addActionListener(e -> {
            dispose();
            ru.vetclinic.Main.showLogin(config, choice.service().database().settings().profile());
        });
        JMenuItem exit = new JMenuItem("Выход");
        exit.addActionListener(e -> System.exit(0));
        file.add(relogin);
        file.addSeparator();
        file.add(exit);

        JMenu help = new JMenu("Справка");
        JMenuItem about = new JMenuItem("О программе");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this, aboutText(), "О программе",
                JOptionPane.INFORMATION_MESSAGE));
        help.add(about);
        bar.add(file);
        bar.add(help);
        return bar;
    }

    private String aboutText() {
        return "ВетКлиник 1.0 — информационная система ветеринарной клиники\n"
                + "Java " + System.getProperty("java.version") + ", Swing + JDBC (без ORM)\n\n"
                + "Подключение: " + choice.service().database().settings().safeDescription() + "\n"
                + "Сервер: " + choice.serverVersion() + "\n"
                + "ОС: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + "\n"
                + "Файлы настроек:\n  " + String.join("\n  ", config.loadedFiles());
    }
}
