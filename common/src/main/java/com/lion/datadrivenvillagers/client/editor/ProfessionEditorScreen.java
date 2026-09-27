package com.lion.datadrivenvillagers.client.editor;

import com.lion.datadrivenvillagers.client.editor.EditorFields.Source;
import com.lion.datadrivenvillagers.client.editor.EditorFields.Spec;
import com.lion.datadrivenvillagers.network.EditorOpenPayload;
import com.lion.datadrivenvillagers.network.EditorResultPayload;
import com.lion.datadrivenvillagers.network.EditorResultPayload.Note;
import com.lion.datadrivenvillagers.network.EditorSavePayload;
import com.lion.datadrivenvillagers.network.EditorTradesPayload;
import com.lion.datadrivenvillagers.platform.ClientNetwork;
import com.lion.datadrivenvillagers.profession.HatKind;
import com.lion.datadrivenvillagers.profession.WorkBehaviour;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/// Builds a profession without leaving the game, and explains every field while it does.
///
/// A save runs the same reload `/ddv edit` runs from the command line, and puts the same
/// answer at the bottom of this screen.
public final class ProfessionEditorScreen extends Screen {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final int MAX_CONTENT = 400;
    private static final int LABEL_WIDTH = 148;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_STEP = ROW_HEIGHT + 4;
    private static final int ROWS_TOP = 70;
    /// Room the row list leaves on its right edge for the scrollbar it draws itself.
    private static final int ROW_GUTTER = 20;
    private static final int SUGGESTION_HEIGHT = 12;
    /// How many wrapped report lines get room; overflow is cut by the scissor in render, never painted over rows.
    private static final int STATUS_LINES = 5;

    private static final int TITLE = 0xFFFFFFFF;
    private static final int LABEL = 0xFFA0A0A0;
    private static final int HINT = 0xFF707070;
    private static final int GOOD = 0xFF7FCF5F;
    private static final int WARN = 0xFFFFD24A;
    private static final int BAD = 0xFFFF6B6B;
    private static final int FIELD_OK = 0xFFE0E0E0;
    private static final int SUGGESTION_BACKGROUND = 0xF0100010;

    private enum Tab {
        BASICS("Basics"), LOOK("Look"), WORK("Work"), COMBAT("Combat"), PLAN("Day plan");

        private final String title;

        Tab(String title) {
            this.title = title;
        }
    }

    /// What the file says about the eleven dangers every villager already runs from.
    private enum FearMode {
        VANILLA("vanilla list"), ADD("vanilla plus these"), REPLACE("only these"), NOTHING("fears nothing");

        private final String title;

        FearMode(String title) {
            this.title = title;
        }
    }

    private enum PlanMode {
        VANILLA("vanilla plan"), DEFAULT("default"), NIGHT("night"), CUSTOM("written out");

        private final String title;

        PlanMode(String title) {
            this.title = title;
        }
    }

    private final JsonObject root;
    private final boolean isNew;
    private String fileName;

    private Tab tab = Tab.BASICS;
    private EditorRowList rowList;
    private int left;
    private int content;
    private final Map<TextFieldWidget, Source> sources = new HashMap<>();

    private FearMode fearMode;
    private PlanMode planMode;
    private TextFieldWidget fearList;
    private TextFieldWidget planEntries;

    private List<String> suggestions = List.of();
    private boolean suggestionsClosed;
    private Element lastFocused;
    private int suggestionX;
    private int suggestionY;
    private int suggestionWidth;

    private List<Note> status = List.of();

    private ProfessionEditorScreen(EditorOpenPayload payload) {
        super(Text.literal("Villager Editor"));
        this.fileName = payload.fileName();
        this.isNew = payload.fileName().isEmpty();

        JsonObject parsed;
        try {
            parsed = JsonParser.parseString(payload.json()).getAsJsonObject();
        } catch (RuntimeException e) {
            // Must stay openable on invalid json too: fixing a hand-broken file is the editor's most useful case.
            parsed = new JsonObject();
            this.status = List.of(
                    EditorResultPayload.bad("That file is not readable as json: " + e.getMessage()),
                    EditorResultPayload.bad("The editor starts empty. Saving replaces the file."));
        }
        if (this.status.isEmpty()) {
            // What the server currently holds for this file, shown before the first keystroke.
            this.status = List.of(payload.state());
        }
        // The attack boxes can only show an object; any other value would survive a save unseen.
        if (parsed.has("attack") && !parsed.get("attack").isJsonObject()) {
            parsed.remove("attack");
        }
        this.root = parsed;
        this.fearMode = readFearMode();
        this.planMode = readPlanMode();
    }

