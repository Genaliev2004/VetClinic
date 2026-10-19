package ru.vetclinic.tools;

import ru.vetclinic.config.AppConfig;
import ru.vetclinic.db.Database;
import ru.vetclinic.model.Owner;
import ru.vetclinic.model.Pet;
import ru.vetclinic.model.Vet;
import ru.vetclinic.service.ClinicService;
import ru.vetclinic.ui.AppointmentDialog;
import ru.vetclinic.ui.HistoryDialog;
import ru.vetclinic.ui.InvoiceDialog;
import ru.vetclinic.ui.MainFrame;
import ru.vetclinic.ui.RoleDialog;
import ru.vetclinic.ui.UiSupport;
import ru.vetclinic.ui.VisitDialog;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Вспомогательная утилита (не входит в приложение): отрисовывает окна приложения
 * в PNG без показа на экране — для отчёта и для проверки интерфейса на реальной БД.
 * Запуск: java -cp app/target/vetclinic.jar;app/target/test-classes
 *         ru.vetclinic.tools.ScreenshotTool &lt;каталог&gt; &lt;postgresql|mssql&gt; [all]
 */
public final class ScreenshotTool {

    private ScreenshotTool() {
    }

    public static void main(String[] args) throws Exception {
        Path out = Paths.get(args.length > 0 ? args[0] : "screens");
        String profile = args.length > 1 ? args[1] : "postgresql";
        boolean all = args.length > 2 && "all".equals(args[2]);
        Files.createDirectories(out);
        AppConfig config = AppConfig.load();
        // «сегодня» для снимков — 21.10.2026 09:00, чтобы в расписании были записи
        Clock clock = Clock.fixed(LocalDateTime.of(2026, 10, 21, 9, 0).atZone(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        ClinicService service = new ClinicService(new Database(config.dbSettings(profile)), clock);
        String version = service.database().serverVersion();
        System.out.println("Подключено: " + version);

        SwingUtilities.invokeAndWait(() -> {
            try {
                UiSupport.applyLookAndFeel(config);
                String p = profile + "_";
                Vet doctor = service.vets(true).stream().filter(v -> v.id() == 1).findFirst().orElseThrow();   // Соколова А. В.
                shootFrame(new MainFrame(config, new RoleDialog.Choice(service, version, RoleDialog.Role.REGISTRAR, null)),
                        out, p + "registrar", all);
                if (!all) {
                    return;
                }
                shootFrame(new MainFrame(config, new RoleDialog.Choice(service, version, RoleDialog.Role.ADMIN, null)),
                        out, p + "admin", true);
                shootFrame(new MainFrame(config, new RoleDialog.Choice(service, version, RoleDialog.Role.DOCTOR, doctor)),
                        out, p + "doctor", true);
                shoot(new RoleDialog(config, profile), out.resolve(p + "login.png"));

                Owner owner = service.findOwners("Иванов").get(0);
                Pet pet = service.petsOf(owner.id()).get(0);
                shoot(new AppointmentDialog(new javax.swing.JPanel(), service, owner, pet), out.resolve(p + "dlg_appointment.png"));
                shoot(new HistoryDialog(new javax.swing.JPanel(), service, pet), out.resolve(p + "dlg_history.png"));
                shoot(new VisitDialog(new javax.swing.JPanel(), service, 17), out.resolve(p + "dlg_visit.png"));
                shoot(new InvoiceDialog(new javax.swing.JPanel(), service, 10), out.resolve(p + "dlg_invoice.png"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        System.exit(0);
    }

    private static void shootFrame(Window frame, Path out, String name, boolean allTabs) throws Exception {
        frame.addNotify();
        frame.validate();
        JTabbedPane tabs = find(frame, JTabbedPane.class);
        int count = allTabs && tabs != null ? tabs.getTabCount() : 1;
        for (int i = 0; i < count; i++) {
            if (tabs != null) {
                tabs.setSelectedIndex(i);
                // на вкладке с владельцами выбираем второго (у него два питомца)
                javax.swing.JTable first = find((Container) tabs.getSelectedComponent(), javax.swing.JTable.class);
                if (first != null && first.getRowCount() > 1 && first.getSelectedRow() < 0
                        && first.getColumnName(1).equals("ФИО")) {
                    first.setRowSelectionInterval(1, 1);
                }
            }
            shoot(frame, out.resolve(name + "_" + (i + 1) + ".png"));
        }
        frame.dispose();
    }

    private static void shoot(Window w, Path file) throws Exception {
        if (!w.isDisplayable()) {
            w.addNotify();
        }
        w.validate();
        JComponent root = ((RootPaneContainer) w).getRootPane();
        root.doLayout();
        layoutAll(root);
        // рисуем в масштабе экрана (Windows: 125–150 %), как это делает настоящее окно
        double scale = w.getGraphicsConfiguration().getDefaultTransform().getScaleX();
        BufferedImage img = new BufferedImage((int) Math.ceil(root.getWidth() * scale),
                (int) Math.ceil(root.getHeight() * scale), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.scale(scale, scale);
        // те же параметры сглаживания шрифтов, что и на экране, иначе ширина текста отличается
        Object hints = java.awt.Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
        if (hints instanceof java.util.Map<?, ?> map) {
            g.addRenderingHints(map);
        }
        root.printAll(g);
        g.dispose();
        ImageIO.write(img, "png", new File(file.toString()));
        System.out.println("  " + file.getFileName() + " " + img.getWidth() + "x" + img.getHeight());
        if (!(w instanceof MainFrame)) {
            w.dispose();
        }
    }

    private static void layoutAll(Container c) {
        c.doLayout();
        for (Component child : c.getComponents()) {
            if (child instanceof Container cc) {
                layoutAll(cc);
            }
        }
    }

    private static <T> T find(Container c, Class<T> type) {
        for (Component child : c.getComponents()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container cc) {
                T found = find(cc, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
