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
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/// Builds a profession without leaving the game, and explains every field while it does.
///
/// The screen is the short half of the loop the mod already had: write a file, reload, read `/ddv
/// why`, correct. It takes the writing and the reloading away and keeps the reading - a save runs the
/// same reload as the command and puts the same answer at the bottom of this screen.
public final class ProfessionEditorScreen extends Screen {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final int MAX_CONTENT = 400;
    private static final int LABEL_WIDTH = 148;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_STEP = ROW_HEIGHT + 4;
    private static final int ROWS_TOP = 70;
    private static final int SCROLL_STEP = 12;
    private static final int SCROLLBAR = 10;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SUGGESTION_HEIGHT = 12;
    private static final int ESCAPE = 256;
    /// How many wrapped report lines get room; overflow is cut by the scissor in render, never painted over rows.
    private static final int STATUS_LINES = 5;

    /// Colours carry full alpha: from 1.21.6 on, the text renderer draws alpha zero as invisible.
    private static final int TITLE = 0xFFFFFFFF;
    private static final int LABEL = 0xFFA0A0A0;
    private static final int HINT = 0xFF707070;
    private static final int GOOD = 0xFF7FCF5F;
    private static final int WARN = 0xFFFFD24A;
    private static final int BAD = 0xFFFF6B6B;
    private static final int FIELD_OK = 0xFFE0E0E0;
    private static final int SUGGESTION_BACKGROUND = 0xF0100010;
    private static final int PANEL = 0x70000000;
    private static final int BAR_TRACK = 0xFF000000;
    private static final int BAR_THUMB = 0xFF808080;
    private static final int BAR_THUMB_LIGHT = 0xFFC0C0C0;

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

    /// One field name on the left, and the rectangle that has to be hovered to read what it means.
    private record Row(String label, String help, int x, int baseY, int width) {