    /// Static so the packet handler need not name this screen type, which a dedicated server must never load.
    public static void open(EditorOpenPayload payload) {
        MinecraftClient.getInstance().setScreen(new ProfessionEditorScreen(payload));
    }

    /// Ignored if the screen is already closed; a rejection also reaches the chat, so nothing is lost.
    public static void showResult(EditorResultPayload payload) {
        if (MinecraftClient.getInstance().currentScreen instanceof ProfessionEditorScreen screen) {
            screen.status = payload.notes();
        }
    }

    @Override
    protected void init() {
        sources.clear();
        suggestions = List.of();
        content = Math.min(MAX_CONTENT, width - 40);
        left = (width - content) / 2;

        int tabWidth = content / Tab.values().length;
        for (Tab value : Tab.values()) {
            ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.literal(value.title),
                            press -> switchTo(value))
                    .dimensions(left + value.ordinal() * tabWidth, 40, tabWidth - 2, 20).build());
            // Vanilla marks the page you are on by taking the button away, and so does this.
            button.active = value != tab;
        }

        // Kept across a re-init that leaves the page unchanged; switchTo clears it to jump to the new page's top.
        double scroll = rowList == null ? 0 : rowList.getScrollAmount();

        rowList = new EditorRowList(MinecraftClient.getInstance(), content, rowsBottom() - ROWS_TOP, ROWS_TOP,
                ROW_STEP, ROW_GUTTER);
        rowList.setX(left);

        switch (tab) {
            case BASICS -> {
                // Lower-cased live, not only on save, so the author sees the real filename right away.
                TextFieldWidget name = box(EditorFields.BASICS.get(0), fileName,
                        value -> fileName = value.trim().toLowerCase(Locale.ROOT));
                name.setTextPredicate(typed -> typed.equals(typed.toLowerCase(Locale.ROOT)));
                name.setEditable(isNew);
                for (int i = 1; i < EditorFields.BASICS.size(); i++) {
                    field(EditorFields.BASICS.get(i));
                }
            }
            case LOOK -> {
                field(EditorFields.LOOK.get(0));
                field(EditorFields.LOOK.get(1));
                cycle("Hat", HatKind.values(),
                        pick(HatKind.class, JsonEdit.text(root, "hat"), HatKind.NONE),
                        kind -> Text.literal(kind.name().toLowerCase(Locale.ROOT)),
                        kind -> JsonEdit.setText(root, "hat", kind.name().toLowerCase(Locale.ROOT)),
                        """
                        Decides how much of the villager head your image covers.
                          none     your image is drawn over the head as it is
                          partial  the type's hat stays visible around it
                          full     the type's whole head layer is switched off
                        Only desert and snow villagers have a hat of their own, so on every
                        other type the three look the same.""");
                field(EditorFields.LOOK.get(2));
                field(EditorFields.LOOK.get(3));
            }
            case WORK -> {
                cycle("Work behaviour", WorkBehaviour.values(),
                        pick(WorkBehaviour.class, JsonEdit.text(root, "work_behaviour"), WorkBehaviour.STATION),
                        behaviour -> Text.literal(behaviour.lower()),
                        behaviour -> JsonEdit.setText(root, "work_behaviour", behaviour.lower()),
                        """
                        Decides what the villager does once it has reached its block.
                          station  the vanilla routine: stand there, look busy, restock
                          farm     the farmer's routine on top - harvests and replants
                        farm needs farmland within reach and its seeds under "Picks up items";
                        the parser fills both in when they are left empty.""");
                for (Spec spec : EditorFields.WORK) {
                    field(spec);
                }
                tradesButton();
            }
            case COMBAT -> {
                field(EditorFields.COMBAT.get(0));
                nested(EditorFields.COMBAT.get(1));
                nested(EditorFields.COMBAT.get(2));
                cycle("Runs from", FearMode.values(), fearMode,
                        mode -> Text.literal(mode.title), this::applyFearMode,
                        """
                        Decides what happens to the eleven things every villager runs from.
                          vanilla list   the eleven, untouched
                          vanilla plus   the eleven and the ones below as well
                          only these     the list below instead of the eleven
                          fears nothing  runs from nothing it sees
                        The two json fields behind this cannot both be written, which is why
                        this is one button and not two boxes.""");
                fearList = box(EditorFields.FEAR_LIST,
                        JsonEdit.ranges(root, fearMode == FearMode.REPLACE ? "flees_only_from" : "flees_from"),
                        this::applyFears);
                fearList.setEditable(fearMode == FearMode.ADD || fearMode == FearMode.REPLACE);
            }
            case PLAN -> {
                cycle("Day plan", PlanMode.values(), planMode,
                        mode -> Text.literal(mode.title), this::applyPlanMode,
                        """
                        Decides when this villager works, meets and sleeps.
                          vanilla plan  no field written, the plan every villager has
                          default       that same plan, said out loud
                          night         it shifted twelve hours: sleeps by day, works from
                                        tick 14000. /time set night is 13000 and still idle
                          written out   your own entries, in the box below
                        The plan is looked up per villager, so an override can set it.""");
                planEntries = box(EditorFields.PLAN_ENTRIES,
                        JsonEdit.schedule(root, "schedule"),
                        value -> {
                            if (planMode == PlanMode.CUSTOM) {
                                JsonEdit.setSchedule(root, "schedule", value);
                            }
                        });
                planEntries.setEditable(planMode == PlanMode.CUSTOM);
            }
        }

        rowList.setScrollAmount(scroll);
        addDrawableChild(rowList);

        int buttons = height - 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("Save and reload"), press -> save())
                .dimensions(width / 2 - 154, buttons, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), press -> close())
                .dimensions(width / 2 + 4, buttons, 150, 20).build());
    }

    /// Status-line room is reserved even when empty, so the rows do not jump when a save answers.
    private int rowsBottom() {
        return height - 40 - STATUS_LINES * 10;
    }

    private boolean inView(int y) {
        return y >= ROWS_TOP && y + ROW_HEIGHT <= rowsBottom();
    }

    private int fieldWidth() {
        return content - LABEL_WIDTH - ROW_GUTTER;
    }

    private void switchTo(Tab value) {
        tab = value;
        // A page change starts at the top; only a same-page re-init keeps the scroll.
        rowList = null;
        clearAndInit();
    }

    private void field(Spec spec) {
        String value = switch (spec.shape()) {
            case TEXT -> JsonEdit.text(root, spec.key());
            case LIST -> JsonEdit.list(root, spec.key());
            case NUMBER -> JsonEdit.number(root, spec.key());
            case RANGES -> JsonEdit.ranges(root, spec.key());
        };
        box(spec, value, typed -> write(spec, typed));
    }

    private void write(Spec spec, String typed) {
        switch (spec.shape()) {
            case TEXT -> JsonEdit.setText(root, spec.key(), typed);
            case LIST -> JsonEdit.setList(root, spec.key(), typed);
            case NUMBER -> JsonEdit.setNumber(root, spec.key(), typed);
            case RANGES -> JsonEdit.setRanges(root, spec.key(), typed);
        }
    }

    /// Removes `attack` entirely when both fields are emptied; the parser rejects a leftover `{}`.
    private void nested(Spec spec) {
        JsonObject attack = JsonEdit.object(root, "attack");
        box(spec, JsonEdit.number(attack, spec.key()), typed -> {
            JsonObject current = JsonEdit.object(root, "attack");
            JsonEdit.setNumber(current, spec.key(), typed);
            if (current.keySet().isEmpty()) {
                root.remove("attack");
            } else {
                root.add("attack", current);
            }
        });
    }

    /// The label carries the help text as a vanilla tooltip.
    private TextFieldWidget box(Spec spec, String value, Consumer<String> onChange) {
        TextWidget label = new TextWidget(LABEL_WIDTH, ROW_HEIGHT, Text.literal(spec.label()), textRenderer)
                .alignLeft()
                .setTextColor(LABEL);
        label.setTooltip(Tooltip.of(Text.literal(spec.help())));

        TextFieldWidget widget = new TextFieldWidget(textRenderer, fieldWidth(), ROW_HEIGHT, Text.literal(spec.label()));
        // The default is 32 characters, which a list of three block ids passes before it is half typed.
        widget.setMaxLength(1024);
        widget.setPlaceholder(Text.literal(spec.placeholder()));
        widget.setText(value);
        widget.setChangedListener(typed -> {
            onChange.accept(typed);
            afterTyping(widget, label, spec, typed);
            // Typing is a new question, so a list that was waved away comes back.
            suggestionsClosed = false;
        });
        sources.put(widget, spec.source());
        rowList.addRow(new EditorRowList.Row(label, widget));
        afterTyping(widget, label, spec, value);
        return widget;
    }

    /// What a box can answer on its own while it is being typed into.
    private void afterTyping(TextFieldWidget widget, TextWidget label, Spec spec, String typed) {
        String last = EditorFields.lastPart(typed);
        List<String> hits = EditorFields.suggest(spec.source(), typed);

        // Vanilla's own ghost completion, the one the chat command line uses.
        widget.setSuggestion(!last.isEmpty() && !hits.isEmpty() && hits.get(0).startsWith(last)
                ? hits.get(0).substring(last.length()) : "");

        // Red flags only an unusable workstation block; every other check stays the server's job.
        if (spec.source() != Source.FREE_BLOCK) {
            return;
        }
        String problem = "";
        if (!last.isEmpty()) {
            problem = EditorFields.ownerOf(last)
                    // Excludes its own block, so editing an existing profession is not flagged red.
                    .filter(owner -> !owner.equals("datadrivenvillagers:" + fileName))
                    .map(owner -> last + " already belongs to " + owner + ". Pick another block - the "
                            + "list under the box leaves the taken ones out.")
                    .orElse(EditorFields.unknownBlock(last) ? last + " is not a block in this game." : "");
        }
        widget.setEditableColor(problem.isEmpty() ? FIELD_OK : BAD);
        label.setTextColor(problem.isEmpty() ? LABEL : BAD);
        label.setTooltip(Tooltip.of(Text.literal(problem.isEmpty() ? spec.help() : problem + "\n\n" + spec.help())));
    }

    /// The one action here reaching past the profession file: writes into the world's own datapacks.
    private void tradesButton() {
        TextWidget label = new TextWidget(LABEL_WIDTH, ROW_HEIGHT, Text.literal("Default trades"), textRenderer)
                .alignLeft()
                .setTextColor(LABEL);
        label.setTooltip(Tooltip.of(Text.literal("""
                Writes the VillagersTradingPlus starting file for this profession
                into this world's own datapacks, filled in from the saved file -
                the same file /ddv scaffold makes, carried to where the game
                reads it. Save first; the trades are built from what is on disk.
                Never overwrites: a trades file already there stays as it is.
                The answer below names the one command still missing (/reload).""")));
        ButtonWidget trades = ButtonWidget.builder(Text.literal("Create default trades"), press -> {
            if (fileName == null || fileName.isEmpty()) {
                status = List.of(EditorResultPayload.bad("Give the file a name first, on the Basics page."));
                return;
            }
            status = List.of(EditorResultPayload.ok("Asking the server..."));
            ClientNetwork.send(new EditorTradesPayload(fileName));
        }).dimensions(0, 0, fieldWidth(), ROW_HEIGHT).build();
        rowList.addRow(new EditorRowList.Row(label, trades));
    }

    private <T> void cycle(String label, T[] values, T initial, Function<T, Text> name, Consumer<T> onChange,
                            String help) {
        TextWidget labelWidget = new TextWidget(LABEL_WIDTH, ROW_HEIGHT, Text.literal(label), textRenderer)
                .alignLeft()
                .setTextColor(LABEL);
        labelWidget.setTooltip(Tooltip.of(Text.literal(help)));
        CyclingButtonWidget<T> widget = CyclingButtonWidget.builder(name)
                .values(values)
                .initially(initial)
                .omitKeyText()
                .build(0, 0, fieldWidth(), ROW_HEIGHT, Text.literal(label), (button, value) -> onChange.accept(value));
        rowList.addRow(new EditorRowList.Row(labelWidget, (ClickableWidget) widget));
    }

    /// Never throws on a bad value; `valueOf` would, closing the editor on a typo instead of letting it be corrected.
    private static <T extends Enum<T>> T pick(Class<T> type, String raw, T fallback) {
        for (T value : type.getEnumConstants()) {
            if (value.name().equalsIgnoreCase(raw)) {
                return value;
            }
        }
        return fallback;
    }

    private FearMode readFearMode() {
        if (JsonEdit.has(root, "flees_only_from")) {
            return JsonEdit.isEmptyArray(root, "flees_only_from") ? FearMode.NOTHING : FearMode.REPLACE;
        }
        return JsonEdit.has(root, "flees_from") ? FearMode.ADD : FearMode.VANILLA;
    }

    private void applyFearMode(FearMode mode) {
        fearMode = mode;
        applyFears(fearList == null ? "" : fearList.getText());
        clearAndInit();
    }

    /// Only one of the two keys may be in the file; the parser rejects both at once, so this never writes both.
    private void applyFears(String list) {
        root.remove("flees_from");
        root.remove("flees_only_from");
        switch (fearMode) {
            case VANILLA -> {
            }
            case ADD -> JsonEdit.setRanges(root, "flees_from", list);
            case REPLACE -> JsonEdit.setRanges(root, "flees_only_from", list);
            case NOTHING -> root.add("flees_only_from", new JsonArray());
        }
    }

    private PlanMode readPlanMode() {
        if (!JsonEdit.has(root, "schedule")) {
            return PlanMode.VANILLA;
        }
        if (root.get("schedule").isJsonArray()) {
            return PlanMode.CUSTOM;
        }
        return JsonEdit.text(root, "schedule").equalsIgnoreCase("night") ? PlanMode.NIGHT : PlanMode.DEFAULT;
    }

    private void applyPlanMode(PlanMode mode) {
        planMode = mode;
        String written = planEntries == null ? "" : planEntries.getText();
        switch (mode) {
            case VANILLA -> root.remove("schedule");
            case DEFAULT -> JsonEdit.setText(root, "schedule", "default");
            case NIGHT -> JsonEdit.setText(root, "schedule", "night");
            case CUSTOM -> JsonEdit.setSchedule(root, "schedule", written);
        }
        clearAndInit();
    }

    /// The screen's own focused child is the row list; this follows it down to the field inside.
    private Element focusedLeaf() {
        Element current = getFocused();
        while (current instanceof ParentElement parent && parent.getFocused() != null) {
            current = parent.getFocused();
        }
        return current;
    }

    private void updateSuggestions() {
        Element focused = focusedLeaf();
        if (focused != lastFocused) {
            // A different box asks a different question, so a closed suggestion list reopens in this one.
            lastFocused = focused;
            suggestionsClosed = false;
        }
        suggestions = List.of();
        if (suggestionsClosed || !(focused instanceof TextFieldWidget widget) || !widget.isFocused()
                || !inView(widget.getY())) {
            return;
        }
        // A row stops being positioned once it scrolls out, so its widget's own y can be stale for this frame.
        EditorRowList.Row row = rowList.getFocused();
        int index = row == null ? -1 : rowList.children().indexOf(row);
        if (index < 0 || !inView(rowList.rowTop(index))) {
            return;
        }
        Source source = sources.get(widget);
        if (source == null || source == Source.NONE) {
            return;
        }
        List<String> hits = EditorFields.suggest(source, widget.getText());
        String last = EditorFields.lastPart(widget.getText());
        if (hits.isEmpty() || (hits.size() == 1 && hits.get(0).equals(last))) {
            return;
        }
        suggestions = hits;
        suggestionX = widget.getX();
        suggestionY = widget.getY() + ROW_HEIGHT;
        suggestionWidth = widget.getWidth();
    }

    /// Replaces only the part being typed, so the rest of a comma list survives; elsewhere it closes the list.
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!suggestions.isEmpty()) {
            boolean inside = mouseX >= suggestionX && mouseX <= suggestionX + suggestionWidth
                    && mouseY >= suggestionY && mouseY < suggestionY + suggestions.size() * SUGGESTION_HEIGHT;
            if (inside) {
                int index = (int) ((mouseY - suggestionY) / SUGGESTION_HEIGHT);
                if (focusedLeaf() instanceof TextFieldWidget widget) {
                    String text = widget.getText();
                    int comma = text.lastIndexOf(',');
                    widget.setText(comma < 0
                            ? suggestions.get(index)
                            : text.substring(0, comma + 1) + " " + suggestions.get(index));
                }
                suggestions = List.of();
                return true;
            }
            suggestionsClosed = true;
            suggestions = List.of();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !suggestions.isEmpty()) {
            suggestionsClosed = true;
            suggestions = List.of();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void save() {
        if (fileName == null || fileName.isEmpty()) {
            status = List.of(EditorResultPayload.bad("Give the file a name first, on the Basics page."));
            return;
        }
        String json = GSON.toJson(root);
        if (json.length() > EditorOpenPayload.MAX_JSON) {
            status = List.of(EditorResultPayload.bad("Too long to save (" + json.length()
                    + " characters, " + EditorOpenPayload.MAX_JSON + " max). Shorten the file."));
            return;
        }
        status = List.of(EditorResultPayload.ok("Saving..."));
        ClientNetwork.send(new EditorSavePayload(fileName, json));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        // Runs after the rows are placed for this frame, so a row that just scrolled out is not read as in view.
        updateSuggestions();

        int content = Math.min(MAX_CONTENT, width - 40);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, TITLE);
        String subtitle = (isNew ? "new file" : fileName + ".json")
                + "  -  hover a field name;  * needs a restart";
        context.drawCenteredTextWithShadow(textRenderer, subtitle, width / 2, 26, HINT);

        if (!suggestions.isEmpty()) {
            // Field text is flushed after this fill, so only depth keeps the list on top.
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 300);
            int box = suggestions.size() * SUGGESTION_HEIGHT;
            context.fill(suggestionX, suggestionY, suggestionX + suggestionWidth, suggestionY + box,
                    SUGGESTION_BACKGROUND);
            for (int i = 0; i < suggestions.size(); i++) {
                int rowY = suggestionY + i * SUGGESTION_HEIGHT;
                boolean under = mouseY >= rowY && mouseY < rowY + SUGGESTION_HEIGHT
                        && mouseX >= suggestionX && mouseX <= suggestionX + suggestionWidth;
                context.drawTextWithShadow(textRenderer, suggestions.get(i), suggestionX + 3, rowY + 2,
                        under ? TITLE : LABEL);
            }
            context.getMatrices().pop();
        }

        // Clipped to its reserved strip, so a long reload answer never paints over the rows above.
        List<Wrapped> lines = wrapped(content);
        int reservedTop = height - 34 - STATUS_LINES * 10;
        int statusY = Math.max(reservedTop, height - 34 - lines.size() * 10);
        context.enableScissor(0, reservedTop, width, height - 30);
        for (Wrapped line : lines) {
            context.drawTextWithShadow(textRenderer, line.text(), (width - content) / 2, statusY,
                    line.colour());
            statusY += 10;
        }
        context.disableScissor();
    }

    private record Wrapped(OrderedText text, int colour) {
    }

    private List<Wrapped> wrapped(int content) {
        List<Wrapped> lines = new ArrayList<>();
        for (Note note : status) {
            int colour = switch (note.level()) {
                case OK -> GOOD;
                case WARN -> WARN;
                case BAD -> BAD;
            };
            for (OrderedText line : textRenderer.wrapLines(Text.literal(note.text()), content)) {
                lines.add(new Wrapped(line, colour));
            }
        }
        return lines;
    }

    /// Judged by what villagers actually do; pausing the game would hide that.
    @Override
    public boolean shouldPause() {
        return false;
    }
}
