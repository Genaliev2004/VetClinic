package ru.vetclinic;

import ru.vetclinic.config.AppConfig;
import ru.vetclinic.ui.MainFrame;
import ru.vetclinic.ui.RoleDialog;
import ru.vetclinic.ui.UiSupport;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;

/**
 * Точка входа приложения «ВетКлиник».
 * Запуск из корня проекта: {@code java -jar app/target/vetclinic.jar}
 * (или scripts/run.bat, scripts/run.sh).
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        AppConfig config;
        try {
            config = AppConfig.load();
        } catch (IOException e) {
            System.err.println(e.getMessage());
            JOptionPane.showMessageDialog(null, e.getMessage(), "ВетКлиник: нет файла настроек", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }
        System.out.println("ВетКлиник: ОС " + config.os() + ", файлы настроек " + config.loadedFiles());

        // масштаб интерфейса нужно задать до создания первого окна
        if (!config.uiScale().isEmpty()) {
            System.setProperty("sun.java2d.uiScale", config.uiScale());
        }
        final AppConfig cfg = config;
        SwingUtilities.invokeLater(() -> {
            UiSupport.applyLookAndFeel(cfg);
            showLogin(cfg, cfg.activeProfile());
        });
    }

    /** Показывает окно входа и, если пользователь вошёл, главное окно. */
    public static void showLogin(AppConfig config, String preferredProfile) {
        RoleDialog.Choice choice = new RoleDialog(config, preferredProfile).ask();
        if (choice == null) {
            System.exit(0);
            return;
        }
        new MainFrame(config, choice).setVisible(true);
    }
}
