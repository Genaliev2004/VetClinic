package ru.vetclinic.ui;

import ru.vetclinic.config.AppConfig;
import ru.vetclinic.db.DbException;
import ru.vetclinic.service.InvoiceFormatter;
import ru.vetclinic.service.ValidationException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;

/** Общие приёмы работы со Swing: внешний вид, ошибки, даты, таблицы, формы. */
public final class UiSupport {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private UiSupport() {
    }

    // ------------------------------------------------------------ внешний вид (из настроек ОС)

    /** Устанавливает Look&Feel и шрифт по настройкам из config/os/*.properties. */
    public static void applyLookAndFeel(AppConfig config) {
        String laf = config.lookAndFeel();
        try {
            if ("system".equalsIgnoreCase(laf)) {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } else {
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if (info.getName().equalsIgnoreCase(laf)) {
                        UIManager.setLookAndFeel(info.getClassName());
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Не удалось установить внешний вид " + laf + ": " + e.getMessage());
        }

        String family = config.fontName();
        boolean installed = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames()).contains(family);
        if (!installed) {
            System.err.println("Шрифт «" + family + "» не найден, используется Dialog");
            family = Font.DIALOG;
        }
        FontUIResource font = new FontUIResource(family, Font.PLAIN, config.fontSize());
        UIManager.getLookAndFeelDefaults().put("defaultFont", font);   // для Nimbus
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            if (UIManager.get(key) instanceof FontUIResource) {
                UIManager.put(key, font);
            }
        }
    }

    /** Окно, в котором находится компонент (или сам компонент, если это окно) — владелец для диалогов. */
    public static Window windowOf(Component c) {
        return c instanceof Window w ? w : SwingUtilities.getWindowAncestor(c);
    }

    // ------------------------------------------------------------ ошибки

    /**
     * Выполняет действие и показывает ошибку в диалоге, если она возникла.
     * Возвращает true, если действие прошло успешно.
     */
    public static boolean attempt(Component parent, Runnable action) {
        Window w = parent == null ? null : windowOf(parent);
        Cursor old = w == null ? null : w.getCursor();
        try {
            if (w != null) {
                w.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            }
            action.run();
            return true;
        } catch (ValidationException e) {
            JOptionPane.showMessageDialog(parent, e.getMessage(), "Проверьте данные", JOptionPane.WARNING_MESSAGE);
        } catch (DbException e) {
            if (e.getCause() != null) {
                System.err.println("Ошибка СУБД: " + e.getCause());
            }
            JOptionPane.showMessageDialog(parent, wrap(e.getMessage()), "Ошибка базы данных", JOptionPane.ERROR_MESSAGE);
        } catch (RuntimeException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(parent, wrap(e.toString()), "Непредвиденная ошибка", JOptionPane.ERROR_MESSAGE);
        } finally {
            if (w != null) {
                w.setCursor(old);
            }
        }
        return false;
    }

    /** Разбивает длинное сообщение на строки, чтобы диалог не растягивался на весь экран. */
    static String wrap(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int line = 0;
        for (String word : text.split(" ")) {
            if (line + word.length() > 90) {
                sb.append('\n');
                line = 0;
            }
            sb.append(word).append(' ');
            line += word.length() + 1;
        }
        return sb.toString().trim();
    }

    public static boolean confirm(Component parent, String question) {
        return JOptionPane.showConfirmDialog(parent, question, "Подтверждение",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    public static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "ВетКлиник", JOptionPane.INFORMATION_MESSAGE);
    }

    // ------------------------------------------------------------ даты

    /** Поле ввода даты (JSpinner с форматом дд.мм.гггг). */
    public static JSpinner dateSpinner(LocalDate initial) {
        SpinnerDateModel model = new SpinnerDateModel(toDate(initial == null ? LocalDate.now() : initial),
                null, null, Calendar.DAY_OF_MONTH);
        JSpinner spinner = new JSpinner(model);
        spinner.setEditor(new JSpinner.DateEditor(spinner, "dd.MM.yyyy"));
        return spinner;
    }

    public static LocalDate dateOf(JSpinner spinner) {
        Date d = (Date) spinner.getValue();
        return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public static void setDate(JSpinner spinner, LocalDate date) {
        spinner.setValue(toDate(date));
    }

    private static Date toDate(LocalDate d) {
        return Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    public static String format(LocalDate d) {
        return d == null ? "" : d.format(DATE);
    }

    public static String format(LocalDateTime d) {
        return d == null ? "" : d.format(DATE_TIME);
    }

    public static String money(BigDecimal v) {
        return InvoiceFormatter.money(v);
    }

    // ------------------------------------------------------------ таблицы и формы

    /** Таблица с выбором одной строки, сортировкой по столбцам и выравниванием чисел вправо. */
    public static JTable table(BeanTableModel<?> model) {
        JTable t = new JTable(model);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setAutoCreateRowSorter(true);
        t.setFillsViewportHeight(true);
        t.setRowHeight(Math.max(t.getRowHeight(), t.getFontMetrics(t.getFont()).getHeight() + 6));
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(JLabel.RIGHT);
        t.setDefaultRenderer(BigDecimal.class, new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                setHorizontalAlignment(JLabel.RIGHT);
                setText(value == null ? "" : money((BigDecimal) value));
            }
        });
        t.setDefaultRenderer(Integer.class, right);
        return t;
    }

    /** Выбранный объект таблицы (с учётом сортировки) или null. */
    public static <T> T selected(JTable table, BeanTableModel<T> model) {
        int viewRow = table.getSelectedRow();
        return viewRow < 0 ? null : model.row(table.convertRowIndexToModel(viewRow));
    }

    public static JScrollPane scroll(JComponent c) {
        return new JScrollPane(c);
    }

    public static JButton button(String text, ActionListener action) {
        JButton b = new JButton(text);
        b.addActionListener(action);
        return b;
    }

    /** Панель с кнопками в ряд. */
    public static JPanel buttons(JComponent... components) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        for (JComponent c : components) {
            p.add(c);
        }
        return p;
    }

    /** Простая форма «подпись — поле» на GridBagLayout. */
    public static final class Form extends JPanel {
        private int row;

        public Form() {
            super(new GridBagLayout());
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        }

        public Form add(String label, JComponent field) {
            GridBagConstraints l = new GridBagConstraints();
            l.gridx = 0;
            l.gridy = row;
            l.anchor = GridBagConstraints.NORTHWEST;
            l.insets = new Insets(4, 0, 4, 10);
            super.add(new JLabel(label), l);
            GridBagConstraints f = new GridBagConstraints();
            f.gridx = 1;
            f.gridy = row++;
            f.weightx = 1;
            f.fill = GridBagConstraints.HORIZONTAL;
            f.insets = new Insets(4, 0, 4, 0);
            super.add(field, f);
            return this;
        }
    }
}
