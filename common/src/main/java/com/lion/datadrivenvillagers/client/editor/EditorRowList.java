package com.lion.datadrivenvillagers.client.editor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;

import java.util.List;

/// One row per profession field. Vanilla handles scrolling, clipping and focus order.
public final class EditorRowList extends ElementListWidget<EditorRowList.Row> {

    private final int rowGutter;

    public EditorRowList(MinecraftClient client, int width, int top, int bottom, int itemHeight, int rowGutter) {
        super(client, width, client.getWindow().getScaledHeight(), top, bottom, itemHeight);
        this.rowGutter = rowGutter;
        setRenderBackground(false);
        setRenderHorizontalShadows(false);
    }

    /// Leaves room on the right for the scrollbar vanilla draws for this list.
    @Override
    public int getRowWidth() {
        return width - rowGutter;
    }

    /// Vanilla's default sits past this list's own right edge; this keeps the bar inside it.
    @Override
    protected int getScrollbarPositionX() {
        return right - 6;
    }

    /// `addEntry` itself is protected; the screen that fills this list is not a subclass of it.
    public void addRow(Row row) {
        addEntry(row);
    }

    /// `getRowTop` itself is protected; the screen checking row visibility is not a subclass of it.
    public int rowTop(int index) {
        return getRowTop(index);
    }

    /// Vanilla keeps a field focused after it scrolls out of view; this drops focus so it stops eating keys.
    @Override
    public void setScrollAmount(double amount) {
        super.setScrollAmount(amount);
        Row focused = getFocused();
        int index = focused == null ? -1 : children().indexOf(focused);
        if (index >= 0 && (getRowBottom(index) < top || getRowTop(index) > bottom)) {
            clearFieldFocus();
        }
    }

    /// Vanilla does not clear a field inside the list when focus moves to a widget outside it.
    public void clearFieldFocus() {
        Row focused = getFocused();
        if (focused != null) {
            focused.setFocused(null);
        }
        setFocused(null);
    }

    /// The list places both widgets again on every render, so scrolling needs no extra code.
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
