package pers.roinflam.battlecorrection.client.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.Loader;
import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.client.editor.ui.EditBox;
import pers.roinflam.battlecorrection.client.editor.ui.GuiEventListener;
import pers.roinflam.battlecorrection.client.editor.ui.GuiGraphics;
import pers.roinflam.battlecorrection.client.editor.ui.Keys;
import pers.roinflam.battlecorrection.client.editor.ui.Screen;
import pers.roinflam.battlecorrection.client.editor.widget.ColorToolbar;
import pers.roinflam.battlecorrection.client.editor.widget.FlatButton;
import pers.roinflam.battlecorrection.client.editor.widget.FlatEditBox;
import pers.roinflam.battlecorrection.client.editor.widget.GradientBar;
import pers.roinflam.battlecorrection.client.editor.widget.PickerWidget;
import pers.roinflam.battlecorrection.client.editor.widget.PresetStrip;
import pers.roinflam.battlecorrection.client.editor.widget.SwatchStrip;
import pers.roinflam.battlecorrection.client.editor.widget.ToggleChip;
import pers.roinflam.battlecorrection.editor.ColorMath;
import pers.roinflam.battlecorrection.editor.LegacyComponents;
import pers.roinflam.battlecorrection.editor.LegacyEdits;
import pers.roinflam.battlecorrection.editor.LegacyText;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 调色盘：挑原版颜色、做多色标渐变，然后写进名字 / Lore 的输入框
 * <p>
 * 从颜色工具条的"调色盘"按钮打开，编辑器还开在后面。1.12.2 的文字只有原版 16 色，所以上半是 16 色色板（8 × 2）、
 * 原色 / 新色对比和当前颜色的名字、最近用过的 8 个颜色；下半是渐变：2~8 个等距色标、一条换成原版颜色后的色带、
 * 一排预设、B / I / U / S 格式开关和一行把渐变真的画上去的预览文字。
 * 色板改的永远是选中的那个色标，所以"挑一个颜色插进去"和"调渐变里的某一段"用的是同一套控件。
 * <p>
 * 渐变照样按色标在 RGB 上线性插值，每个字再换成最接近的原版颜色（CIELAB 色差，见 {@link ColorMath#nearestLegacy}），
 * 相邻几个字颜色相同时只写一次代码；色带和预览画的都是换算后的结果，和提示框里看到的一样。物品里只会写原版颜色码。
 * <p>
 * 底部三个按钮：插入颜色（光标处插这个颜色的代码；有选区就把选区整体换成这个颜色，原有颜色码去掉、格式码保留）、
 * 应用渐变（选区或整个框）、取消。回车执行高亮的那个：最近碰的是渐变那一半（色标、预设、增删反转）就是应用渐变，否则是插入颜色；
 * 用 Tab 停在某个按钮上时回车按那个按钮。Esc / 取消原样回到编辑器，什么都不改。
 * <p>
 * 打开时把目标框的文字和选区记下来：回到编辑器时编辑器会整个重建（回到上一个界面就是重新 init），输入框是新的，
 * 选区早没了，只能按记下的位置改。改完把焦点还给目标框，并选中改写过的那一段，和工具条上的渐变按钮一样。
 * <p>
 * 色标、选中的色标、格式开关放在静态字段里：缩放窗口、关掉再开都还在。工具条两个颜色框的颜色当作渐变头尾的初值传进来，
 * 和上次留下的头尾不一样（在工具条上改过）就按它们重新开始；应用之后把头尾颜色写回那两个框。
 * 最近使用的颜色存在 config 目录的一个小 JSON 里（{@code {"recent":["&c","&6"]}}），读写失败就当没有。
 */
public class ColorPaletteScreen extends Screen {

    /**
     * 应用之后把渐变的头尾颜色交还给工具条
     */
    @FunctionalInterface
    public interface ColorsApplied {
        /**
         * @param head 第一个色标
         * @param tail 最后一个色标
         */
        void accept(int head, int tail);
    }

    // 布局常量

    private static final int MAX_WIDTH = 400;
    private static final int MARGIN = 8;
    private static final int PAD = 10;
    /**
     * 面板四角的准线角饰占的宽度，标题和底栏从它里面让开
     */
    private static final int CORNER_CLEARANCE = 22;
    private static final int TOP_INSET = 26;
    private static final int ROW = 12;
    private static final int GRID_COLUMNS = 8;
    private static final int GRID_CELL = 14;
    private static final int GRID_GAP = 2;
    private static final int COMPARE_CELL = 12;
    private static final int RECENT_CELL = 10;
    private static final int TOGGLE_WIDTH = 14;
    private static final int STOP_BUTTON_WIDTH = 16;
    private static final int STOP_BUTTON_HEIGHT = 14;
    private static final int PREVIEW_HEIGHT = 14;
    private static final int FOOTER_BUTTON_HEIGHT = 16;
    private static final int FOOTER_BUTTON_MIN_WIDTH = 48;

    // 数量

    private static final int MIN_STOPS = 2;
    private static final int MAX_STOPS = 8;
    private static final int RECENT_SIZE = 8;
    /**
     * 预览文字最多几个字
     */
    private static final int SAMPLE_CHARS = 24;

    private static final String KEY = "gui.battlecorrection.editor.palette.";
    private static final String COLOR_KEY = "gui.battlecorrection.editor.color.";
    private static final String RECENT_FILE = "battlecorrection-editor-colors.json";

    /**
     * 格式开关：格式、显示的字母、提示翻译键（沿用工具条格式按钮的提示）
     */
    private static final TextFormatting[] FORMATS = {
            TextFormatting.BOLD, TextFormatting.ITALIC, TextFormatting.UNDERLINE, TextFormatting.STRIKETHROUGH
    };
    private static final String[] FORMAT_LETTERS = {"B", "I", "U", "S"};
    private static final String[] FORMAT_KEYS = {"bold", "italic", "underline", "strikethrough"};

    /**
     * 预设渐变（名字的翻译键后缀 + 色标，都是原版颜色）
     */
    private static final String[] PRESET_KEYS = {
            "rainbow", "fire", "ocean", "sunset", "forest", "frost", "candy", "gold", "neon", "grayscale"
    };
    private static final int[][] PRESETS = {
            codes("c6eab9d"),
            codes("e6c4"),
            codes("b391"),
            codes("e6c5"),
            codes("a2"),
            codes("fb9"),
            codes("dfb"),
            codes("e6e"),
            codes("d9ba"),
            codes("f78")
    };

    // 跨次打开保留的状态

    private static int[] stops = {0xFF5555, 0xFFFF55};
    private static int selected;
    private static int formats;
    /**
     * 回车执行哪个：true = 应用渐变，false = 插入颜色
     */
    private static boolean gradientArea;
    /**
     * 上次打开（或应用）时工具条给的头尾颜色；这次传进来的不一样就重新开始
     */
    private static int seedFrom = -1;
    private static int seedTo = -1;

    private static final int[] RECENT = new int[RECENT_SIZE];
    private static int recentCount;
    private static boolean recentLoaded;

    // 这一次打开

    private final Supplier<EditBox> target;
    private final ColorsApplied onApplied;
    private final LegacyComponents.Look start;
    private final boolean hasTarget;
    private final String sourceText;
    private final int selStart;
    private final int selEnd;
    /**
     * 预览用的示例（编辑文本写法）和它从哪来的说明
     */
    private final String sample;
    private final Component sampleSource;

    /**
     * 原色：打开时（或选中当前色标时）的颜色
     */
    private int original;
    private Component preview = Component.empty();

    // 布局
    private int boxX;
    private int boxY;
    private int boxWidth;
    private int boxHeight;
    private int contentX;
    private int contentWidth;
    private int rightX;
    private int compareY;
    private int compareRight;
    private int recentY;
    private int headerY;
    private int formatY;
    private int togglesRight;
    private int previewY;
    private int footerY;

    // 控件
    private final List<PickerWidget> pickers = new ArrayList<>();
    @Nullable
    private SwatchStrip gridStrip;
    @Nullable
    private GradientBar gradientBar;
    @Nullable
    private PresetStrip presetStrip;
    @Nullable
    private SwatchStrip recentStrip;
    @Nullable
    private FlatButton addButton;
    @Nullable
    private FlatButton removeButton;
    @Nullable
    private FlatButton reverseButton;
    @Nullable
    private FlatButton insertButton;
    @Nullable
    private FlatButton applyButton;

    // 每帧画的文字（算好缓存）
    private final Component compareCaption = Component.translatable(KEY + "compare");
    private final Component recentCaption = Component.translatable(KEY + "recent");
    private final Component gradientCaption = Component.translatable(KEY + "gradient");
    private final Component formatCaption = Component.translatable(KEY + "format");
    @Nullable
    private Component stopCount;
    private int stopCountFor = -1;
    @Nullable
    private Component colorName;
    private int colorNameFor = -1;
    @Nullable
    private String footerText;
    private boolean showCompareCaption;

    /**
     * 行首样式按 §r 基础样式（白色、不斜体）算
     *
     * @param parent    关掉后回到的界面（编辑器）
     * @param target    目标输入框（回到编辑器后再取一次：编辑器重建后是新的框）
     * @param from      渐变第一个色标的初值（不是原版颜色时换成最接近的）
     * @param to        最后一个色标的初值
     * @param onApplied 应用之后回写头尾颜色
     */
    public ColorPaletteScreen(@Nonnull GuiScreen parent, @Nonnull Supplier<EditBox> target, int from, int to,
                              @Nonnull ColorsApplied onApplied) {
        this(parent, target, from, to, LegacyComponents.RESET, onApplied);
    }

    /**
     * @param parent    关掉后回到的界面（编辑器）
     * @param target    目标输入框（回到编辑器后再取一次：编辑器重建后是新的框）
     * @param from      渐变第一个色标的初值（不是原版颜色时换成最接近的）
     * @param to        最后一个色标的初值
     * @param start     目标那一行行首没写代码的字是什么样子（改写选区后补恢复代码用，见 {@link LegacyComponents#start}）
     * @param onApplied 应用之后回写头尾颜色
     */
    public ColorPaletteScreen(@Nonnull GuiScreen parent, @Nonnull Supplier<EditBox> target, int from, int to,
                              @Nonnull LegacyComponents.Look start, @Nonnull ColorsApplied onApplied) {
        super(Component.translatable(KEY + "title"), parent);
        this.target = target;
        this.onApplied = onApplied;
        this.start = start;

        EditBox box = usable(target.get());
        hasTarget = box != null;
        sourceText = box == null ? "" : box.getValue();
        if (box instanceof FlatEditBox) {
            selStart = ((FlatEditBox) box).selectionStart();
            selEnd = ((FlatEditBox) box).selectionEnd();
        } else {
            selStart = box == null ? 0 : box.getCursorPosition();
            selEnd = selStart;
        }

        from = ColorMath.quantize(from);
        to = ColorMath.quantize(to);
        if (from != seedFrom || to != seedTo) {
            stops = new int[]{from, to};
            selected = 0;
            gradientArea = false;
            seedFrom = from;
            seedTo = to;
        }
        selected = Math.max(0, Math.min(stops.length - 1, selected));
        original = stops[selected];
        loadRecent();

        String picked = hasTarget ? LegacyEdits.sample(sourceText, selStart, selEnd, SAMPLE_CHARS) : "";
        if (LegacyText.plain(picked).trim().isEmpty()) {
            String fallback = Component.translatable(KEY + "preview.default").getString();
            LegacyText.Writer writer = new LegacyText.Writer(fallback.length() + 4);
            for (int i = 0; i < fallback.length(); i++) {
                writer.literal(fallback.charAt(i));
            }
            sample = writer.build();
            // 没有目标框时这个位置改成提示：底栏左边太窄，放不下一句话
            sampleSource = Component.translatable(KEY + (hasTarget ? "preview.sample" : "no_target"));
        } else {
            sample = picked;
            sampleSource = Component.translatable(KEY + (selStart != selEnd ? "preview.selection" : "preview.whole"));
        }
        refreshPreview();
    }

    @Nullable
    private static EditBox usable(@Nullable EditBox box) {
        return box == null || !box.visible || !box.active ? null : box;
    }

    /**
     * 一串代码字符 → 对应原版颜色的 RGB
     */
    @Nonnull
    private static int[] codes(@Nonnull String chars) {
        int[] result = new int[chars.length()];
        for (int i = 0; i < result.length; i++) {
            TextFormatting formatting = LegacyText.codeOf(chars.charAt(i));
            result[i] = formatting == null ? 0xFFFFFF : ColorMath.legacyRgb(formatting);
        }
        return result;
    }

    // 布局

    @Override
    protected void init() {
        pickers.clear();
        boxWidth = Math.min(width - 2 * MARGIN, MAX_WIDTH);
        contentWidth = boxWidth - 2 * PAD;
        int gridHeight = SwatchStrip.heightOf(16, GRID_COLUMNS, GRID_CELL, GRID_GAP);
        int pickerHeight = Math.max(gridHeight, 2 * ROW + 6);
        int presetHeight = PresetStrip.heightFor(contentWidth, PRESETS.length);
        // 竖直方向：标题 + 色板区 + 渐变标题 + 色标行 + 预设 + 格式行 + 预览 + 底栏
        boxHeight = TOP_INSET + pickerHeight + 6 + 12 + GradientBar.HEIGHT + 5 + presetHeight + 6 + ROW + 4
                + PREVIEW_HEIGHT + 8 + FOOTER_BUTTON_HEIGHT + 8;
        boxX = (width - boxWidth) / 2;
        boxY = Math.max(MARGIN / 2, (height - boxHeight) / 2);
        contentX = boxX + PAD;

        int y = boxY + TOP_INSET;
        layoutPicker(y, pickerHeight);
        y += pickerHeight + 6;
        headerY = y;
        y += 12;
        layoutGradient(y);
        y += GradientBar.HEIGHT + 5;
        presetStrip = addRenderableWidget(new PresetStrip(contentX, y, contentWidth, Component.translatable(KEY + "presets"),
                PRESETS, presetNames(), this::pickPreset));
        pickers.add(presetStrip);
        y += presetHeight + 6;
        formatY = y;
        layoutFormats(y);
        y += ROW + 4;
        previewY = y;
        footerY = boxY + boxHeight - 8 - FOOTER_BUTTON_HEIGHT;
        layoutFooter();
        updateButtons();
    }

    /**
     * 色板区：左边 16 色（8 × 2），右边原色 / 新色对比 + 颜色名、最近使用
     */
    private void layoutPicker(int top, int areaHeight) {
        int gridHeight = SwatchStrip.heightOf(16, GRID_COLUMNS, GRID_CELL, GRID_GAP);
        gridStrip = addRenderableWidget(new SwatchStrip(contentX, top + (areaHeight - gridHeight) / 2, 16, GRID_COLUMNS,
                GRID_CELL, GRID_GAP, title, i -> ColorMath.legacyRgb(LegacyText.VANILLA_COLORS[i]),
                i -> colorNameOf(ColorMath.legacyRgb(LegacyText.VANILLA_COLORS[i])),
                i -> setColor(ColorMath.legacyRgb(LegacyText.VANILLA_COLORS[i])))
                .marked(() -> indexOf(current())));
        pickers.add(gridStrip);

        rightX = contentX + SwatchStrip.widthOf(GRID_COLUMNS, GRID_CELL, GRID_GAP) + 10;
        int rightWidth = contentX + contentWidth - rightX;
        int gap = Math.max(2, areaHeight - 2 * ROW);

        // 原色 / 新色：说明放得下才画；颜色名画在两个格子右边
        compareY = top;
        int captionWidth = font.width(compareCaption) + 6;
        showCompareCaption = captionWidth + SwatchStrip.widthOf(2, COMPARE_CELL, 1) <= rightWidth;
        int compareX = rightX + (showCompareCaption ? captionWidth : 0);
        SwatchStrip compare = addRenderableWidget(new SwatchStrip(compareX, top + (ROW - COMPARE_CELL) / 2, 2, COMPARE_CELL,
                1, compareCaption, i -> i == 0 ? original : current(),
                i -> Component.translatable(KEY + (i == 0 ? "compare.old" : "compare.new"), colorNameOf(i == 0 ? original : current())),
                i -> {
                    if (i == 0) {
                        setColor(original);
                    }
                }));
        pickers.add(compare);
        compareRight = compareX + SwatchStrip.widthOf(2, COMPARE_CELL, 1);

        recentY = top + ROW + gap;
        int recentCaptionWidth = font.width(recentCaption) + 4;
        int available = rightWidth - recentCaptionWidth;
        int cell = RECENT_CELL;
        int recentGap = 2;
        while (cell > 5 && SwatchStrip.widthOf(RECENT_SIZE, cell, recentGap) > available) {
            cell--;
        }
        recentStrip = addRenderableWidget(new SwatchStrip(rightX + recentCaptionWidth, recentY + (ROW - cell) / 2,
                RECENT_SIZE, cell, recentGap, recentCaption, i -> i < recentCount ? RECENT[i] : -1,
                i -> i < recentCount ? Component.translatable(KEY + "recent.tooltip", colorNameOf(RECENT[i]))
                        : Component.translatable(KEY + "recent.empty"),
                i -> {
                    if (i < recentCount) {
                        gradientArea = false;
                        updateButtons();
                        setColor(RECENT[i]);
                    }
                }));
        pickers.add(recentStrip);
    }

    /**
     * 渐变条 + 增 / 删 / 反转
     */
    private void layoutGradient(int top) {
        int buttonsWidth = 3 * STOP_BUTTON_WIDTH + 2 * 3;
        gradientBar = addRenderableWidget(new GradientBar(contentX, top, contentWidth - buttonsWidth - 6,
                gradientCaption, () -> stops, () -> selected, this::selectStop)
                .hoverTip(Component.translatable(KEY + "stops.tooltip")));
        pickers.add(gradientBar);
        int x = contentX + contentWidth - buttonsWidth;
        int y = top + 1;
        addButton = addRenderableWidget(new FlatButton(x, y, STOP_BUTTON_WIDTH, STOP_BUTTON_HEIGHT, Component.raw("+"),
                this::addStop).tooltip(Component.translatable(KEY + "stop.add.tooltip")));
        removeButton = addRenderableWidget(new FlatButton(x + STOP_BUTTON_WIDTH + 3, y, STOP_BUTTON_WIDTH, STOP_BUTTON_HEIGHT,
                Component.raw("−"), this::removeStop)
                .tooltip(Component.translatable(KEY + "stop.remove.tooltip")));
        reverseButton = addRenderableWidget(new FlatButton(x + 2 * (STOP_BUTTON_WIDTH + 3), y, STOP_BUTTON_WIDTH, STOP_BUTTON_HEIGHT,
                Component.raw("⇄"), this::reverseStops)
                .tooltip(Component.translatable(KEY + "stop.reverse.tooltip")));
    }

    /**
     * 格式开关 B / I / U / S
     */
    private void layoutFormats(int top) {
        int x = contentX + font.width(formatCaption) + 4;
        for (int i = 0; i < FORMATS.length; i++) {
            int bit = LegacyComponents.formatBit(FORMATS[i]);
            Component tip = Component.translatable("gui.battlecorrection.editor.format." + FORMAT_KEYS[i])
                    .append("\n").append(Component.translatable(KEY + "format.tooltip"));
            addRenderableWidget(new ToggleChip(x, top, TOGGLE_WIDTH, ROW,
                    Component.raw(FORMAT_LETTERS[i]).withStyle(FORMATS[i]), (formats & bit) != 0, on -> {
                formats = on ? formats | bit : formats & ~bit;
                refreshPreview();
            }).tooltip(tip));
            x += TOGGLE_WIDTH + 2;
        }
        togglesRight = x - 2;
    }

    /**
     * 底栏：右边三个按钮，左边放得下就写一行按键说明
     */
    private void layoutFooter() {
        Component insertLabel = Component.translatable(KEY + "insert");
        Component applyLabel = Component.translatable(KEY + "apply");
        Component cancelLabel = Component.translatable(KEY + "cancel");
        int insertWidth = Math.max(FOOTER_BUTTON_MIN_WIDTH, font.width(insertLabel) + 12);
        int applyWidth = Math.max(FOOTER_BUTTON_MIN_WIDTH, font.width(applyLabel) + 12);
        int cancelWidth = Math.max(FOOTER_BUTTON_MIN_WIDTH, font.width(cancelLabel) + 12);
        // 从右往左算位置，按从左往右的顺序加控件（Tab 的顺序跟着加入顺序走）
        int cancelX = boxX + boxWidth - CORNER_CLEARANCE - cancelWidth;
        int applyX = cancelX - 4 - applyWidth;
        int insertX = applyX - 4 - insertWidth;
        insertButton = addRenderableWidget(new FlatButton(insertX, footerY, insertWidth, FOOTER_BUTTON_HEIGHT, insertLabel,
                this::insertColor).tooltip(Component.translatable(KEY + "insert.tooltip")));
        applyButton = addRenderableWidget(new FlatButton(applyX, footerY, applyWidth, FOOTER_BUTTON_HEIGHT, applyLabel,
                this::applyGradient).tooltip(Component.translatable(KEY + "apply.tooltip")));
        addRenderableWidget(new FlatButton(cancelX, footerY, cancelWidth, FOOTER_BUTTON_HEIGHT, cancelLabel, this::cancel));
        Component text = Component.translatable(KEY + "keys");
        footerText = font.width(text) <= insertX - 8 - (boxX + CORNER_CLEARANCE) ? text.getString() : null;
    }

    @Nonnull
    private static Component[] presetNames() {
        Component[] names = new Component[PRESET_KEYS.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = Component.translatable(KEY + "preset.tooltip", Component.translatable(KEY + "preset." + PRESET_KEYS[i]));
        }
        return names;
    }

    // 颜色状态

    /**
     * 当前颜色（选中的色标）
     *
     * @return RGB
     */
    private static int current() {
        return stops[selected];
    }

    /**
     * 原版颜色在 16 色里的下标
     *
     * @param rgb 颜色
     * @return 0~15；不是原版颜色时 -1
     */
    private static int indexOf(int rgb) {
        TextFormatting formatting = ColorMath.exactLegacy(rgb);
        return formatting == null ? -1 : formatting.getColorIndex();
    }

    /**
     * 颜色的名字（例如"金色 &6"）
     */
    @Nonnull
    private static Component colorNameOf(int rgb) {
        return Component.translatable(COLOR_KEY + ColorMath.nearestLegacy(rgb).getFriendlyName());
    }

    /**
     * 设成某个颜色（色板、最近使用、还原）
     *
     * @param rgb 颜色（换成最接近的原版颜色）
     */
    private void setColor(int rgb) {
        stops[selected] = ColorMath.quantize(rgb);
        refreshPreview();
    }

    // 色标

    private void selectStop(int index) {
        selected = Math.max(0, Math.min(stops.length - 1, index));
        original = current();
        markGradient();
    }

    private void addStop() {
        if (stops.length >= MAX_STOPS) {
            return;
        }
        int[] next = new int[stops.length + 1];
        System.arraycopy(stops, 0, next, 0, selected + 1);
        next[selected + 1] = stops[selected];
        System.arraycopy(stops, selected + 1, next, selected + 2, stops.length - selected - 1);
        stops = next;
        selected++;
        original = current();
        refreshPreview();
        markGradient();
    }

    private void removeStop() {
        if (stops.length <= MIN_STOPS) {
            return;
        }
        int[] next = new int[stops.length - 1];
        System.arraycopy(stops, 0, next, 0, selected);
        System.arraycopy(stops, selected + 1, next, selected, stops.length - selected - 1);
        stops = next;
        selected = Math.min(selected, stops.length - 1);
        original = current();
        refreshPreview();
        markGradient();
    }

    private void reverseStops() {
        int[] next = new int[stops.length];
        for (int i = 0; i < stops.length; i++) {
            next[i] = stops[stops.length - 1 - i];
        }
        stops = next;
        selected = stops.length - 1 - selected;
        refreshPreview();
        markGradient();
    }

    private void pickPreset(int index) {
        stops = PRESETS[index].clone();
        selected = 0;
        original = current();
        refreshPreview();
        markGradient();
    }

    /**
     * 动过渐变那一半：回车改成应用渐变
     */
    private void markGradient() {
        gradientArea = true;
        updateButtons();
    }

    /**
     * 增删按钮能不能按、哪个底栏按钮是回车的默认动作（画成主要样式）
     */
    private void updateButtons() {
        if (addButton != null) {
            addButton.active = stops.length < MAX_STOPS;
        }
        if (removeButton != null) {
            removeButton.active = stops.length > MIN_STOPS;
        }
        if (insertButton != null && applyButton != null) {
            insertButton.active = hasTarget;
            applyButton.active = hasTarget;
            insertButton.style(gradientArea ? FlatButton.Style.NORMAL : FlatButton.Style.PRIMARY);
            applyButton.style(gradientArea ? FlatButton.Style.PRIMARY : FlatButton.Style.NORMAL);
        }
    }

    /**
     * 色标或格式变了：重算预览文字（只在变化时算，渲染时直接画缓存的组件）
     * <p>
     * 预览按 §r 基础样式（白色、不斜体）画，颜色看得最清楚；每个字前面都有颜色码，基础样式本来也只影响得到选区外的字。
     */
    private void refreshPreview() {
        String colored = LegacyEdits.gradient(sample, 0, 0, stops, formats, LegacyComponents.RESET).text();
        preview = Component.literal(LegacyText.toSection(colored));
    }

    // 应用

    /**
     * 回车：执行高亮的那个按钮
     */
    private void enterAction() {
        if (gradientArea) {
            applyGradient();
        } else {
            insertColor();
        }
    }

    /**
     * 插入颜色：光标处插这个颜色的代码（加上打开的格式码）；有选区就把选区整体换成这个颜色
     */
    private void insertColor() {
        if (!hasTarget) {
            return;
        }
        int rgb = current();
        remember(rgb);
        String code = LegacyText.colorCode(rgb) + LegacyComponents.formatCodes(formats);
        int[] single = {rgb};
        finish(box -> {
            String value = box.getValue();
            boolean same = value.equals(sourceText);
            int from = same ? selStart : value.length();
            int to = same ? selEnd : value.length();
            if (from != to) {
                ColorToolbar.applyEdit(box, LegacyEdits.gradient(value, from, to, single, formats, start));
            } else {
                placeCursor(box, from);
                ColorToolbar.insertInto(box, code);
            }
        });
    }

    /**
     * 应用渐变：选区（没选中就是整个框）按当前色标做渐变
     */
    private void applyGradient() {
        if (!hasTarget) {
            return;
        }
        for (int i = stops.length - 1; i >= 0; i--) {
            remember(stops[i]);
        }
        int[] colors = stops.clone();
        finish(box -> {
            String value = box.getValue();
            if (value.isEmpty()) {
                ColorToolbar.refocus(box);
                return;
            }
            boolean same = value.equals(sourceText);
            ColorToolbar.applyEdit(box, LegacyEdits.gradient(value, same ? selStart : 0, same ? selEnd : 0, colors,
                    formats, start));
        });
    }

    /**
     * 收尾：存最近使用、把头尾颜色交还工具条、回到编辑器，再对（重建后的）目标框动手
     */
    private void finish(@Nonnull Consumer<EditBox> edit) {
        saveRecent();
        int head = stops[0];
        int tail = stops[stops.length - 1];
        seedFrom = head;
        seedTo = tail;
        // 先回写再回编辑器：编辑器重建时工具条按这两个值新建颜色框
        onApplied.accept(head, tail);
        minecraft.displayGuiScreen(parent);
        EditBox box = usable(target.get());
        if (box != null) {
            edit.accept(box);
        }
    }

    /**
     * 光标放到某处、不选中
     */
    private static void placeCursor(@Nonnull EditBox box, int position) {
        int at = Math.max(0, Math.min(position, box.getValue().length()));
        if (box instanceof FlatEditBox) {
            ((FlatEditBox) box).select(at, at);
        } else {
            box.setCursorPosition(at);
            box.setHighlightPos(at);
        }
    }

    private void cancel() {
        minecraft.displayGuiScreen(parent);
    }

    @Override
    public void onClose() {
        cancel();
    }

    // 最近使用

    /**
     * 记一个用过的颜色：挪到最前面，去重，最多 8 个
     */
    private static void remember(int rgb) {
        rgb = ColorMath.quantize(rgb);
        int found = -1;
        for (int i = 0; i < recentCount; i++) {
            if (RECENT[i] == rgb) {
                found = i;
                break;
            }
        }
        int shiftEnd = found >= 0 ? found : Math.min(recentCount, RECENT_SIZE - 1);
        System.arraycopy(RECENT, 0, RECENT, 1, shiftEnd);
        RECENT[0] = rgb;
        if (found < 0 && recentCount < RECENT_SIZE) {
            recentCount++;
        }
    }

    @Nullable
    private static Path recentFile() {
        try {
            File dir = Loader.instance().getConfigDir();
            return dir == null ? null : dir.toPath().resolve(RECENT_FILE);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static void loadRecent() {
        if (recentLoaded) {
            return;
        }
        recentLoaded = true;
        Path file = recentFile();
        if (file != null) {
            readRecent(file);
        }
    }

    private static void saveRecent() {
        Path file = recentFile();
        if (file != null) {
            writeRecent(file);
        }
    }

    /**
     * 读一条记录：{@code &c} / 分节号写法 / 单个代码字符；{@code #RRGGBB}（旧文件、别的版本写的）换成最接近的原版颜色
     *
     * @param text 记录
     * @return RGB；认不出时返回 -1
     */
    private static int parseEntry(@Nonnull String text) {
        String value = text.trim();
        char code = value.length() == 1 ? value.charAt(0)
                : value.length() == 2 && (value.charAt(0) == LegacyText.AMPERSAND || value.charAt(0) == LegacyText.SECTION)
                ? value.charAt(1) : 0;
        if (code != 0) {
            TextFormatting formatting = LegacyText.codeOf(code);
            return formatting != null && formatting.isColor() ? ColorMath.legacyRgb(formatting) : -1;
        }
        int rgb = ColorMath.parseHex(value);
        return rgb < 0 ? -1 : ColorMath.quantize(rgb);
    }

    /**
     * 从文件读最近使用；文件不在、格式不对都当作没有
     *
     * @param file 文件
     */
    static void readRecent(@Nonnull Path file) {
        recentCount = 0;
        try {
            if (!Files.isRegularFile(file)) {
                return;
            }
            JsonElement root = new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            if (!root.isJsonObject() || !root.getAsJsonObject().has("recent")
                    || !root.getAsJsonObject().get("recent").isJsonArray()) {
                return;
            }
            for (JsonElement element : root.getAsJsonObject().getAsJsonArray("recent")) {
                if (recentCount >= RECENT_SIZE) {
                    break;
                }
                int rgb = element.isJsonPrimitive() ? parseEntry(element.getAsString()) : -1;
                if (rgb < 0) {
                    continue;
                }
                boolean duplicate = false;
                for (int i = 0; i < recentCount; i++) {
                    duplicate |= RECENT[i] == rgb;
                }
                if (!duplicate) {
                    RECENT[recentCount++] = rgb;
                }
            }
        } catch (IOException | RuntimeException e) {
            // 读不了就当没有：只是个方便，不值得打扰人
            recentCount = 0;
        }
    }

    /**
     * 把最近使用写进文件：先写临时文件再改名，写到一半退出游戏也不会留下半个文件；写不了就算了
     *
     * @param file 文件
     */
    static void writeRecent(@Nonnull Path file) {
        JsonArray array = new JsonArray();
        for (int i = 0; i < recentCount; i++) {
            array.add(LegacyText.colorCode(RECENT[i]));
        }
        JsonObject root = new JsonObject();
        root.add("recent", array);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Path dir = file.getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            Files.write(temp, root.toString().getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException | RuntimeException ignored) {
                // 临时文件删不掉也无妨，下次写会覆盖
            }
        }
    }

    // 输入

    @Override
    public void setFocused(@Nullable GuiEventListener listener) {
        super.setFocused(listener);
        // 焦点（Tab 或点击）落到渐变那一半的控件上：回车改成应用渐变；落到"最近使用"上：改回插入颜色
        if (listener != null && (listener == gradientBar || listener == presetStrip || listener == addButton
                || listener == removeButton || listener == reverseButton)) {
            if (!gradientArea) {
                markGradient();
            }
        } else if (listener != null && listener == recentStrip && gradientArea) {
            gradientArea = false;
            updateButtons();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        GuiEventListener before = getFocused();
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        // 用鼠标点的按钮不留焦点：焦点还给原来的控件，回车不会变成"再按一次那个按钮"
        if (minecraft.currentScreen == this) {
            GuiEventListener now = getFocused();
            if (now != before && (now instanceof FlatButton || now instanceof ToggleChip)) {
                super.setFocused(before);
            }
        }
        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // 界面只通知鼠标底下的控件，拖出控件后松开的那个收不到，这里统一结束拖动
        for (PickerWidget picker : pickers) {
            picker.endDrag();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        GuiEventListener focused = getFocused();
        if (keyCode == Keys.KEY_ENTER || keyCode == Keys.KEY_KP_ENTER) {
            // Tab 停在某个按钮上：按那个按钮；开关自己处理回车；其余执行默认动作
            if (focused instanceof FlatButton) {
                FlatButton focusedButton = (FlatButton) focused;
                if (focusedButton.active && focusedButton.visible) {
                    focusedButton.onClick(0, 0);
                }
                return true;
            }
            if (focused != null && focused.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            enterAction();
            return true;
        }
        if (keyCode == Keys.KEY_SPACE && focused instanceof FlatButton) {
            FlatButton focusedButton = (FlatButton) focused;
            if (focusedButton.active && focusedButton.visible) {
                focusedButton.onClick(0, 0);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // 绘制

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        renderPanel(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    /**
     * 面板和控件以外的文字、分隔线、预览
     */
    private void renderPanel(@Nonnull GuiGraphics g) {
        EditorTheme.panel(g, boxX, boxY, boxWidth, boxHeight);
        g.drawString(font, title, boxX + CORNER_CLEARANCE, boxY + 9, EditorTheme.ACCENT_HI, false);
        EditorTheme.headingLine(g, boxX + PAD, boxY + 20, boxWidth - 2 * PAD);

        // 原色 / 新色说明、当前颜色的名字（放得下才画）、最近使用说明
        if (showCompareCaption) {
            g.drawString(font, compareCaption, rightX, compareY + 2, EditorTheme.TEXT_DIM, false);
        }
        int rgb = current();
        if (colorNameFor != rgb || colorName == null) {
            colorNameFor = rgb;
            colorName = colorNameOf(rgb);
        }
        int nameX = compareRight + 6;
        if (nameX + font.width(colorName) <= contentX + contentWidth) {
            g.drawString(font, colorName, nameX, compareY + 2, EditorTheme.TEXT, false);
        }
        g.drawString(font, recentCaption, rightX, recentY + 2, EditorTheme.TEXT_DIM, false);

        // 渐变小标题 + 色标数 + 分隔线
        g.drawString(font, gradientCaption, contentX, headerY, EditorTheme.TEXT_MUTED, false);
        if (stopCountFor != stops.length || stopCount == null) {
            stopCountFor = stops.length;
            stopCount = Component.translatable(KEY + "stops", stops.length, MAX_STOPS);
        }
        int countWidth = font.width(stopCount);
        int lineX = contentX + font.width(gradientCaption) + 6;
        int lineRight = contentX + contentWidth - countWidth - 6;
        g.drawString(font, stopCount, contentX + contentWidth - countWidth, headerY, EditorTheme.TEXT_DIM, false);
        if (lineRight > lineX) {
            EditorTheme.divider(g, lineX, headerY + 4, lineRight - lineX);
        }

        // 格式行：左边说明，右边写预览文字从哪来（没有目标框时是提示）
        g.drawString(font, formatCaption, contentX, formatY + 2, EditorTheme.TEXT_MUTED, false);
        int sourceWidth = font.width(sampleSource);
        if (contentX + contentWidth - sourceWidth >= togglesRight + 8) {
            g.drawString(font, sampleSource, contentX + contentWidth - sourceWidth, formatY + 2,
                    hasTarget ? EditorTheme.TEXT_DIM : EditorTheme.WARNING, false);
        }

        // 预览：放得下就居中，放不下靠左裁掉
        EditorTheme.well(g, contentX, previewY, contentWidth, PREVIEW_HEIGHT, false);
        int textWidth = font.width(preview);
        int inner = contentWidth - 8;
        int textX = textWidth <= inner ? contentX + (contentWidth - textWidth) / 2 : contentX + 4;
        g.enableScissor(contentX + 2, previewY + 1, contentX + contentWidth - 2, previewY + PREVIEW_HEIGHT - 1);
        g.drawString(font, preview, textX, previewY + (PREVIEW_HEIGHT - 8) / 2, 0xFFFFFFFF, false);
        g.disableScissor();

        if (footerText != null) {
            g.drawString(font, footerText, boxX + CORNER_CLEARANCE, footerY + (FOOTER_BUTTON_HEIGHT - 8) / 2, EditorTheme.TEXT_DIM, false);
        }
    }
}