        boolean under(int mouseX, int mouseY, int y) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y - 6 && mouseY <= y + ROW_HEIGHT - 6;
        }
    }

    private record Placed(ClickableWidget widget, int baseY) {
    }

    private final JsonObject root;
    private final boolean isNew;
    private String fileName;

    private Tab tab = Tab.BASICS;
    private final List<Row> rows = new ArrayList<>();
    private final List<Placed> placed = new ArrayList<>();
    private int scroll;
    private int maxScroll;
    private boolean draggingBar;
    private int left;
    private int content;
    private final Map<TextFieldWidget, Source> sources = new HashMap<>();
    private final Map<String, String> problems = new HashMap<>();

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
        rows.clear();
        placed.clear();
        sources.clear();
        suggestions = List.of();
        draggingBar = false;
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

        int top = ROWS_TOP;
        int step = ROW_STEP;
        int row = 0;
        switch (tab) {
            case BASICS -> {
                // Lower-cased live, not only on save, so the author sees the real filename right away.
                TextFieldWidget name = box(left, content, top + step * row++, EditorFields.BASICS.get(0),
                        fileName, value -> fileName = value.trim().toLowerCase(Locale.ROOT));
                name.setTextPredicate(typed -> typed.equals(typed.toLowerCase(Locale.ROOT)));
                name.setEditable(isNew);
                for (int i = 1; i < EditorFields.BASICS.size(); i++) {
                    field(left, content, top + step * row++, EditorFields.BASICS.get(i));
                }
            }
            case LOOK -> {
                field(left, content, top + step * row++, EditorFields.LOOK.get(0));
                field(left, content, top + step * row++, EditorFields.LOOK.get(1));
                cycle(left, content, top + step * row++, "Hat", HatKind.values(),
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
                field(left, content, top + step * row++, EditorFields.LOOK.get(2));
                field(left, content, top + step * row, EditorFields.LOOK.get(3));
            }
            case WORK -> {
                cycle(left, content, top + step * row++, "Work behaviour", WorkBehaviour.values(),
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
                    field(left, content, top + step * row++, spec);
                }
                tradesButton(left, content, top + step * row);
            }
            case COMBAT -> {
                field(left, content, top + step * row++, EditorFields.COMBAT.get(0));
                nested(left, content, top + step * row++, EditorFields.COMBAT.get(1));
                nested(left, content, top + step * row++, EditorFields.COMBAT.get(2));
                cycle(left, content, top + step * row++, "Runs from", FearMode.values(), fearMode,
                        mode -> Text.literal(mode.title), this::applyFearMode,
                        """
                        Decides what happens to the eleven things every villager runs from.
                          vanilla list   the eleven, untouched
                          vanilla plus   the eleven and the ones below as well
                          only these     the list below instead of the eleven
                          fears nothing  runs from nothing it sees
                        The two json fields behind this cannot both be written, which is why
                        this is one button and not two boxes.""");
                fearList = box(left, content, top + step * row, EditorFields.FEAR_LIST,
                        JsonEdit.ranges(root, fearMode == FearMode.REPLACE ? "flees_only_from" : "flees_from"),
                        this::applyFears);
                fearList.setEditable(fearMode == FearMode.ADD || fearMode == FearMode.REPLACE);
            }
            case PLAN -> {
                cycle(left, content, top + step * row++, "Day plan", PlanMode.values(), planMode,
                        mode -> Text.literal(mode.title), this::applyPlanMode,
                        """
                        Decides when this villager works, meets and sleeps.
                          vanilla plan  no field written, the plan every villager has
                          default       that same plan, said out loud
                          night         it shifted twelve hours: sleeps by day, works from
                                        tick 14000. /time set night is 13000 and still idle
                          written out   your own entries, in the box below
                        The plan is looked up per villager, so an override can set it.""");
                planEntries = box(left, content, top + step * row, EditorFields.PLAN_ENTRIES,
                        JsonEdit.schedule(root, "schedule"),
                        value -> {
                            if (planMode == PlanMode.CUSTOM) {
                                JsonEdit.setSchedule(root, "schedule", value);
                            }
                        });
                planEntries.setEditable(planMode == PlanMode.CUSTOM);
            }
        }

        maxScroll = Math.max(0, rowCount() * ROW_STEP - (rowsBottom() - ROWS_TOP));
        scroll = Math.min(scroll, maxScroll);
        applyScroll();

        int buttons = height - 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("Save and reload"), press -> save())
                .dimensions(width / 2 - 154, buttons, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), press -> close())
                .dimensions(width / 2 + 4, buttons, 150, 20).build());
    }

    private int rowCount() {
        return switch (tab) {
            case BASICS -> EditorFields.BASICS.size();
            case LOOK -> EditorFields.LOOK.size() + 1;
            case WORK -> EditorFields.WORK.size() + 2;
            case COMBAT -> EditorFields.COMBAT.size() + 2;
            case PLAN -> 2;
        };
    }

    /// Status-line room is reserved even when empty, so the rows do not jump when a save answers.
    private int rowsBottom() {
        return height - 40 - STATUS_LINES * 10;
    }

    /// A partly visible row stays drawn and is clipped at the panel edge, so the list reads as scrollable.
    private boolean overlaps(int y) {
        return y + ROW_HEIGHT > ROWS_TOP && y < rowsBottom();
    }

    private boolean inView(int y) {
        return y >= ROWS_TOP && y + ROW_HEIGHT <= rowsBottom();
    }

    private boolean inRowArea(double mouseY) {
        return mouseY >= ROWS_TOP && mouseY < rowsBottom();
    }

    private void scrollTo(int value) {
        scroll = Math.max(0, Math.min(maxScroll, value));
        applyScroll();
    }

    private void applyScroll() {
        for (Placed entry : placed) {
            int y = entry.baseY() - scroll;
            entry.widget().setY(y);
            entry.widget().visible = overlaps(y);
            if (!entry.widget().visible && getFocused() == entry.widget()) {
                setFocused(null);
            }
        }
    }

    private int barX() {
        return left + content - SCROLLBAR_WIDTH;
    }

    private int thumbHeight() {
        int track = rowsBottom() - ROWS_TOP;
        return Math.max(10, track * track / (track + maxScroll));
    }

    private void dragBarTo(double mouseY) {
        int free = rowsBottom() - ROWS_TOP - thumbHeight();
        scrollTo((int) Math.round((mouseY - ROWS_TOP - thumbHeight() / 2.0) * maxScroll / Math.max(1, free)));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (maxScroll > 0 && inRowArea(mouseY)) {
            scrollTo(scroll - (int) Math.signum(amount) * SCROLL_STEP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingBar) {
            dragBarTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingBar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void switchTo(Tab value) {
        tab = value;
        scroll = 0;
        clearAndInit();
    }

    // ---- rows -----------------------------------------------------------------------------------

    private void field(int left, int content, int y, Spec spec) {
        String value = switch (spec.shape()) {
            case TEXT -> JsonEdit.text(root, spec.key());
            case LIST -> JsonEdit.list(root, spec.key());
            case NUMBER -> JsonEdit.number(root, spec.key());
            case RANGES -> JsonEdit.ranges(root, spec.key());
        };
        box(left, content, y, spec, value, typed -> write(spec, typed));
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
    private void nested(int left, int content, int y, Spec spec) {
        JsonObject attack = JsonEdit.object(root, "attack");
        box(left, content, y, spec, JsonEdit.number(attack, spec.key()), typed -> {
            JsonObject current = JsonEdit.object(root, "attack");
            JsonEdit.setNumber(current, spec.key(), typed);
            if (current.keySet().isEmpty()) {
                root.remove("attack");
            } else {
                root.add("attack", current);
            }
        });
    }

    /// Registers a `Row`, so hovering the label, not the box, is what shows the help tooltip.
    private TextFieldWidget box(int left, int content, int y, Spec spec, String value,
                                Consumer<String> onChange) {
        rows.add(new Row(spec.label(), spec.help(), left, y + 6, LABEL_WIDTH));

        TextFieldWidget widget = new TextFieldWidget(textRenderer, left + LABEL_WIDTH, y,
                content - LABEL_WIDTH - SCROLLBAR, ROW_HEIGHT, Text.literal(spec.label()));
        // The default is 32 characters, which a list of three block ids passes before it is half typed.
        widget.setMaxLength(1024);
        widget.setPlaceholder(Text.literal(spec.placeholder()));
        widget.setText(value);
        widget.setChangedListener(typed -> {
            onChange.accept(typed);
            afterTyping(widget, spec, typed);
            // Typing is a new question, so a list that was waved away comes back.
            suggestionsClosed = false;
        });
        addSelectableChild(widget);
        placed.add(new Placed(widget, y));
        sources.put(widget, spec.source());
        afterTyping(widget, spec, value);
        return widget;
    }

    /// What a box can answer on its own while it is being typed into.
    private void afterTyping(TextFieldWidget widget, Spec spec, String typed) {
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
        if (problem.isEmpty()) {
            problems.remove(spec.label());
        } else {
            problems.put(spec.label(), problem);
        }
    }

    /// The one action here reaching past the profession file: writes into the world's own datapacks.
    private void tradesButton(int left, int content, int y) {
        rows.add(new Row("Default trades", """
                Writes the VillagersTradingPlus starting file for this profession
                into this world's own datapacks, filled in from the saved file -
                the same file /ddv scaffold makes, carried to where the game
                reads it. Save first; the trades are built from what is on disk.
                Never overwrites: a trades file already there stays as it is.
                The answer below names the one command still missing (/reload).""",
                left, y + 6, LABEL_WIDTH));
        ButtonWidget trades = addSelectableChild(ButtonWidget.builder(Text.literal("Create default trades"), press -> {
            if (fileName == null || fileName.isEmpty()) {
                status = List.of(EditorResultPayload.bad("Give the file a name first, on the Basics page."));
                return;
            }
            status = List.of(EditorResultPayload.ok("Asking the server..."));
            ClientNetwork.send(new EditorTradesPayload(fileName));
        }).dimensions(left + LABEL_WIDTH, y, content - LABEL_WIDTH - SCROLLBAR, ROW_HEIGHT).build());
        placed.add(new Placed(trades, y));
    }

    private <T> void cycle(int left, int content, int y, String label, T[] values, T initial,
                           Function<T, Text> name, Consumer<T> onChange, String help) {
        rows.add(new Row(label, help, left, y + 6, LABEL_WIDTH));
        CyclingButtonWidget<T> widget = CyclingButtonWidget.builder(name)
                .values(values)
                .initially(initial)
                .omitKeyText()
                .build(left + LABEL_WIDTH, y, content - LABEL_WIDTH - SCROLLBAR, ROW_HEIGHT,
                        Text.literal(label), (button, value) -> onChange.accept(value));
        addSelectableChild((ClickableWidget) widget);
        placed.add(new Placed(widget, y));
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

    // ---- the two fields that cannot be a plain box ----------------------------------------------

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

    // ---- suggestions under the focused box -------------------------------------------------------

    private void updateSuggestions() {
        if (getFocused() != lastFocused) {
            // A different box asks a different question, so a closed suggestion list reopens in this one.
            lastFocused = getFocused();
            suggestionsClosed = false;
        }
        suggestions = List.of();
        if (suggestionsClosed || !(getFocused() instanceof TextFieldWidget widget) || !widget.isFocused()
                || !inView(widget.getY())) {
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
                if (getFocused() instanceof TextFieldWidget widget) {
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
        if (maxScroll > 0 && inRowArea(mouseY) && mouseX >= barX() && mouseX < barX() + SCROLLBAR_WIDTH) {
            draggingBar = true;
            dragBarTo(mouseY);
            return true;
        }
        if (inRowArea(mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        // The clipped part of an edge row lies outside the panel and must not take the click.
        for (Placed entry : placed) {
            entry.widget().visible = false;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        applyScroll();
        return handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == ESCAPE && !suggestions.isEmpty()) {
            suggestionsClosed = true;
            suggestions = List.of();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- sending and drawing --------------------------------------------------------------------

    private void save() {
        if (fileName == null || fileName.isEmpty()) {
            status = List.of(EditorResultPayload.bad("Give the file a name first, on the Basics page."));
            return;
        }
        String json = GSON.toJson(root);
        if (json.length() > EditorOpenPayload.MAX_JSON) {
            status = List.of(EditorResultPayload.bad("This file is " + json.length() + " characters and one "
                    + "packet carries " + EditorOpenPayload.MAX_JSON + ". Nothing was sent; shorten it, or edit "
                    + "the file where the server keeps it."));
            return;
        }
        status = List.of(EditorResultPayload.ok("Saving..."));
        ClientNetwork.send(new EditorSavePayload(fileName, json));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateSuggestions();
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);

        int content = Math.min(MAX_CONTENT, width - 40);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, TITLE);
        String subtitle = (isNew ? "new file" : fileName + ".json")
                + "  -  hover a field name;  * needs a restart";
        context.drawCenteredTextWithShadow(textRenderer, subtitle, width / 2, 26, HINT);

        context.fill(left - 4, ROWS_TOP - 4, left + content + 4, rowsBottom() + 4, PANEL);
        // A text field draws its border one pixel outside its bounds.
        context.enableScissor(0, ROWS_TOP - 1, width, rowsBottom() + 1);
        for (Row row : rows) {
            int y = row.baseY() - scroll;
            if (overlaps(y - 6)) {
                context.drawTextWithShadow(textRenderer, row.label(), row.x(), y,
                        problems.containsKey(row.label()) ? BAD : LABEL);
            }
        }
        // Outside the panel the mouse is over the clipped part of a row, which must not light up.
        int rowMouseY = inRowArea(mouseY) ? mouseY : -1;
        for (Placed entry : placed) {
            if (entry.widget().visible) {
                entry.widget().render(context, mouseX, rowMouseY, delta);
            }
        }
        context.disableScissor();
        drawScrollBar(context);

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

        // Last, so it lies over the suggestions and the report rather than under them.
        drawHelp(context, mouseX, mouseY);
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

    private void drawScrollBar(DrawContext context) {
        if (maxScroll <= 0) {
            return;
        }
        int x = barX();
        int thumb = thumbHeight();
        int thumbY = ROWS_TOP + (rowsBottom() - ROWS_TOP - thumb) * scroll / maxScroll;
        context.fill(x, ROWS_TOP, x + SCROLLBAR_WIDTH, rowsBottom(), BAR_TRACK);
        context.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumb, BAR_THUMB);
        context.fill(x, thumbY, x + SCROLLBAR_WIDTH - 1, thumbY + thumb - 1, BAR_THUMB_LIGHT);
    }

    private void drawHelp(DrawContext context, int mouseX, int mouseY) {
        for (Row row : rows) {
            int y = row.baseY() - scroll;
            if (!inRowArea(mouseY) || !overlaps(y - 6) || !row.under(mouseX, mouseY, y)) {
                continue;
            }
            // Wrapped to the room right of the cursor, where vanilla places a tooltip first.
            int wrap = Math.max(120, width - mouseX - 24);
            List<OrderedText> help = new ArrayList<>();
            String problem = problems.get(row.label());
            if (problem != null) {
                help.addAll(textRenderer.wrapLines(Text.literal(problem), wrap));
                help.add(OrderedText.EMPTY);
            }
            for (String line : row.help().split("\n")) {
                help.addAll(line.isEmpty() ? List.of(OrderedText.EMPTY) : textRenderer.wrapLines(Text.literal(line), wrap));
            }
            context.drawOrderedTooltip(textRenderer, help, mouseX, mouseY);
            return;
        }
    }

    /// Judged by what villagers actually do; pausing the game would hide that.
    @Override
    public boolean shouldPause() {
        return false;
    }
}
