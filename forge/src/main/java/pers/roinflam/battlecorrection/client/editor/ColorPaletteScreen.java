package pers.roinflam.battlecorrection.client.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.battlecorrection.client.editor.widget.ChannelSlider;
import pers.roinflam.battlecorrection.client.editor.widget.ColorSquare;
import pers.roinflam.battlecorrection.client.editor.widget.ColorToolbar;
import pers.roinflam.battlecorrection.client.editor.widget.FlatButton;
import pers.roinflam.battlecorrection.client.editor.widget.FlatEditBox;
import pers.roinflam.battlecorrection.client.editor.widget.GradientBar;
import pers.roinflam.battlecorrection.client.editor.widget.HexColorField;
import pers.roinflam.battlecorrection.client.editor.widget.HueBar;
import pers.roinflam.battlecorrection.client.editor.widget.NumberField;
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
 * 调色盘：在游戏里拖着取颜色、做多色标渐变，然后写进名字 / Lore 的输入框
 * <p>
 * 从颜色工具条的"调色盘"按钮打开，编辑器还开在后面。上半是取色区：饱和度 × 明度方块、色相条、R / G / B 滑块和数值框、
 * 十六进制框（能认粘贴的各种颜色写法）、原色 / 新色对比、最近用过的 16 个颜色；下半是渐变：2~8 个等距色标、
 * 一条真实渐变、一排预设、B / I / U / S 格式开关和一行把渐变真的画上去的预览文字。
 * 取色区改的永远是选中的那个色标，所以"挑一个颜色插进去"和"调渐变里的某一段"用的是同一套控件。
 * <p>
 * 底部三个按钮：插入颜色（光标处插 {@code &#RRGGBB}；有选区就把选区整体换成这个颜色，原有颜色码去掉、格式码保留）、
 * 应用渐变（选区或整个框）、取消。回车执行高亮的那个：最近碰的是渐变那一半（色标、预设、增删反转）就是应用渐变，否则是插入颜色；
 * 用 Tab 停在某个按钮上时回车按那个按钮。Esc / 取消原样回到编辑器，什么都不改。
 * <p>
 * 打开时把目标框的文字和选区记下来：回到编辑器时编辑器会整个重建（原版回到上一个界面就是重新 init），输入框是新的，
 * 选区早没了，只能按记下的位置改。改完把焦点还给目标框，并选中改写过的那一段，和工具条上的渐变按钮一样。
 * <p>
 * 色标、选中的色标、格式开关放在静态字段里：缩放窗口、关掉再开都还在。工具条两个十六进制框的颜色当作渐变头尾的初值传进来，
 * 和上次留下的头尾不一样（在工具条上改过）就按它们重新开始；应用之后把头尾颜色写回那两个框。
 * 最近使用的颜色存在 config 目录的一个小 JSON 里，读写失败就当没有。
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

    // ═══════════════════════════════════════════════════════════════
    // 布局常量
    // ═══════════════════════════════════════════════════════════════

    private static final int MAX_WIDTH = 400;
    private static final int MARGIN = 8;
    private static final int PAD = 10;
    /**
     * 面板四角的准线角饰占的宽度，标题和底栏从它里面让开
     */
    private static final int CORNER_CLEARANCE = 22;
    private static final int TOP_INSET = 26;
    private static final int ROW = 12;
    private static final int HUE_WIDTH = 12;
    private static final int NUMBER_WIDTH = 28;
    private static final int HEX_WIDTH = 72;
    private static final int COMPARE_CELL = 12;
    private static final int RECENT_CELL = 8;
    private static final int TOGGLE_WIDTH = 14;
    private static final int STOP_BUTTON_WIDTH = 16;
    private static final int STOP_BUTTON_HEIGHT = 14;
    private static final int PREVIEW_HEIGHT = 14;
    private static final int FOOTER_BUTTON_HEIGHT = 16;
    private static final int FOOTER_BUTTON_MIN_WIDTH = 48;
    /**
     * 右栏五行（三个滑块、十六进制、最近使用）本身的高度之和，方块边长比它大的部分平均分给行间距
     */
    private static final int RIGHT_COLUMN_ROWS = 4 * ROW + 10;
    private static final int MIN_SQUARE = RIGHT_COLUMN_ROWS + 4;
    private static final int MAX_SQUARE = 88;

    // ═══════════════════════════════════════════════════════════════
    // 数量
    // ═══════════════════════════════════════════════════════════════

    private static final int MIN_STOPS = 2;
    private static final int MAX_STOPS = 8;
    private static final int RECENT_SIZE = 16;
    /**
     * 预览文字最多几个字
     */
    private static final int SAMPLE_CHARS = 24;

    private static final String KEY = "gui.battlecorrection.editor.palette.";
    private static final String RECENT_FILE = "battlecorrection-editor-colors.json";

    private static final String[] CHANNEL_LABELS = {"R", "G", "B"};
    private static final int[] CHANNEL_COLORS = {0xFFFF7A7A, 0xFF7AF59A, 0xFF7AB4FF};
    private static final String[] CHANNEL_KEYS = {"channel.r", "channel.g", "channel.b"};

    /**
     * 格式开关：显示文字、格式、提示翻译键（沿用工具条格式按钮的提示）
     */
    private static final ChatFormatting[] FORMATS = {
            ChatFormatting.BOLD, ChatFormatting.ITALIC, ChatFormatting.UNDERLINE, ChatFormatting.STRIKETHROUGH
    };
    private static final String[] FORMAT_LETTERS = {"B", "I", "U", "S"};
    private static final String[] FORMAT_KEYS = {"bold", "italic", "underline", "strikethrough"};

    /**
     * 预览文字的基础样式：白色、不斜体（和 Lore 页关掉原版样式时一样，颜色看得最清楚）
     */
    private static final Style PREVIEW_BASE = Style.EMPTY.withColor(ChatFormatting.WHITE).withItalic(false);

    /**
     * 预设渐变（名字的翻译键后缀 + 色标）
     */
    private static final String[] PRESET_KEYS = {
            "rainbow", "fire", "ocean", "sunset", "forest", "frost", "candy", "gold", "neon", "grayscale"
    };
    private static final int[][] PRESETS = {
            {0xFF4D4D, 0xFFA23D, 0xFFE94D, 0x5CF27A, 0x4DC8FF, 0x6F6BFF, 0xD66BFF},
            {0xFFF0A0, 0xFFC23D, 0xFF7A1F, 0xE8381C},
            {0xA8F5FF, 0x4CCBF0, 0x2A86D9, 0x2448B0},
            {0xFFD66B, 0xFF8A65, 0xF2557F, 0x9B4DCA},
            {0xD8F78C, 0x86D95A, 0x34A853, 0x1E7A44},
            {0xFFFFFF, 0xD2F3FF, 0x95D7FF, 0x5DA8FF},
            {0xFF7EB9, 0xFFC2E2, 0xC3CBFF, 0x7EDDFF},
            {0xFFF3B5, 0xFFCC33, 0xD9A11A, 0xFFE38A},
            {0xFF2BD6, 0x9C4DFF, 0x2BE8FF, 0x3DFF8F},
            {0xFFFFFF, 0x9A9A9A, 0x4A4A4A}
    };

    // ═══════════════════════════════════════════════════════════════
    // 跨次打开保留的状态
    // ═══════════════════════════════════════════════════════════════

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
    /**
     * 选中色标的 HSV。单独存着：拖到灰色时色相不确定、拖到黑色时饱和度不确定，从 RGB 反算会跳回 0
     */
    private static double hue;
    private static double saturation;
    private static double brightness;

    private static final int[] RECENT = new int[RECENT_SIZE];
    private static int recentCount;
    private static boolean recentLoaded;

    // ═══════════════════════════════════════════════════════════════
    // 这一次打开
    // ═══════════════════════════════════════════════════════════════

    private final Screen parent;
    private final Supplier<EditBox> target;
    private final ColorsApplied onApplied;
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
    private boolean syncing;
    private final double[] hsvScratch = new double[3];

    // 布局
    private int boxX;
    private int boxY;
    private int boxWidth;
    private int boxHeight;
    private int contentX;
    private int contentWidth;
    private int rightX;
    private final int[] channelY = new int[3];
    private int compareX;
    private int compareY;
    private int recentY;
    private int headerY;
    private int formatY;
    private int togglesRight;
    private int previewY;
    private int footerY;

    // 控件
    private final List<PickerWidget> pickers = new ArrayList<>();
    private final NumberField[] numbers = new NumberField[3];
    @Nullable
    private HexColorField hexField;
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

    // 每帧画的文字（init 里算好）
    private final Component compareCaption = Component.translatable(KEY + "compare");
    private final Component recentCaption = Component.translatable(KEY + "recent");
    private final Component gradientCaption = Component.translatable(KEY + "gradient");
    private final Component formatCaption = Component.translatable(KEY + "format");
    @Nullable
    private Component stopCount;
    private int stopCountFor = -1;
    @Nullable
    private String footerText;
    private boolean showCompareCaption;

    /**
     * @param parent    关掉后回到的界面（编辑器）
     * @param target    目标输入框（回到编辑器后再取一次：编辑器重建后是新的框）
     * @param from      工具条左边十六进制框的颜色（渐变第一个色标的初值）
     * @param to        工具条右边十六进制框的颜色（最后一个色标的初值）
     * @param onApplied 应用之后回写头尾颜色
     */
    public ColorPaletteScreen(@Nonnull Screen parent, @Nonnull Supplier<EditBox> target, int from, int to,
                              @Nonnull ColorsApplied onApplied) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.target = target;
        this.onApplied = onApplied;

        EditBox box = usable(target.get());
        hasTarget = box != null;
        sourceText = box == null ? "" : box.getValue();
        if (box instanceof FlatEditBox flat) {
            selStart = flat.selectionStart();
            selEnd = flat.selectionEnd();
        } else {
            selStart = box == null ? 0 : box.getCursorPosition();
            selEnd = selStart;
        }

        from &= 0xFFFFFF;
        to &= 0xFFFFFF;
        if (from != seedFrom || to != seedTo) {
            stops = new int[]{from, to};
            selected = 0;
            gradientArea = false;
            seedFrom = from;
            seedTo = to;
            hue = 0;
            saturation = 0;
        }
        selected = Math.max(0, Math.min(stops.length - 1, selected));
        deriveHsv(stops[selected]);
        original = stops[selected];
        loadRecent();

        String picked = hasTarget ? LegacyEdits.sample(sourceText, selStart, selEnd, SAMPLE_CHARS) : "";
        if (LegacyText.plain(picked).isBlank()) {
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

    // ═══════════════════════════════════════════════════════════════
    // 布局
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        pickers.clear();
        boxWidth = Math.min(width - 2 * MARGIN, MAX_WIDTH);
        contentWidth = boxWidth - 2 * PAD;
        int square = Math.max(MIN_SQUARE, Math.min(MAX_SQUARE, contentWidth / 4));
        int presetHeight = PresetStrip.heightFor(contentWidth, PRESETS.length);
        // 竖直方向：标题 + 取色区 + 渐变标题 + 色标行 + 预设 + 格式行 + 预览 + 底栏
        int fixed = TOP_INSET + 6 + 12 + GradientBar.HEIGHT + 5 + presetHeight + 6 + ROW + 4 + PREVIEW_HEIGHT
                + 8 + FOOTER_BUTTON_HEIGHT + 8;
        int room = height - 2 * MARGIN - fixed;
        if (square > room) {
            // 窗口很矮时先缩方块（右栏五行最少也要这么高，再矮就只能超出去了）
            square = Math.max(MIN_SQUARE, room);
        }
        boxHeight = fixed + square;
        boxX = (width - boxWidth) / 2;
        boxY = (height - boxHeight) / 2;
        contentX = boxX + PAD;

        int y = boxY + TOP_INSET;
        layoutPicker(y, square);
        y += square + 6;
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
        syncFields(null);
        updateButtons();
    }

    /**
     * 取色区：方块 + 色相条 + 右栏（三个滑块、十六进制和对比、最近使用）
     */
    private void layoutPicker(int top, int square) {
        ColorSquare squareWidget = addRenderableWidget(new ColorSquare(contentX, top, square,
                Component.translatable(KEY + "square"), new ColorSquare.Model() {
            @Override
            public double hue() {
                return hue;
            }

            @Override
            public double saturation() {
                return saturation;
            }

            @Override
            public double value() {
                return brightness;
            }

            @Override
            public void setSaturationValue(double s, double v) {
                saturation = s;
                brightness = v;
                applyHsv();
            }
        }));
        pickers.add(squareWidget);
        HueBar hueBar = addRenderableWidget(new HueBar(contentX + square + 4, top, HUE_WIDTH, square,
                Component.translatable(KEY + "hue"), () -> hue, h -> {
            hue = h;
            applyHsv();
        }));
        pickers.add(hueBar);

        rightX = contentX + square + 4 + HUE_WIDTH + 8;
        int rightWidth = contentX + contentWidth - rightX;
        int gap = Math.max(3, (square - RIGHT_COLUMN_ROWS) / 4);
        int y = top;
        for (int i = 0; i < 3; i++) {
            int channel = i;
            channelY[i] = y;
            Component name = Component.translatable(KEY + CHANNEL_KEYS[i]);
            ChannelSlider slider = addRenderableWidget(new ChannelSlider(rightX + 10, y, rightWidth - 10 - NUMBER_WIDTH - 4, ROW,
                    channel, name, ColorPaletteScreen::current, value -> setChannel(channel, value, null)));
            pickers.add(slider);
            NumberField number = new NumberField(font, rightX + rightWidth - NUMBER_WIDTH - 1, y, NUMBER_WIDTH, ROW,
                    Component.literal(CHANNEL_LABELS[i]), 0, 255, ColorMath.channel(current(), i), value -> setChannel(channel, value, numbers[channel]));
            numbers[i] = addRenderableWidget(number);
            y += ROW + gap;
        }

        hexField = new HexColorField(font, rightX, y, HEX_WIDTH, ROW, ColorMath.hex(current()), value -> {
            if (!syncing && value.length() == 6) {
                setColor(Integer.parseInt(value, 16), hexField);
            }
        });
        hexField.setTooltip(Tooltip.create(Component.translatable(KEY + "hex.tooltip")));
        hexField.onSubmit(this::enterAction);
        addRenderableWidget(hexField);
        compareX = rightX + rightWidth - SwatchStrip.widthOf(2, COMPARE_CELL, 1);
        compareY = y;
        SwatchStrip compare = addRenderableWidget(new SwatchStrip(compareX, y + (ROW - COMPARE_CELL) / 2, 2, COMPARE_CELL,
                1, compareCaption, i -> i == 0 ? original : current(),
                i -> Component.translatable(KEY + (i == 0 ? "compare.old" : "compare.new"), ColorMath.hex(i == 0 ? original : current())),
                i -> {
                    if (i == 0) {
                        setColor(original, null);
                    }
                }));
        pickers.add(compare);
        showCompareCaption = compareX - 6 - font.width(compareCaption) >= rightX + HEX_WIDTH + 6;
        y += ROW + gap;

        recentY = y;
        int captionWidth = font.width(recentCaption) + 4;
        int available = rightWidth - captionWidth;
        int cell = RECENT_CELL;
        int recentGap = Math.max(1, Math.min(2, (available - RECENT_SIZE * cell) / (RECENT_SIZE - 1)));
        while (cell > 5 && SwatchStrip.widthOf(RECENT_SIZE, cell, recentGap) > available) {
            cell--;
        }
        recentStrip = addRenderableWidget(new SwatchStrip(rightX + captionWidth, y + 1, RECENT_SIZE, cell, recentGap,
                recentCaption, i -> i < recentCount ? RECENT[i] : -1,
                i -> i < recentCount ? Component.translatable(KEY + "recent.tooltip", ColorMath.hex(RECENT[i]))
                        : Component.translatable(KEY + "recent.empty"),
                i -> {
                    if (i < recentCount) {
                        gradientArea = false;
                        updateButtons();
                        setColor(RECENT[i], null);
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
        addButton = addRenderableWidget(new FlatButton(x, y, STOP_BUTTON_WIDTH, STOP_BUTTON_HEIGHT, Component.literal("+"),
                this::addStop).tooltip(Component.translatable(KEY + "stop.add.tooltip")));
        removeButton = addRenderableWidget(new FlatButton(x + STOP_BUTTON_WIDTH + 3, y, STOP_BUTTON_WIDTH, STOP_BUTTON_HEIGHT,
                Component.literal("−"), this::removeStop).tooltip(Component.translatable(KEY + "stop.remove.tooltip")));
        reverseButton = addRenderableWidget(new FlatButton(x + 2 * (STOP_BUTTON_WIDTH + 3), y, STOP_BUTTON_WIDTH, STOP_BUTTON_HEIGHT,
                Component.literal("⇄"), this::reverseStops).tooltip(Component.translatable(KEY + "stop.reverse.tooltip")));
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
                    Component.literal(FORMAT_LETTERS[i]).withStyle(FORMATS[i]), (formats & bit) != 0, on -> {
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

    // ═══════════════════════════════════════════════════════════════
    // 颜色状态
    // ═══════════════════════════════════════════════════════════════

    /**
     * 当前颜色（选中的色标）
     *
     * @return RGB
     */
    private static int current() {
        return stops[selected];
    }

    /**
     * 从 RGB 算出 HSV：灰色保留原来的色相，黑色保留原来的饱和度（不然拖到角落再拖回来色相就丢了）
     */
    private void deriveHsv(int rgb) {
        ColorMath.rgbToHsv(rgb, hsvScratch);
        if (ColorMath.hasHue(rgb)) {
            hue = hsvScratch[0];
        }
        if (hsvScratch[2] > 0) {
            saturation = hsvScratch[1];
        }
        brightness = hsvScratch[2];
    }

    /**
     * 方块 / 色相条改了 HSV：按 HSV 算颜色，HSV 本身照用户拖的值留着（不从 RGB 反算，免得色相在灰色上跳）
     */
    private void applyHsv() {
        stops[selected] = ColorMath.hsvToRgb(hue, saturation, brightness);
        syncFields(null);
        refreshPreview();
    }

    /**
     * 改一个通道
     */
    private void setChannel(int channel, int value, @Nullable Object source) {
        if (syncing) {
            return;
        }
        setColor(ColorMath.withChannel(current(), channel, value), source);
    }

    /**
     * 设成某个颜色（滑块、数值框、十六进制框、色板、还原）
     *
     * @param rgb    颜色
     * @param source 是哪个输入框改的（同步时跳过它，免得打断正在输入的光标）；null = 都同步
     */
    private void setColor(int rgb, @Nullable Object source) {
        rgb &= 0xFFFFFF;
        stops[selected] = rgb;
        deriveHsv(rgb);
        syncFields(source);
        refreshPreview();
    }

    /**
     * 把当前颜色同步到数值框和十六进制框（滑块、方块每帧自己读，不用同步）
     */
    private void syncFields(@Nullable Object source) {
        syncing = true;
        try {
            int rgb = current();
            for (int i = 0; i < 3; i++) {
                if (numbers[i] != null && numbers[i] != source) {
                    numbers[i].show(ColorMath.channel(rgb, i));
                }
            }
            if (hexField != null && hexField != source && hexField.rgb() != rgb) {
                hexField.setRgb(rgb);
            }
        } finally {
            syncing = false;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 色标
    // ═══════════════════════════════════════════════════════════════

    private void selectStop(int index) {
        selected = Math.max(0, Math.min(stops.length - 1, index));
        original = current();
        deriveHsv(current());
        syncFields(null);
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
        deriveHsv(current());
        syncFields(null);
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
        deriveHsv(current());
        syncFields(null);
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
     */
    private void refreshPreview() {
        String colored = LegacyEdits.gradient(sample, 0, 0, stops, formats).text();
        preview = LegacyComponents.toComponent(colored, PREVIEW_BASE, Style.EMPTY);
    }

    // ═══════════════════════════════════════════════════════════════
    // 应用
    // ═══════════════════════════════════════════════════════════════

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
     * 插入颜色：光标处插 {@code &#RRGGBB}（加上打开的格式码）；有选区就把选区整体换成这个颜色
     */
    private void insertColor() {
        if (!hasTarget) {
            return;
        }
        int rgb = current();
        remember(rgb);
        String code = String.valueOf(LegacyText.AMPERSAND) + LegacyText.HEX_MARK + ColorMath.hex(rgb)
                + LegacyComponents.formatCodes(formats);
        int[] single = {rgb};
        finish(box -> {
            String value = box.getValue();
            boolean same = value.equals(sourceText);
            int start = same ? selStart : value.length();
            int end = same ? selEnd : value.length();
            if (start != end) {
                ColorToolbar.applyEdit(box, LegacyEdits.gradient(value, start, end, single, formats));
            } else {
                placeCursor(box, start);
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
            ColorToolbar.applyEdit(box, LegacyEdits.gradient(value, same ? selStart : 0, same ? selEnd : 0, colors, formats));
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
        // 先回写再回编辑器：编辑器重建时工具条按这两个值新建十六进制框
        onApplied.accept(head, tail);
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
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
        if (box instanceof FlatEditBox flat) {
            flat.select(at, at);
        } else {
            box.setCursorPosition(at);
            box.setHighlightPos(at);
        }
    }

    private void cancel() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        cancel();
    }

    // ═══════════════════════════════════════════════════════════════
    // 最近使用
    // ═══════════════════════════════════════════════════════════════

    /**
     * 记一个用过的颜色：挪到最前面，去重，最多 16 个
     */
    private static void remember(int rgb) {
        rgb &= 0xFFFFFF;
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
            Path dir = FMLPaths.CONFIGDIR.get();
            return dir == null ? null : dir.resolve(RECENT_FILE);
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
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!root.isJsonObject() || !root.getAsJsonObject().has("recent")
                    || !root.getAsJsonObject().get("recent").isJsonArray()) {
                return;
            }
            for (JsonElement element : root.getAsJsonObject().getAsJsonArray("recent")) {
                if (recentCount >= RECENT_SIZE) {
                    break;
                }
                int rgb = element.isJsonPrimitive() ? ColorMath.parseHex(element.getAsString()) : -1;
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
            array.add("#" + ColorMath.hex(RECENT[i]));
        }
        JsonObject root = new JsonObject();
        root.add("recent", array);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Path dir = file.getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            Files.writeString(temp, root.toString(), StandardCharsets.UTF_8);
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

    // ═══════════════════════════════════════════════════════════════
    // 输入
    // ═══════════════════════════════════════════════════════════════

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
        // 用鼠标点的按钮不留焦点：焦点还给原来的输入框 / 控件，回车不会变成"再按一次那个按钮"
        if (minecraft != null && minecraft.screen == this) {
            GuiEventListener now = getFocused();
            if (now != before && (now instanceof FlatButton || now instanceof ToggleChip)) {
                super.setFocused(before);
            }
        }
        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // 原版只通知鼠标底下的控件，拖出控件后松开的那个收不到，这里统一结束拖动
        for (PickerWidget picker : pickers) {
            picker.endDrag();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        GuiEventListener focused = getFocused();
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            // Tab 停在某个按钮上：按那个按钮；输入框、开关自己处理回车（十六进制框回车 = 默认动作）；其余执行默认动作
            if (focused instanceof FlatButton focusedButton) {
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
        if (keyCode == GLFW.GLFW_KEY_SPACE && focused instanceof FlatButton focusedButton) {
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

    // ═══════════════════════════════════════════════════════════════
    // 绘制
    // ═══════════════════════════════════════════════════════════════

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

        for (int i = 0; i < 3; i++) {
            g.drawString(font, CHANNEL_LABELS[i], rightX + 1, channelY[i] + 2, CHANNEL_COLORS[i], false);
        }
        if (showCompareCaption) {
            g.drawString(font, compareCaption, compareX - 6 - font.width(compareCaption), compareY + 2, EditorTheme.TEXT_DIM, false);
        }
        g.drawString(font, recentCaption, rightX, recentY + 1, EditorTheme.TEXT_DIM, false);

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
