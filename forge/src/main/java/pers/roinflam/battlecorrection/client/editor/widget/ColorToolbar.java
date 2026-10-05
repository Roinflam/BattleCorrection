package pers.roinflam.battlecorrection.client.editor.widget;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pers.roinflam.battlecorrection.client.editor.ColorPaletteScreen;
import pers.roinflam.battlecorrection.editor.ColorMath;
import pers.roinflam.battlecorrection.editor.LegacyEdits;
import pers.roinflam.battlecorrection.editor.LegacyText;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 颜色/格式代码工具条：16 个色块 + 粗体/斜体/下划线/删除线/乱码/重置 + 清除代码 + 十六进制颜色和渐变
 * <p>
 * 只列原版的 16 种颜色：有些模组会往 ChatFormatting 枚举里塞自己的颜色（例如 goety_nether），
 * 那些没有对应的 § 码，插进去也没用，所以不从 values() 取。
 * 点一下就往目标输入框的光标处插入对应的 & 码（例如 {@code &a}）；Shift + 点色块 = 把这个颜色填进左边的十六进制框（拿来当渐变起点）。
 * 目标由 Supplier 提供，Lore 页有很多行，返回当前（或最近）有焦点的那一行；没有目标时点击无事发生。
 * 按钮都是"不抢焦点"的，插完代码输入框还能接着打字；十六进制框要打字，会拿走焦点，所以插入 / 渐变之后把焦点还给目标框。
 * <p>
 * 十六进制那一组：[色块 RRGGBB] [插入] [色块 RRGGBB] [渐变]。插入用左边的颜色；渐变把选中的文字（没选中就是整个框）
 * 从左边的颜色过渡到右边的颜色，逐字插 {@code &#RRGGBB}，粗体这类格式码保留（见 {@link LegacyEdits#gradient}）。
 * 两个颜色记在静态字段里，同一次游戏里换页、重开编辑器都还在。
 * <p>
 * 最后是"调色盘"按钮（{@link ColorPaletteScreen}）：拖着取色、多色标渐变、预设。两个十六进制框的颜色当作渐变头尾的初值带过去，
 * 在调色盘里应用之后把头尾颜色写回这两个静态字段，回到编辑器重建工具条时框里就是新颜色。
 * 那一行剩的地方放不下带文字的按钮时只画图标，免得为了它多占一行。
 * <p>
 * 宽度不够时自动换行，{@link #build} 返回实际占用的高度，页面据此往下排版。
 */
public final class ColorToolbar {

    private static final int SWATCH = 10;
    private static final int SWATCH_GAP = 2;
    private static final int FORMAT_WIDTH = 22;
    private static final int FORMAT_HEIGHT = 12;
    private static final int FORMAT_GAP = 3;
    private static final int ROW_GAP = 4;
    private static final int HEX_WIDTH = 60;
    private static final int GROUP_GAP = 8;

    /**
     * 格式按钮：显示文字 + 对应的格式码 + 提示翻译键
     */
    private static final Object[][] FORMATS = {
            {Component.literal("B").withStyle(ChatFormatting.BOLD), ChatFormatting.BOLD, "bold"},
            {Component.literal("I").withStyle(ChatFormatting.ITALIC), ChatFormatting.ITALIC, "italic"},
            {Component.literal("U").withStyle(ChatFormatting.UNDERLINE), ChatFormatting.UNDERLINE, "underline"},
            {Component.literal("S").withStyle(ChatFormatting.STRIKETHROUGH), ChatFormatting.STRIKETHROUGH, "strikethrough"},
            {Component.literal("K"), ChatFormatting.OBFUSCATED, "obfuscated"},
            {Component.literal("R"), ChatFormatting.RESET, "reset"},
    };

    /**
     * 上次用的两个十六进制颜色
     */
    private static String lastFrom = "FF5555";
    private static String lastTo = "FFFF55";

    private ColorToolbar() {
    }

    /**
     * 按行排按钮：放不下就换行；一组按钮可以要求整组放在同一行
     */
    private static final class Flow {
        final int left;
        final int maxWidth;
        int x;
        int y;
        int rowHeight;

        Flow(int left, int y, int maxWidth) {
            this.left = left;
            this.x = left;
            this.y = y;
            this.maxWidth = maxWidth;
        }

        /**
         * 下一块（宽 width）放在哪：放不下就先换行
         */
        int place(int width, int height) {
            if (x > left && x + width > left + maxWidth) {
                newLine();
            }
            int at = x;
            x += width;
            rowHeight = Math.max(rowHeight, height);
            return at;
        }

        void gap(int width) {
            x += width;
        }

        /**
         * 接下来一组总宽 width 的东西要在同一行，当前行放不下就换行
         */
        void keepTogether(int width) {
            if (x > left && x + width > left + maxWidth) {
                newLine();
            }
        }

        void newLine() {
            x = left;
            y += rowHeight + ROW_GAP;
            rowHeight = 0;
        }

        int bottom() {
            return y + rowHeight;
        }

        /**
         * 宽 width 的一块放在当前行还放不放得下（行首永远放得下：place 不会在行首换行）
         */
        boolean fits(int width) {
            return x == left || x + width <= left + maxWidth;
        }
    }

    /**
     * 创建工具条的所有按钮
     *
     * @param add      把按钮交给页面登记的回调
     * @param x        左
     * @param y        上
     * @param maxWidth 可用宽度
     * @param target   目标输入框（可返回 null）
     * @return 占用的总高度
     */
    public static int build(@Nonnull Consumer<AbstractWidget> add, int x, int y, int maxWidth,
                            @Nonnull Supplier<EditBox> target) {
        Font font = Minecraft.getInstance().font;

        // 先建十六进制框（位置后面再定）：色块的 Shift + 点击要往里填颜色
        HexColorField fromField = new HexColorField(font, 0, 0, HEX_WIDTH, FORMAT_HEIGHT, lastFrom, value -> {
            if (value.length() == 6) {
                lastFrom = value;
            }
        });
        HexColorField toField = new HexColorField(font, 0, 0, HEX_WIDTH, FORMAT_HEIGHT, lastTo, value -> {
            if (value.length() == 6) {
                lastTo = value;
            }
        });

        // 第一行：16 色
        Flow flow = new Flow(x, y, maxWidth);
        for (ChatFormatting formatting : LegacyText.VANILLA_COLORS) {
            Integer rgb = formatting.getColor();
            if (rgb == null) {
                continue;
            }
            int swatchX = flow.place(SWATCH, SWATCH);
            flow.gap(SWATCH_GAP);
            Component name = Component.translatable("gui.battlecorrection.editor.color." + formatting.getName());
            int color = rgb;
            add.accept(FlatButton.swatch(swatchX, flow.y, SWATCH, 0xFF000000 | color, name, () -> {
                if (Screen.hasShiftDown()) {
                    fromField.setRgb(color);
                } else {
                    insert(target, String.valueOf(LegacyText.AMPERSAND) + formatting.getChar());
                }
            }).keepFocus());
        }

        // 第二行起：格式 + 清除 + 帮助，然后是十六进制一组
        flow.newLine();
        for (Object[] format : FORMATS) {
            Component label = (Component) format[0];
            ChatFormatting formatting = (ChatFormatting) format[1];
            String key = (String) format[2];
            int buttonX = flow.place(FORMAT_WIDTH, FORMAT_HEIGHT);
            flow.gap(FORMAT_GAP);
            add.accept(new FlatButton(buttonX, flow.y, FORMAT_WIDTH, FORMAT_HEIGHT, label,
                    () -> insert(target, String.valueOf(LegacyText.AMPERSAND) + formatting.getChar()))
                    .tooltip(Component.translatable("gui.battlecorrection.editor.format." + key))
                    .keepFocus());
        }
        Component clearLabel = Component.translatable("gui.battlecorrection.editor.format.clear");
        int clearWidth = Math.max(FORMAT_WIDTH, font.width(clearLabel) + 10);
        int clearX = flow.place(clearWidth, FORMAT_HEIGHT);
        flow.gap(FORMAT_GAP);
        add.accept(new FlatButton(clearX, flow.y, clearWidth, FORMAT_HEIGHT, clearLabel, () -> stripCodes(target))
                .tooltip(Component.translatable("gui.battlecorrection.editor.format.clear.tooltip"))
                .keepFocus());
        int helpX = flow.place(FORMAT_HEIGHT + 2, FORMAT_HEIGHT);
        flow.gap(GROUP_GAP);
        add.accept(new FlatButton(helpX, flow.y, FORMAT_HEIGHT + 2, FORMAT_HEIGHT, Component.literal("?"), () -> {
        }).tooltip(Component.translatable("gui.battlecorrection.editor.codes.help")).keepFocus());

        Component insertLabel = Component.translatable("gui.battlecorrection.editor.hex.insert");
        Component gradientLabel = Component.translatable("gui.battlecorrection.editor.hex.gradient");
        int insertWidth = Math.max(FORMAT_WIDTH, font.width(insertLabel) + 10);
        int gradientWidth = Math.max(FORMAT_WIDTH, font.width(gradientLabel) + 10);
        int groupWidth = 2 * HEX_WIDTH + insertWidth + gradientWidth + 3 * FORMAT_GAP + GROUP_GAP - FORMAT_GAP;
        if (groupWidth <= maxWidth) {
            flow.keepTogether(groupWidth);
        }

        int fromX = flow.place(HEX_WIDTH, FORMAT_HEIGHT);
        flow.gap(FORMAT_GAP);
        fromField.moveTo(fromX, flow.y);
        fromField.setTooltip(Tooltip.create(Component.translatable("gui.battlecorrection.editor.hex.from.tooltip")));
        fromField.onSubmit(() -> insertHex(target, fromField));
        add.accept(fromField);
        int insertX = flow.place(insertWidth, FORMAT_HEIGHT);
        flow.gap(GROUP_GAP);
        add.accept(new FlatButton(insertX, flow.y, insertWidth, FORMAT_HEIGHT, insertLabel,
                () -> insertHex(target, fromField))
                .tooltip(Component.translatable("gui.battlecorrection.editor.hex.insert.tooltip"))
                .keepFocus());

        int toX = flow.place(HEX_WIDTH, FORMAT_HEIGHT);
        flow.gap(FORMAT_GAP);
        toField.moveTo(toX, flow.y);
        toField.setTooltip(Tooltip.create(Component.translatable("gui.battlecorrection.editor.hex.to.tooltip")));
        toField.onSubmit(() -> gradient(target, fromField, toField));
        add.accept(toField);
        int gradientX = flow.place(gradientWidth, FORMAT_HEIGHT);
        add.accept(new FlatButton(gradientX, flow.y, gradientWidth, FORMAT_HEIGHT, gradientLabel,
                () -> gradient(target, fromField, toField))
                .tooltip(Component.translatable("gui.battlecorrection.editor.hex.gradient.tooltip"))
                .keepFocus());

        // 调色盘：这一行放得下带文字的就带文字，只放得下图标就只画图标，都放不下就换行（换了行就有地方写字了）
        flow.gap(FORMAT_GAP);
        Component paletteLabel = Component.translatable("gui.battlecorrection.editor.palette.open");
        int labeledWidth = PaletteButton.widthFor(font, paletteLabel, false);
        boolean iconOnly = !flow.fits(labeledWidth) && flow.fits(PaletteButton.ICON_ONLY_WIDTH);
        int paletteWidth = iconOnly ? PaletteButton.ICON_ONLY_WIDTH : labeledWidth;
        int paletteX = flow.place(paletteWidth, FORMAT_HEIGHT);
        add.accept(new PaletteButton(paletteX, flow.y, paletteWidth, FORMAT_HEIGHT, paletteLabel, iconOnly,
                () -> openPalette(target, fromField, toField))
                .tooltip(Component.translatable("gui.battlecorrection.editor.palette.open.tooltip"))
                .keepFocus());

        return flow.bottom() - y;
    }

    /**
     * 打开调色盘：带上目标框和两个十六进制框的颜色；应用之后头尾颜色写回静态字段（回到编辑器时工具条重建，框里就是新颜色）
     */
    private static void openPalette(@Nonnull Supplier<EditBox> target, @Nonnull HexColorField fromField,
                                    @Nonnull HexColorField toField) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.screen;
        if (parent == null) {
            return;
        }
        // 先取一次目标：Lore 页借这次调用记下"最近有焦点的行"，回到编辑器后靠它找回同一行
        targetOf(target);
        int from = fromField.rgb() >= 0 ? fromField.rgb() : Integer.parseInt(lastFrom, 16);
        int to = toField.rgb() >= 0 ? toField.rgb() : Integer.parseInt(lastTo, 16);
        minecraft.setScreen(new ColorPaletteScreen(parent, target, from, to, (head, tail) -> {
            lastFrom = ColorMath.hex(head);
            lastTo = ColorMath.hex(tail);
        }));
    }

    /**
     * 拿到可用的目标框
     */
    @Nullable
    private static EditBox targetOf(@Nonnull Supplier<EditBox> target) {
        EditBox box = target.get();
        if (box == null || !box.visible || !box.active) {
            return null;
        }
        return box;
    }

    /**
     * 往目标输入框插入一段代码
     *
     * @param target 目标提供者
     * @param code   编辑文本写法的代码
     */
    private static void insert(@Nonnull Supplier<EditBox> target, @Nonnull String code) {
        EditBox box = targetOf(target);
        if (box == null) {
            return;
        }
        insertInto(box, code);
    }

    /**
     * 往输入框的光标处（有选区就替换选区）插入一段代码，再把焦点还给它
     *
     * @param box  输入框
     * @param code 编辑文本写法的代码
     */
    public static void insertInto(@Nonnull EditBox box, @Nonnull String code) {
        if (box instanceof FlatEditBox flat) {
            flat.insertCode(code);
        } else {
            box.insertText(code);
        }
        refocus(box);
    }

    /**
     * 插入左边十六进制框的颜色
     */
    private static void insertHex(@Nonnull Supplier<EditBox> target, @Nonnull HexColorField field) {
        int rgb = field.rgb();
        if (rgb < 0) {
            field.setError(true);
            return;
        }
        insert(target, String.valueOf(LegacyText.AMPERSAND) + LegacyText.HEX_MARK + field.getValue());
    }

    /**
     * 渐变
     */
    private static void gradient(@Nonnull Supplier<EditBox> target, @Nonnull HexColorField fromField,
                                 @Nonnull HexColorField toField) {
        int from = fromField.rgb();
        int to = toField.rgb();
        if (from < 0 || to < 0) {
            fromField.setError(from < 0);
            toField.setError(to < 0);
            return;
        }
        EditBox box = targetOf(target);
        if (box == null || box.getValue().isEmpty()) {
            return;
        }
        int start = 0;
        int end = 0;
        if (box instanceof FlatEditBox flat) {
            start = flat.selectionStart();
            end = flat.selectionEnd();
        }
        applyEdit(box, LegacyEdits.gradient(box.getValue(), start, end, from, to));
    }

    /**
     * 清除选中文字（没选中就是整个框）里的代码
     */
    private static void stripCodes(@Nonnull Supplier<EditBox> target) {
        EditBox box = targetOf(target);
        if (box == null || box.getValue().isEmpty()) {
            return;
        }
        int start = 0;
        int end = 0;
        if (box instanceof FlatEditBox flat) {
            start = flat.selectionStart();
            end = flat.selectionEnd();
        }
        applyEdit(box, LegacyEdits.stripCodes(box.getValue(), start, end));
    }

    /**
     * 改写结果放回输入框，并选中改写过的那一段（方便接着换一组颜色重做），焦点还给它
     *
     * @param box    输入框
     * @param result 改写结果
     */
    public static void applyEdit(@Nonnull EditBox box, @Nonnull LegacyEdits.Result result) {
        if (result.text().equals(box.getValue())) {
            refocus(box);
            return;
        }
        if (box instanceof FlatEditBox flat) {
            flat.replaceAll(result.text(), result.start(), result.end());
        } else {
            box.setValue(result.text());
        }
        refocus(box);
    }

    /**
     * 焦点还给目标框（点十六进制框时焦点被拿走了；从调色盘回来时编辑器是重建过的，焦点是空的）
     *
     * @param box 输入框
     */
    public static void refocus(@Nonnull EditBox box) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null && screen.getFocused() != box) {
            screen.setFocused(box);
        }
    }
}
