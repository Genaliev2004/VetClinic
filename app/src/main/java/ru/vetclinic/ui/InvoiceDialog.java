package ru.vetclinic.ui;

import ru.vetclinic.config.AppConfig;
import ru.vetclinic.model.AppointmentInfo;
import ru.vetclinic.model.ServiceLine;
import ru.vetclinic.service.ClinicService;
import ru.vetclinic.service.InvoiceFormatter;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;

/**
 * Счёт по приёму. Сохраняется в текстовый файл в каталог, кодировке и с
 * разделителем строк из настроек ОС (config/os/*.properties).
 */
public class InvoiceDialog extends JDialog {

    /** Настройки ОС; задаются один раз при запуске (см. MainFrame). */
    private static AppConfig config;

    private AppointmentInfo appointment;
    private List<ServiceLine> lines;

    static void setConfig(AppConfig cfg) {
        config = cfg;
    }

    public InvoiceDialog(Component parent, ClinicService service, int appointmentId) {
        super(UiSupport.windowOf(parent),
                "Счёт по приёму № " + appointmentId, ModalityType.APPLICATION_MODAL);
        JTextArea text = new JTextArea(22, 74);
        text.setEditable(false);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, text.getFont().getSize()));
        UiSupport.attempt(parent, () -> {
            appointment = service.appointment(appointmentId);
            lines = service.lines(appointmentId);
            text.setText(InvoiceFormatter.format(appointment, lines, "\n"));
        });
        add(UiSupport.scroll(text), BorderLayout.CENTER);
        add(UiSupport.buttons(UiSupport.button("Сохранить в файл…", e -> saveToFile()),
                UiSupport.button("Закрыть", e -> dispose())), BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(parent);
    }

    private void saveToFile() {
        if (appointment == null) {
            return;
        }
        File dir = config == null ? null : config.exportDir().toFile();
        JFileChooser chooser = new JFileChooser(dir != null && dir.isDirectory() ? dir : null);
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), InvoiceFormatter.fileName(appointment)));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        String nl = config == null ? System.lineSeparator() : config.lineSeparator();
        Charset cs = Charset.forName(config == null ? "UTF-8" : config.exportCharset());
        try {
            Files.writeString(chooser.getSelectedFile().toPath(), InvoiceFormatter.format(appointment, lines, nl), cs);
            UiSupport.info(this, "Счёт сохранён: " + chooser.getSelectedFile());
        } catch (IOException ex) {
            UiSupport.attempt(this, () -> {
                throw new IllegalStateException("Не удалось сохранить файл: " + ex.getMessage(), ex);
            });
        }
    }
}
