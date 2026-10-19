package ru.vetclinic.ui;

import ru.vetclinic.model.RefItem;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import java.awt.BorderLayout;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Вкладка простого справочника «код — название» (виды животных, специализации).
 * Что загружать, как сохранять и удалять — передаётся снаружи функциями.
 */
public class ReferencePanel extends JPanel {

    private final String itemTitle;
    private final Supplier<List<RefItem>> loader;
    private final Function<RefItem, Integer> saver;
    private final Consumer<Integer> deleter;
    private final BeanTableModel<RefItem> model = new BeanTableModel<RefItem>()
            .column("Код", Integer.class, RefItem::id)
            .column("Название", RefItem::name);
    private final JTable table = UiSupport.table(model);

    public ReferencePanel(String itemTitle, Supplier<List<RefItem>> loader,
                          Function<RefItem, Integer> saver, Consumer<Integer> deleter) {
        super(new BorderLayout());
        this.itemTitle = itemTitle;
        this.loader = loader;
        this.saver = saver;
        this.deleter = deleter;
        table.getColumnModel().getColumn(0).setMaxWidth(80);
        add(UiSupport.buttons(
                UiSupport.button("Добавить", e -> edit(null)),
                UiSupport.button("Изменить", e -> edit(UiSupport.selected(table, model))),
                UiSupport.button("Удалить", e -> delete()),
                UiSupport.button("Обновить", e -> reload())), BorderLayout.NORTH);
        add(UiSupport.scroll(table), BorderLayout.CENTER);
        reload();
    }

    public void reload() {
        UiSupport.attempt(this, () -> model.setRows(loader.get()));
    }

    private void edit(RefItem item) {
        String initial = item == null ? "" : item.name();
        String name = (String) JOptionPane.showInputDialog(this, itemTitle + ":",
                item == null ? "Добавление" : "Изменение", JOptionPane.PLAIN_MESSAGE, null, null, initial);
        if (name == null) {
            return;   // нажата «Отмена»
        }
        if (UiSupport.attempt(this, () -> saver.apply(new RefItem(item == null ? null : item.id(), name)))) {
            reload();
        }
    }

    private void delete() {
        RefItem item = UiSupport.selected(table, model);
        if (item == null || !UiSupport.confirm(this, "Удалить «" + item.name() + "»?")) {
            return;
        }
        if (UiSupport.attempt(this, () -> deleter.accept(item.id()))) {
            reload();
        }
    }
}
