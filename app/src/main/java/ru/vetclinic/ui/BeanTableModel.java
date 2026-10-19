package ru.vetclinic.ui;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Универсальная модель таблицы для списка объектов: столбцы задаются
 * заголовком и функцией, которая достаёт значение из объекта.
 */
public class BeanTableModel<T> extends AbstractTableModel {

    /** Описание столбца. */
    public record Column<T>(String title, Class<?> type, Function<T, Object> getter) {
    }

    private final List<Column<T>> columns = new ArrayList<>();
    private List<T> rows = new ArrayList<>();

    public BeanTableModel<T> column(String title, Function<T, Object> getter) {
        return column(title, Object.class, getter);
    }

    public BeanTableModel<T> column(String title, Class<?> type, Function<T, Object> getter) {
        columns.add(new Column<>(title, type, getter));
        return this;
    }

    public void setRows(List<T> newRows) {
        rows = new ArrayList<>(newRows);
        fireTableDataChanged();
    }

    public List<T> rows() {
        return List.copyOf(rows);
    }

    public T row(int index) {
        return rows.get(index);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public String getColumnName(int column) {
        return columns.get(column).title();
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return columns.get(column).type();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return columns.get(columnIndex).getter().apply(rows.get(rowIndex));
    }
}
