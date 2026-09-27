package com.lion.datadrivenvillagers.client.editor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;

import java.util.List;

/// Vanilla's own scrolling list. Every profession field is one row: a label on the left, its widget
/// on the right. Scrolling, clipping and focus order are handed to the vanilla list, not hand-rolled.
public final class EditorRowList extends ElementListWidget<EditorRowList.Row> {

    private final int rowGutter;

    public EditorRowList(MinecraftClient client, int width, int height, int y, int itemHeight, int rowGutter) {
        super(client, width, height, y, itemHeight);
        this.rowGutter = rowGutter;
    }

    /// Leaves room on the right for the scrollbar vanilla draws for this list.
    @Override
    public int getRowWidth() {
        return getWidth() - rowGutter;
    }

    /// `addEntry` itself is protected; the screen that fills this list is not a subclass of it.
    public void addRow(Row row) {
        addEntry(row);
    }

    /// One label and its field. The list repositions both on every render call, so scroll is free.
    public static final class Row extends ElementListWidget.Entry<Row> {
        private final ClickableWidget label;
        private final ClickableWidget field;
        private final List<ClickableWidget> children;

        public Row(ClickableWidget label, ClickableWidget field) {
            this.label = label;
            this.field = field;
            this.children = List.of(label, field);
        }

        public ClickableWidget field() {
            return field;
        }

        @Override
        public List<? extends Element> children() {
            return children;
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return children;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
                            int mouseX, int mouseY, boolean hovered, float tickDelta) {
            label.setPosition(x, y);
            field.setPosition(x + label.getWidth(), y);
            label.render(context, mouseX, mouseY, tickDelta);
            field.render(context, mouseX, mouseY, tickDelta);
        }
    }
}
