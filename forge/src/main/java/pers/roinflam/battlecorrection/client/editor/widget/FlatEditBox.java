package pers.roinflam.battlecorrection.client.editor.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.editor.LegacyText;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * 单行输入框：原版 EditBox 换个边框颜色，再处理颜色码
 * <p>
 * 原版 EditBox 的文字排版、光标、选区逻辑都是私有的，没法重画；它画的边框是 x-1..x+w+1 那一圈 1px，
 * 这里在它画完之后用主题的沟槽色把那一圈盖一遍，再补上内阴影和焦点亮线，内部黑底保留（凹槽本来就该更深）。
 * <p>
 * legacyCodes 打开时（名字、Lore 这类颜色码文本）：
 * <ul>
 *   <li>直接键入的 § 变成 &；粘贴带 § 的文本按 § 文本整段转换（{@link LegacyText#fromClipboard}），
 *   不成代码的 § 也变成字面的 &，不吞字符。原版 insertText 会把 § 过滤掉，不先换就丢了</li>
 *   <li>代码按自己的颜色高亮（{@code &6} 显示成金色，格式码青色，{@code &&} 的转义符变暗），文字本身保持默认色，
 *   方便看清哪些是代码。高亮只改颜色不改字宽，光标和选区的位置计算不受影响</li>
 * </ul>
 * 默认最大长度放宽到 4096（原版默认只有 32，Lore 一行就超了）。颜色码文本粘贴时不受这个上限截断
 * （最多放宽到 {@link #HARD_MAX_LENGTH}），免得截出半个代码；打字照常受限。
 * <p>
 * 原版不给读选区的另一端（highlightPos 是私有的），这里重写 setHighlightPos 自己记一份，渐变、清除代码要用。
 * <p>
 * 提示文字：原版只在没焦点时画，可很多框一打开就有焦点（Lore 页新加的行、跳到行），提示就永远看不到。
 * 这里在有焦点、内容为空时自己补画一遍，颜色调暗，放在闪烁的 "_" 光标右边，免得和它叠在一起。
 */
public class FlatEditBox extends EditBox {

    /**
     * 默认最大长度
     */
    public static final int DEFAULT_MAX_LENGTH = 4096;

    /**
     * 往一个框里塞文字时最多放宽到多长（渐变会让文本变长好几倍）
     */
    public static final int HARD_MAX_LENGTH = 32767;

    private static final Style FORMAT_STYLE = Style.EMPTY.withColor(EditorTheme.ACCENT & 0xFFFFFF);
    private static final Style RESET_STYLE = Style.EMPTY.withColor(EditorTheme.TEXT_MUTED & 0xFFFFFF);
    private static final Style DIM_STYLE = Style.EMPTY.withColor(EditorTheme.TEXT_DIM & 0xFFFFFF);
    private static final Style CUSTOM_STYLE = Style.EMPTY.withColor(EditorTheme.WARNING & 0xFFFFFF);
    private static final Style STRAY_STYLE = Style.EMPTY.withColor(EditorTheme.DANGER & 0xFFFFFF);
    private static final Style[] NO_STYLES = new Style[0];

    private final boolean legacyCodes;
    @Nullable
    private Consumer<List<String>> multiLinePaste;
    @Nullable
    private Runnable backspaceAtStart;
    /**
     * 选区的另一端（原版的 highlightPos）
     */
    private int highlight;
    private boolean error;
    /**
     * 当前最大长度（原版的 getMaxLength 是私有的）
     */
    private int maxLength = 32;

    /**
     * 提示文字（原版的 hint 是私有的，自己记一份）；原版是否画边框（同理）
     */
    @Nullable
    private Component hint;
    private boolean bordered = true;
    @Nullable
    private String suggestion;
    /**
     * 有焦点时画的提示：按提示对象和可用宽度缓存裁好的结果，每帧不分配
     */
    @Nullable
    private Component focusedHintFor;
    private int focusedHintWidth = -1;
    @Nullable
    private FormattedCharSequence focusedHint;
    private int focusedHintX;
    /**
     * 字体（原版的是私有的）和 "_" 光标的宽度（第一次画提示时量，-1 = 还没量）
     */
    private final Font textFont;
    private int cursorWidth = -1;

    /**
     * 高亮缓存：对应的文本（按对象身份比，文本一变就重建）和逐字样式
     */
    @Nullable
    private String highlightedFor;
    private Style[] highlightStyles = NO_STYLES;

    /**
     * @param font        字体
     * @param x           左
     * @param y           上
     * @param width       宽
     * @param height      高
     * @param hint        空时显示的灰字提示
     * @param legacyCodes 是否是颜色码文本（§ → &、代码高亮）
     */
    public FlatEditBox(@Nonnull Font font, int x, int y, int width, int height, @Nonnull Component hint,
                       boolean legacyCodes) {
        super(font, x, y, width, height, hint);
        this.legacyCodes = legacyCodes;
        this.textFont = font;
        setBordered(true);
        setHint(hint);
        setMaxLength(DEFAULT_MAX_LENGTH);
        if (legacyCodes) {
            setFormatter(this::highlight);
        }
    }

    /**
     * 粘贴多行文本时的处理（链式）
     * <p>
     * 不设的话多行文本会被原版把换行过滤掉揉成一行；设了就整段交给回调自己拆，回调里想往本框写第一行
     * 直接再调 {@link #insertText}（没有换行不会再触发回调）。
     *
     * @param handler 回调，参数是按行拆好的文本（原样，§ 还没转换）
     * @return this
     */
    @Nonnull
    public FlatEditBox onMultiLinePaste(@Nonnull Consumer<List<String>> handler) {
        this.multiLinePaste = handler;
        return this;
    }

    /**
     * 光标在最前面（没有选区）时按退格的处理（链式），Lore 页用来把这一行并到上一行
     *
     * @param handler 回调
     * @return this
     */
    @Nonnull
    public FlatEditBox onBackspaceAtStart(@Nonnull Runnable handler) {
        this.backspaceAtStart = handler;
        return this;
    }

    /**
     * 标成错误状态（边框变红）
     *
     * @param error true = 有错
     */
    public void setError(boolean error) {
        this.error = error;
    }

    /**
     * 选区起点（没选中时等于光标位置）
     */
    public int selectionStart() {
        return Math.min(getCursorPosition(), Mth.clamp(highlight, 0, getValue().length()));
    }

    /**
     * 选区终点（不含）
     */
    public int selectionEnd() {
        return Math.max(getCursorPosition(), Mth.clamp(highlight, 0, getValue().length()));
    }

    /**
     * 有没有选中文字
     */
    public boolean hasSelection() {
        return selectionStart() != selectionEnd();
    }

    /**
     * 选中一段（光标放在 end）
     *
     * @param start 起点
     * @param end   终点
     */
    public void select(int start, int end) {
        setCursorPosition(end);
        setHighlightPos(start);
    }

    /**
     * 整段替换内容并选中改写过的那一段：一次写入，只触发一次回调；超过最大长度时先放宽
     *
     * @param value 新内容
     * @param start 选区起点
     * @param end   选区终点
     */
    public void replaceAll(@Nonnull String value, int start, int end) {
        ensureCapacity(value.length());
        setValue(value);
        int length = getValue().length();
        select(Mth.clamp(start, 0, length), Mth.clamp(end, 0, length));
    }

    /**
     * 在光标处插入一段代码（颜色工具条用）
     * <p>
     * 和打字不同：光标前面如果是一个单独的 &，插入后要保证它仍然显示为 &（{@link LegacyText#guardInsertion}）。
     *
     * @param code 编辑文本写法的代码，例如 {@code &a}
     */
    public void insertCode(@Nonnull String code) {
        if (!legacyCodes) {
            insertText(code);
            return;
        }
        String value = getValue();
        int start = selectionStart();
        int end = selectionEnd();
        String guarded = LegacyText.guardInsertion(value.substring(0, start), code, value.substring(end));
        ensureCapacity(value.length() - (end - start) + guarded.length());
        super.insertText(guarded);
    }

    /**
     * 最大长度不够放下 length 个字符时放宽（不超过 {@link #HARD_MAX_LENGTH}）
     *
     * @param length 需要的长度
     */
    public void ensureCapacity(int length) {
        if (length > maxLength) {
            setMaxLength(Math.min(HARD_MAX_LENGTH, length));
        }
    }

    @Override
    public void setMaxLength(int length) {
        super.setMaxLength(length);
        maxLength = length;
    }

    /**
     * 当前最大长度
     */
    public int maxLength() {
        return maxLength;
    }

    @Override
    public void insertText(@Nonnull String text) {
        if (multiLinePaste != null && text.indexOf('\n') >= 0) {
            multiLinePaste.accept(Arrays.asList(text.replace("\r", "").split("\n", -1)));
            return;
        }
        if (legacyCodes && text.indexOf(LegacyText.SECTION) >= 0) {
            String value = getValue();
            int start = selectionStart();
            int end = selectionEnd();
            String after = value.substring(end);
            String converted = LegacyText.fromClipboard(text, after);
            converted = LegacyText.guardInsertion(value.substring(0, start), converted, after);
            // § 码转成 & 码后可能变长（§x 十六进制变 &#、& 要转义）；按上限截断会截出半个代码，粘贴时先放宽
            ensureCapacity(value.length() - (end - start) + converted.length());
            super.insertText(converted);
            return;
        }
        if (legacyCodes && text.length() > 1) {
            // 粘贴（打字一次只来一个字符）：同理，不从代码中间截断
            ensureCapacity(getValue().length() - (selectionEnd() - selectionStart()) + text.length());
        }
        super.insertText(text);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        // 原版把 § 当非法字符直接丢掉；这里当成 & 收下（某些输入法 / 键盘布局能直接打出 §）
        if (legacyCodes && c == LegacyText.SECTION) {
            return super.charTyped(LegacyText.AMPERSAND, modifiers);
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (backspaceAtStart != null && keyCode == GLFW.GLFW_KEY_BACKSPACE && canConsumeInput()
                && getCursorPosition() == 0 && !hasSelection()) {
            backspaceAtStart.run();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void setHint(@Nonnull Component hint) {
        super.setHint(hint);
        this.hint = hint;
    }

    @Override
    public void setBordered(boolean bordered) {
        super.setBordered(bordered);
        this.bordered = bordered;
    }

    @Override
    public void setSuggestion(@Nullable String suggestion) {
        super.setSuggestion(suggestion);
        this.suggestion = suggestion;
    }

    @Override
    public void setHighlightPos(int position) {
        super.setHighlightPos(position);
        highlight = position;
    }

    @Override
    public void renderWidget(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(g, mouseX, mouseY, partialTick);
        if (!isVisible()) {
            return;
        }
        renderFocusedHint(g);
        int x = getX() - 1;
        int y = getY() - 1;
        int w = getWidth() + 2;
        int h = getHeight() + 2;
        boolean focused = isFocused();
        // 盖掉原版的灰边：沟槽色外圈 + 上/左内阴影，焦点时整圈青色并在底边加一道亮线；出错时整圈红色
        int border = error ? EditorTheme.DANGER : focused ? EditorTheme.INPUT_BORDER_FOCUS : EditorTheme.INPUT_BORDER;
        EditorTheme.wellOutline(g, x, y, w, h, border);
        EditorTheme.rect(g, x + 1, y + 1, w - 2, 1, EditorTheme.WELL_TOP);
        EditorTheme.rect(g, x + 1, y + 1, 1, h - 2, EditorTheme.WELL_TOP);
        if (focused) {
            EditorTheme.rect(g, x + 2, y + h - 1, Math.max(0, w - 4), 1, error ? EditorTheme.DANGER : EditorTheme.ACCENT_HI);
        } else if (active) {
            EditorTheme.rect(g, x + 1, y + h - 2, w - 2, 1, EditorTheme.WELL_LIGHT);
        }
    }

    /**
     * 有焦点、内容为空时补画提示文字（原版这时不画）
     * <p>
     * 原版的空框光标是一个 "_"，画在文字起点；提示放在它右边。框太窄、放在右边要被裁掉、而从起点画正好放得下时
     * （十六进制框的 "RRGGBB"），退回起点画，只在光标闪亮的半拍和 "_" 叠在一起；起点也放不下就放在右边按宽度裁掉。
     * 有补全建议（suggestion）时原版会把它画在光标后面，这时不画提示。
     */
    private void renderFocusedHint(@Nonnull GuiGraphics g) {
        Component current = hint;
        if (current == null || !isFocused() || suggestion != null || !getValue().isEmpty()) {
            return;
        }
        int textX = bordered ? getX() + 4 : getX();
        int textY = bordered ? getY() + (getHeight() - 8) / 2 : getY();
        int inner = bordered ? getWidth() - 8 : getWidth();
        if (current != focusedHintFor || inner != focusedHintWidth) {
            if (cursorWidth < 0) {
                cursorWidth = textFont.width("_");
            }
            focusedHintFor = current;
            focusedHintWidth = inner;
            int full = textFont.width(current);
            int offset = full > inner - cursorWidth && full <= inner ? 0 : cursorWidth;
            FormattedText fitted = full <= inner - offset ? current : textFont.substrByWidth(current, Math.max(0, inner - offset));
            focusedHint = Language.getInstance().getVisualOrder(fitted);
            focusedHintX = offset;
        }
        FormattedCharSequence text = focusedHint;
        if (text != null) {
            g.drawString(textFont, text, textX + focusedHintX, textY, EditorTheme.TEXT_DIM, false);
        }
    }

    // 代码高亮

    /**
     * 原版每帧对看得见的那一段调一次：value 没变就直接用缓存的逐字样式，只分配一个很小的迭代对象
     *
     * @param visible 要画的那一段
     * @param offset  这一段在整个文本里的起点
     * @return 带样式的字符序列
     */
    @Nonnull
    private FormattedCharSequence highlight(@Nonnull String visible, @Nonnull Integer offset) {
        String value = getValue();
        if (value != highlightedFor) {
            rebuildHighlight(value);
        }
        Style[] styles = highlightStyles;
        if (styles.length == 0) {
            return FormattedCharSequence.forward(visible, Style.EMPTY);
        }
        int base = offset;
        return sink -> {
            int length = visible.length();
            int i = 0;
            while (i < length) {
                char c = visible.charAt(i);
                int codePoint = c;
                int size = 1;
                if (Character.isHighSurrogate(c) && i + 1 < length && Character.isLowSurrogate(visible.charAt(i + 1))) {
                    codePoint = Character.toCodePoint(c, visible.charAt(i + 1));
                    size = 2;
                } else if (Character.isSurrogate(c)) {
                    codePoint = 0xFFFD;
                }
                int index = base + i;
                Style style = index < styles.length && styles[index] != null ? styles[index] : Style.EMPTY;
                if (!sink.accept(i, style, codePoint)) {
                    return false;
                }
                i += size;
            }
            return true;
        };
    }

    /**
     * 文本变了：重新切记号，算出每个字符的高亮样式（没有代码的文本不分配数组）
     *
     * @param value 文本
     */
    private void rebuildHighlight(@Nonnull String value) {
        highlightedFor = value;
        if (value.indexOf(LegacyText.AMPERSAND) < 0 && value.indexOf(LegacyText.SECTION) < 0) {
            highlightStyles = NO_STYLES;
            return;
        }
        Style[] styles = new Style[value.length()];
        boolean any = false;
        LegacyText.Scanner scanner = LegacyText.scanEditable(value);
        while (scanner.next()) {
            Style style;
            int end = scanner.end();
            switch (scanner.type()) {
                case COLOR, HEX -> style = codeColor(scanner.rgb());
                case FORMAT -> style = FORMAT_STYLE;
                case RESET -> style = RESET_STYLE;
                case CUSTOM -> style = CUSTOM_STYLE;
                case STRAY -> style = STRAY_STYLE;
                case ESCAPE -> {
                    // 只把转义用的那个 & 变暗，留下来显示的 & 照常
                    style = DIM_STYLE;
                    end = scanner.start() + 1;
                }
                default -> style = null;
            }
            if (style != null) {
                any = true;
                Arrays.fill(styles, scanner.start(), end, style);
            }
        }
        highlightStyles = any ? styles : NO_STYLES;
    }

    /**
     * 颜色码用它自己的颜色画；太暗的（黑、深蓝这些在黑底上看不见）往白色提一提
     *
     * @param rgb 颜色
     * @return 样式
     */
    @Nonnull
    private static Style codeColor(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        if (luminance < 0.35) {
            rgb = EditorTheme.lerp(0xFF000000 | rgb, 0xFFFFFFFF, 0.45F) & 0xFFFFFF;
        }
        return Style.EMPTY.withColor(rgb);
    }
}
