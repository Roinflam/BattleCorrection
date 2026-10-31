package pers.roinflam.battlecorrection.client.editor.widget;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.lwjgl.input.Mouse;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.snbt.BracketMatcher;
import pers.roinflam.battlecorrection.client.editor.snbt.CharWidths;
import pers.roinflam.battlecorrection.client.editor.snbt.ColorCodes;
import pers.roinflam.battlecorrection.client.editor.snbt.SnbtEditing;
import pers.roinflam.battlecorrection.client.editor.snbt.SnbtLexer;
import pers.roinflam.battlecorrection.client.editor.snbt.TextDocument;
import pers.roinflam.battlecorrection.client.editor.snbt.TextSearch;
import pers.roinflam.battlecorrection.client.editor.snbt.WrapLayout;
import pers.roinflam.battlecorrection.client.editor.ui.AbstractWidget;
import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.client.editor.ui.Font;
import pers.roinflam.battlecorrection.client.editor.ui.GuiGraphics;
import pers.roinflam.battlecorrection.client.editor.ui.Keys;
import pers.roinflam.battlecorrection.client.editor.ui.Screen;
import pers.roinflam.battlecorrection.client.editor.ui.TextRun;
import pers.roinflam.battlecorrection.client.editor.ui.Ui;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * SNBT 编辑框：行号栏 + 自动换行 + 语法高亮 + 括号配对 + 查找高亮 + 错误标记
 * <p>
 * 1.12.2 的 GuiTextField 只有一行，输入和粘贴还会过一遍 ChatAllowedCharacters 把 § 吞掉（粘贴带颜色码的 SNBT，§c 只剩一个 c），
 * 所以多行编辑整个自己做。文本、光标、撤销都在 {@link TextDocument} 里，排版在 {@link WrapLayout} 里，这个控件只是它们的一个视图：
 * <ul>
 *   <li>每帧只画可视的那几行：二分找到首行，往下数到框底为止；每行按词上色后拼成几段文字交给字体，拼好的结果按行缓存，
 *   文本不变时每帧不再重新拼；</li>
 *   <li>改动只增量重排改动点附近，分词结果按行缓存、只给可视行分词，超过 {@link #HIGHLIGHT_LIMIT} 个字符就不做语法高亮和括号配对；</li>
 *   <li>字符宽度按码点查表缓存，排版和测宽都不走字体的字符串接口（1.12.2 的字体渲染器对每个字符的前进宽度取整，
 *   和逐字查到的宽度一致，所以光标、选区和画出来的字对得上）。</li>
 * </ul>
 * 鼠标：点一下移光标并清选区，Shift+点扩选，拖动选择（拖出框外自动滚），双击选词，三击选行，点行号选整行（可拖），拖滚动条。
 * 键盘：方向键（Ctrl 按词、Shift 扩选）、Home（先到首个非空白）/ End、PgUp / PgDn（视图跟着翻页）、Ctrl+上下滚动一行、
 * Ctrl+A / C / X / V（以及 Ctrl+Insert / Shift+Insert / Shift+Delete）、
 * Ctrl+Z / Y / Shift+Z（文本级撤销，焦点在这里时一定吃掉，不会落到界面的整体撤销上）、
 * Tab / Shift+Tab 缩进、Ctrl+D 复制行、Alt+上下移动行、回车自动缩进、括号引号自动补全。
 * Ctrl+S、Ctrl+Tab、Ctrl+数字、Ctrl+Enter、Ctrl+F、Ctrl+G、F1、F3 不处理，留给页面和界面；
 * Ctrl+Alt 加字母一律吃掉：那多半是 AltGr 在打字。输入法提交的汉字和普通键入一样从 charTyped 进来。
 * <p>
 * 光标落点：软折行的行末按约定算下一行行首，点击和上下移动不会把光标放在那里（见 {@link WrapLayout#caretOffsetAt}），
 * 拖选和 Shift+点击可以选到行末。
 */
public class FlatTextArea extends AbstractWidget {

    /**
     * 每行高度
     */
    public static final int ROW_HEIGHT = 10;

    /**
     * 超过这么多字符就不做语法高亮（只给 § 颜色码上色），也不做括号配对
     */
    public static final int HIGHLIGHT_LIMIT = 256 * 1024;

    /**
     * 逻辑行超过这么多字符就不给这一行做语法高亮：分词按整行来，6.4 万字符的一行分一遍约 1 毫秒，再长每次改动后的那一帧就明显变慢
     */
    public static final int LONG_LINE_CHARS = 64 * 1024;

    private static final int PAD = 3;
    private static final int GUTTER_PAD = 3;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int SCROLLBAR_HIT = 6;
    private static final int MIN_WRAP_WIDTH = 24;
    private static final int WHEEL_ROWS = 3;
    private static final long MULTI_CLICK_MS = 350L;
    private static final long BLINK_MS = 530L;
    private static final int TOKEN_CACHE_LIMIT = 512;
    private static final int ROW_CACHE_LIMIT = 512;
    private static final char REPLACEMENT = (char) 0xFFFD;

    // 颜色
    private static final int GUTTER_BG = 0xFF05090C;
    private static final int GUTTER_LINE = 0xFF14232D;
    private static final int LINE_NUMBER = 0xFF3D5563;
    private static final int LINE_NUMBER_CURRENT = EditorTheme.TEXT_MUTED;
    private static final int CURRENT_LINE = 0x1437C8FF;
    private static final int CURRENT_LINE_UNFOCUSED = 0x0937C8FF;
    private static final int SELECTION = EditorTheme.ACCENT_SOFT;
    private static final int SELECTION_UNFOCUSED = 0x2237C8FF;
    private static final int SEARCH_HIT = 0x50F2C25C;
    private static final int BRACKET_MATCH = 0x5537C8FF;
    private static final int BRACKET_BAD = 0x70FF5C7A;
    private static final int ERROR_LINE = 0x1CFF5C7A;
    private static final int OVERLAY_BG = 0xB0070C10;

    private static final int FLAG_LINE_START = 1;
    private static final int FLAG_NEWLINE = 2;

    // 语法高亮配色（不带透明度；装箱好的，拼文字段时不再分配）
    private static final Integer[] KIND_COLORS = new Integer[SnbtLexer.KIND_COUNT];
    private static final Integer[] TINT_COLORS = new Integer[16];
    /**
     * § 本身：下标 = 参数 + 1（0 = 重置码 r，1-16 = 颜色，17 = 格式码 k-o）
     */
    private static final Integer[] SECTION_COLORS = new Integer[18];
    /**
     * § 后面的代码字符，下标同上
     */
    private static final Integer[] CODE_COLORS = new Integer[18];

    static {
        int[] colors = new int[SnbtLexer.KIND_COUNT];
        Arrays.fill(colors, EditorTheme.TEXT & 0xFFFFFF);
        colors[SnbtLexer.KEY] = 0x8BD5F0;
        colors[SnbtLexer.STRING] = 0xA6DB8C;
        colors[SnbtLexer.ESCAPE] = 0xE5C07B;
        colors[SnbtLexer.NUMBER] = 0xF2A65E;
        colors[SnbtLexer.SUFFIX] = 0xB07A4C;
        colors[SnbtLexer.BOOL] = 0xC792EA;
        colors[SnbtLexer.BRACE] = 0xC9D7E0;
        colors[SnbtLexer.BRACKET] = 0xC9D7E0;
        colors[SnbtLexer.COLON] = 0x6F8B9A;
        colors[SnbtLexer.COMMA] = 0x6F8B9A;
        colors[SnbtLexer.ARRAY_TYPE] = 0xC792EA;
        for (int kind = 0; kind < colors.length; kind++) {
            KIND_COLORS[kind] = colors[kind];
        }
        int well = EditorTheme.WELL & 0xFFFFFF;
        for (int i = 0; i < 16; i++) {
            int readable = readable(ColorCodes.rgb(i));
            TINT_COLORS[i] = readable;
            CODE_COLORS[i + 1] = readable;
            SECTION_COLORS[i + 1] = mix(readable, well, 0.5F);
        }
        int format = 0xB48EAD;
        int reset = EditorTheme.TEXT_MUTED & 0xFFFFFF;
        CODE_COLORS[0] = reset;
        SECTION_COLORS[0] = mix(reset, well, 0.5F);
        CODE_COLORS[17] = format;
        SECTION_COLORS[17] = mix(format, well, 0.5F);
    }

    /**
     * 拖动的是什么
     */
    private enum Drag {
        NONE,
        TEXT,
        GUTTER,
        SCROLLBAR
    }

    /**
     * 一个显示行拼好的文字段（对应的行终点一起记下，排版变了就不再用）
     */
    private static final class RowRuns {
        final int end;
        final List<TextRun> runs;

        RowRuns(int end, @Nonnull List<TextRun> runs) {
            this.end = end;
            this.runs = runs;
        }
    }

    private final Font font;
    private final TextDocument doc;
    private final Component placeholder;
    private final FontWidths widths;
    private final WrapLayout layout;
    private final Int2ObjectOpenHashMap<int[]> tokenCache = new Int2ObjectOpenHashMap<>();
    private final IntArrayList tokenScratch = new IntArrayList();
    private final Int2ObjectOpenHashMap<RowRuns> rowCache = new Int2ObjectOpenHashMap<>();
    private final StringBuilder runText = new StringBuilder();
    private final Runnable passes = this::renderPasses;
    private long tokenCacheVersion = -1;
    private long rowCacheVersion = -1;
    private boolean rowCacheHighlight;

    private int scrollPx;
    private float preferredX = -1;
    private boolean readOnly;
    @Nullable
    private Component overlay;
    private int errorOffset = -1;
    @Nullable
    private TextSearch.Result search;
    private boolean revealPending;

    // 几何
    private int gutterDigits;
    private int gutterWidth;
    private int textLeft;
    private int textRight;
    private int textTop;
    private int textBottom;

    // 鼠标
    private Drag drag = Drag.NONE;
    private int dragLine;
    private double dragGrab;
    private long lastClickTime;
    private int lastClickOffset = -1;
    private int clickCount;
    private long lastAutoScroll;
    private long caretEpoch;

    // 括号配对缓存（按版本号 + 光标位置）
    private long bracketVersion = -1;
    private int bracketCursor = -1;
    private int bracketAt = -1;
    private int bracketMatch = -1;
    private boolean bracketBad;

    // 每帧的可视行（数组按框高预分配，帧内只填不分配）
    private int[] visStart = new int[0];
    private int[] visEnd = new int[0];
    private int[] visLine = new int[0];
    private int[] visFlags = new int[0];
    private int visFirst;
    private int visCount;
    private boolean frameFocused;
    @Nullable
    private GuiGraphics frameGraphics;

    /**
     * @param font        字体
     * @param x           左
     * @param y           上
     * @param width       宽
     * @param height      高
     * @param doc         文档（页面持有，控件重建时沿用）
     * @param placeholder 空时显示的灰字提示
     * @param message     控件名字
     */
    public FlatTextArea(@Nonnull Font font, int x, int y, int width, int height, @Nonnull TextDocument doc,
                        @Nonnull Component placeholder, @Nonnull Component message) {
        super(x, y, width, height, message);
        this.font = font;
        this.doc = doc;
        this.placeholder = placeholder;
        this.widths = new FontWidths(font);
        this.layout = new WrapLayout(widths);
        updateGeometry();
        layout.rebuild(doc, wrapWidth());
        doc.setViewListener(this::onDocChanged);
        // 恢复上次的滚动位置（页面切走再回来、窗口大小变化时控件是新建的）：顶端那一行 + 卷上去的像素，
        // 滚到底时也能原样回到底（只记行的话会差半行）
        scrollPx = layout.rowOf(doc.viewTop()) * ROW_HEIGHT + Math.min(ROW_HEIGHT - 1, doc.viewTopPixel());
        clampScroll();
        caretEpoch = Ui.millis();
    }

    // 给页面用的接口

    /**
     * 编辑的文档
     */
    @Nonnull
    public TextDocument document() {
        return doc;
    }

    /**
     * 只读（载入 / 格式化进行中时用）：还能移动光标、选择、复制，不能改
     */
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    /**
     * 盖在文本上的一行提示（"载入中…"），null = 不盖
     */
    public void setOverlay(@Nullable Component overlay) {
        this.overlay = overlay;
    }

    /**
     * 解析错误的位置，-1 = 没有
     */
    public void setErrorMarker(int offset) {
        this.errorOffset = offset;
    }

    /**
     * 查找结果（版本号和文档对不上时不画）
     */
    public void setSearchResult(@Nullable TextSearch.Result result) {
        this.search = result;
    }

    /**
     * 是否在做语法高亮（文本太大时关掉）
     */
    public boolean isHighlighting() {
        return doc.length() <= HIGHLIGHT_LIMIT;
    }

    /**
     * 改位置和高度（查找栏开关时用；宽度不变）
     *
     * @param y      新的上边
     * @param height 新的高度
     */
    public void setBounds(int y, int height) {
        setY(y);
        setHeight(height);
        if (updateGeometry()) {
            layout.rebuild(doc, wrapWidth());
        }
        ensureLayout();
        clampScroll();
    }

    /**
     * 把光标滚到看得见的地方
     *
     * @param center true = 不在可视范围时滚到正中（跳转用），false = 只滚到刚好看见（打字用）
     */
    public void revealCursor(boolean center) {
        revealPending = false;
        revealOffset(doc.cursor(), center);
    }

    /**
     * 把某个位置滚到看得见的地方
     */
    public void revealOffset(int offset, boolean center) {
        ensureLayout();
        int row = layout.rowOf(offset);
        int top = row * ROW_HEIGHT;
        int bottom = top + ROW_HEIGHT;
        int view = viewHeight();
        if (top >= scrollPx && bottom <= scrollPx + view) {
            return;
        }
        if (center) {
            setScroll(top - (view - ROW_HEIGHT) / 2);
        } else if (top < scrollPx) {
            setScroll(top);
        } else {
            setScroll(bottom - view);
        }
    }

    /**
     * 可视行数
     */
    public int visibleRows() {
        return Math.max(1, viewHeight() / ROW_HEIGHT);
    }

    // 几何 / 滚动

    /**
     * 按当前位置、大小和行数算各区域；返回折行宽度有没有变
     */
    private boolean updateGeometry() {
        int oldWrap = wrapWidth();
        gutterDigits = Math.max(3, digitCount(doc.lineCount()));
        gutterWidth = Math.round(widths.width('0') * gutterDigits) + GUTTER_PAD * 2;
        textLeft = getX() + 2 + gutterWidth + PAD;
        textRight = getX() + width - 2 - SCROLLBAR_WIDTH - PAD;
        textTop = getY() + 2;
        textBottom = getY() + height - 2;
        int capacity = Math.max(1, viewHeight() / ROW_HEIGHT + 3);
        if (visStart.length < capacity) {
            visStart = new int[capacity];
            visEnd = new int[capacity];
            visLine = new int[capacity];
            visFlags = new int[capacity];
        }
        return wrapWidth() != oldWrap;
    }

    private int wrapWidth() {
        return Math.max(MIN_WRAP_WIDTH, textRight - textLeft);
    }

    private int viewHeight() {
        return Math.max(ROW_HEIGHT, textBottom - textTop);
    }

    private int maxScroll() {
        return Math.max(0, layout.rowCount() * ROW_HEIGHT - viewHeight());
    }

    private void setScroll(int value) {
        scrollPx = Math.max(0, Math.min(value, maxScroll()));
        int row = Math.min(layout.rowCount() - 1, scrollPx / ROW_HEIGHT);
        doc.setViewTop(layout.rowStart(row), scrollPx - row * ROW_HEIGHT);
    }

    /**
     * 排版落后于文档时整篇重排（正常不会发生：改动都会通知当前的视图；只有已经被新控件顶替掉监听的旧控件还收到输入时才会走到）。
     * 输入处理都先过一遍，免得拿旧排版去算下标越界
     */
    private void ensureLayout() {
        if (layout.version() != doc.version()) {
            layout.rebuild(doc, wrapWidth());
            clampScroll();
        }
    }

    private void clampScroll() {
        setScroll(scrollPx);
    }

    private static int digitCount(int value) {
        int count = 1;
        while (value >= 10) {
            value /= 10;
            count++;
        }
        return count;
    }

    /**
     * 文档改了：增量重排；行数位数变了（行号栏变宽）就整篇重排
     */
    private void onDocChanged(@Nonnull TextDocument.Change change) {
        if (Math.max(3, digitCount(doc.lineCount())) != gutterDigits && updateGeometry()) {
            layout.rebuild(doc, wrapWidth());
        } else {
            layout.update(doc, change);
        }
        preferredX = -1;
        revealPending = true;
        caretEpoch = Ui.millis();
        clampScroll();
    }

    // 绘制

    @Override
    protected void renderWidget(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 正常不会走到（改动都会通知）；万一漏了，整篇重排兜底
        ensureLayout();
        if (drag != Drag.NONE) {
            continueDrag(mouseX, mouseY);
        }
        if (revealPending) {
            revealPending = false;
            revealOffset(doc.cursor(), false);
        }

        int x = getX();
        int y = getY();
        EditorTheme.well(g, x, y, width, height, true);
        g.fill(x + 1, y + 1, x + 1 + gutterWidth, y + height - 1, GUTTER_BG);
        g.fill(x + 1 + gutterWidth, y + 1, x + 2 + gutterWidth, y + height - 1, GUTTER_LINE);

        g.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        if (doc.length() == 0 && !isFocused() && overlay == null) {
            g.drawWordWrap(font, placeholder, textLeft, textTop + 1, Math.max(10, textRight - textLeft), EditorTheme.TEXT_DIM);
        } else {
            prepareVisibleRows();
            // 背景、文字、覆盖层三趟，色块在画布里攒着一起提交，文字一行一次。
            // 回调是构造时绑好的，画布通过字段传进去，免得每帧新建一个捕获参数的 lambda
            frameGraphics = g;
            g.drawManaged(passes);
            frameGraphics = null;
        }
        g.disableScissor();

        renderScrollbar(g, mouseX, mouseY);
        if (overlay != null) {
            g.fill(x + 1, y + 1, x + width - 1, y + height - 1, OVERLAY_BG);
            int textWidth = font.width(overlay);
            g.drawString(font, overlay, x + (width - textWidth) / 2, y + (height - 8) / 2, EditorTheme.TEXT_MUTED, false);
        }
        if (isFocused()) {
            EditorTheme.wellOutline(g, x, y, width, height, EditorTheme.INPUT_BORDER_FOCUS);
        }
    }

    /**
     * 找出可视行，填进预分配的数组：首行二分，之后顺序往下推，逻辑行号跟着行首推进
     */
    private void prepareVisibleRows() {
        CharSequence text = doc.chars();
        int length = text.length();
        int rowCount = layout.rowCount();
        int first = Math.min(rowCount - 1, scrollPx / ROW_HEIGHT);
        int last = Math.min(rowCount - 1, (scrollPx + viewHeight() - 1) / ROW_HEIGHT);
        int count = Math.min(visStart.length, last - first + 1);
        int lineCount = doc.lineCount();
        int line = doc.lineOf(layout.rowStart(first));
        int nextLineStart = line + 1 < lineCount ? doc.lineStart(line + 1) : Integer.MAX_VALUE;
        for (int k = 0; k < count; k++) {
            int row = first + k;
            int start = layout.rowStart(row);
            while (start >= nextLineStart) {
                line++;
                nextLineStart = line + 1 < lineCount ? doc.lineStart(line + 1) : Integer.MAX_VALUE;
            }
            int end = layout.rowEnd(text, row);
            int flags = 0;
            if (start == doc.lineStart(line)) {
                flags |= FLAG_LINE_START;
            }
            if (end < length && text.charAt(end) == '\n') {
                flags |= FLAG_NEWLINE;
            }
            visStart[k] = start;
            visEnd[k] = end;
            visLine[k] = line;
            visFlags[k] = flags;
        }
        visFirst = first;
        visCount = count;
        frameFocused = isFocused();
        updateBracketMatch();
    }

    private void renderPasses() {
        GuiGraphics g = frameGraphics;
        if (g == null || visCount == 0) {
            return;
        }
        CharSequence text = doc.chars();
        int cursor = doc.cursor();
        int cursorLine = doc.lineOf(cursor);
        int length = text.length();
        int errorLine = errorOffset >= 0 && errorOffset <= length ? doc.lineOf(errorOffset) : -1;
        int bandLeft = getX() + 2 + gutterWidth;
        int bandRight = getX() + width - 2 - SCROLLBAR_WIDTH;

        // 第一趟：背景（当前行、错误行、查找命中、括号配对）
        for (int k = 0; k < visCount; k++) {
            int rowY = rowY(visFirst + k);
            if (visLine[k] == cursorLine) {
                g.fill(bandLeft, rowY, bandRight, rowY + ROW_HEIGHT, frameFocused ? CURRENT_LINE : CURRENT_LINE_UNFOCUSED);
            }
            if (visLine[k] == errorLine) {
                g.fill(bandLeft, rowY, bandRight, rowY + ROW_HEIGHT, ERROR_LINE);
            }
        }
        renderSearchHits(g, text);
        if (bracketAt >= 0) {
            int color = bracketBad ? BRACKET_BAD : BRACKET_MATCH;
            charBox(g, text, bracketAt, color);
            if (bracketMatch >= 0) {
                charBox(g, text, bracketMatch, color);
            }
        }

        // 第二趟：文字和行号
        boolean highlight = length <= HIGHLIGHT_LIMIT;
        int gutterRight = getX() + 1 + gutterWidth - GUTTER_PAD;
        for (int k = 0; k < visCount; k++) {
            int rowY = rowY(visFirst + k);
            List<TextRun> runs = runsFor(text, visStart[k], visEnd[k], visLine[k], highlight);
            if (!runs.isEmpty()) {
                g.drawRuns(font, runs, textLeft, rowY + 1, EditorTheme.TEXT, false);
            }
            if ((visFlags[k] & FLAG_LINE_START) != 0) {
                String number = Integer.toString(visLine[k] + 1);
                int color = visLine[k] == errorLine ? EditorTheme.DANGER
                        : visLine[k] == cursorLine ? LINE_NUMBER_CURRENT : LINE_NUMBER;
                g.drawString(font, number, gutterRight - digitsWidth(number), rowY + 1, color, false);
            }
        }

        // 第三趟：选区、错误下划线、光标
        int selectionStart = doc.selectionStart();
        int selectionEnd = doc.selectionEnd();
        if (selectionStart != selectionEnd) {
            int color = frameFocused ? SELECTION : SELECTION_UNFOCUSED;
            for (int k = 0; k < visCount; k++) {
                int start = visStart[k];
                int end = visEnd[k];
                boolean coversBreak = (visFlags[k] & FLAG_NEWLINE) != 0 && selectionStart <= end && selectionEnd > end;
                int a = Math.max(selectionStart, start);
                int b = Math.min(selectionEnd, end);
                if (a < b || coversBreak) {
                    int rowY = rowY(visFirst + k);
                    int x1 = textX(text, start, a);
                    int x2 = textX(text, start, Math.max(a, b)) + (coversBreak ? 4 : 0);
                    if (x2 > x1) {
                        g.fill(x1, rowY, x2, rowY + ROW_HEIGHT, color);
                    }
                }
            }
        }
        if (errorOffset >= 0 && errorOffset <= length) {
            renderErrorMark(g, text);
        }
        boolean caretOn = frameFocused && ((Ui.millis() - caretEpoch) / BLINK_MS) % 2 == 0;
        if (caretOn) {
            int k = visibleIndex(layout.rowOf(cursor));
            if (k >= 0) {
                int rowY = rowY(visFirst + k);
                int caretX = textX(text, visStart[k], cursor);
                g.fill(caretX, rowY, caretX + 1, rowY + ROW_HEIGHT, EditorTheme.ACCENT_HI);
            }
        }
    }

    private int rowY(int row) {
        return textTop + row * ROW_HEIGHT - scrollPx;
    }

    /**
     * 行首到 offset 的横坐标（屏幕坐标）
     */
    private int textX(@Nonnull CharSequence text, int rowStart, int offset) {
        return textLeft + Math.round(layout.measure(text, rowStart, offset));
    }

    /**
     * 显示行在可视数组里的下标，不可见返回 -1
     */
    private int visibleIndex(int row) {
        int k = row - visFirst;
        return k >= 0 && k < visCount ? k : -1;
    }

    private int digitsWidth(@Nonnull String number) {
        float sum = 0;
        for (int i = 0; i < number.length(); i++) {
            sum += widths.width(number.charAt(i));
        }
        return Math.round(sum);
    }

    /**
     * 给一个字符垫一块底色（括号配对用）
     */
    private void charBox(@Nonnull GuiGraphics g, @Nonnull CharSequence text, int offset, int color) {
        int k = visibleIndex(layout.rowOf(offset));
        if (k < 0 || offset >= visEnd[k]) {
            return;
        }
        int rowY = rowY(visFirst + k);
        g.fill(textX(text, visStart[k], offset), rowY, textX(text, visStart[k], offset + 1), rowY + ROW_HEIGHT, color);
    }

    /**
     * 查找命中：从可视范围第一行之前的命中开始，顺着行往下，指针只前进不回退
     */
    private void renderSearchHits(@Nonnull GuiGraphics g, @Nonnull CharSequence text) {
        TextSearch.Result result = search;
        if (result == null || result.count() == 0 || result.version() != doc.version()) {
            return;
        }
        int[] hits = result.hits();
        int count = result.count();
        int needle = result.length();
        int hit = result.lowerBound(Math.max(0, visStart[0] - needle + 1));
        for (int k = 0; k < visCount; k++) {
            int start = visStart[k];
            int end = visEnd[k];
            while (hit < count && hits[hit] + needle <= start) {
                hit++;
            }
            int rowY = rowY(visFirst + k);
            for (int j = hit; j < count && hits[j] < end; j++) {
                int a = Math.max(hits[j], start);
                int b = Math.min(hits[j] + needle, end);
                g.fill(textX(text, start, a), rowY, textX(text, start, b), rowY + ROW_HEIGHT, SEARCH_HIT);
            }
        }
    }

    /**
     * 解析错误：出错字符下面一道红线（在行尾或文末时画一小段），行号栏左边一道红条
     */
    private void renderErrorMark(@Nonnull GuiGraphics g, @Nonnull CharSequence text) {
        int k = visibleIndex(layout.rowOf(errorOffset));
        if (k < 0) {
            return;
        }
        int rowY = rowY(visFirst + k);
        int start = visStart[k];
        int end = visEnd[k];
        int x1 = textX(text, start, Math.min(errorOffset, end));
        int x2;
        if (errorOffset < end) {
            int next = errorOffset + 1;
            if (Character.isHighSurrogate(text.charAt(errorOffset)) && next < end) {
                next++;
            }
            x2 = textX(text, start, next);
        } else {
            x2 = x1 + 4;
        }
        x2 = Math.max(x2, x1 + 2);
        int underline = rowY + ROW_HEIGHT - 1;
        g.fill(x1, underline, x2, underline + 1, EditorTheme.DANGER);
        // 波浪：隔一个像素往上抬一格
        for (int px = x1; px < x2; px += 2) {
            g.fill(px, underline - 1, px + 1, underline, EditorTheme.DANGER);
        }
        g.fill(getX() + 1, rowY, getX() + 3, rowY + ROW_HEIGHT, EditorTheme.DANGER);
    }

    private void renderScrollbar(@Nonnull GuiGraphics g, int mouseX, int mouseY) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        int trackX = getX() + width - 1 - SCROLLBAR_WIDTH;
        int trackTop = getY() + 1;
        int trackHeight = height - 2;
        g.fill(trackX, trackTop, trackX + SCROLLBAR_WIDTH, trackTop + trackHeight, EditorTheme.ACCENT_FAINT);
        // 错误位置在滚动条上标一道红线，长文本里一眼能看到错在哪一段
        if (errorOffset >= 0 && errorOffset <= doc.length()) {
            int markY = trackTop + (int) ((long) trackHeight * layout.rowOf(errorOffset) / Math.max(1, layout.rowCount()));
            g.fill(trackX - 1, markY, trackX + SCROLLBAR_WIDTH, Math.min(trackTop + trackHeight, markY + 2), EditorTheme.DANGER);
        }
        int thumbHeight = thumbHeight();
        int thumbY = thumbY();
        boolean hot = drag == Drag.SCROLLBAR || (mouseX >= getX() + width - SCROLLBAR_HIT && mouseX < getX() + width
                && mouseY >= getY() && mouseY < getY() + height);
        g.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, hot ? EditorTheme.ACCENT_HOVER : EditorTheme.ACCENT);
        g.fill(trackX + 1, thumbY + 1, trackX + 2, thumbY + thumbHeight - 1, EditorTheme.ACCENT_HI);
    }

    private int thumbHeight() {
        int trackHeight = height - 2;
        int content = Math.max(1, layout.rowCount() * ROW_HEIGHT);
        return Math.max(10, Math.min(trackHeight, (int) ((long) trackHeight * viewHeight() / content)));
    }

    private int thumbY() {
        int max = maxScroll();
        int travel = height - 2 - thumbHeight();
        return getY() + 1 + (max <= 0 ? 0 : (int) ((long) travel * scrollPx / max));
    }

    // 分词缓存 / 文字段 / 括号配对

    /**
     * 一个逻辑行的词（按版本号缓存，文本一改全部作废——作废后只有可视的那几行会被重新分词）
     * <p>
     * 分词以整个逻辑行为单位，超长的行（压成一行的大 NBT）每打一个字都要整行重分，返回 null 不做语法高亮，只给 § 颜色码上色。
     *
     * @return 词，超长行返回 null
     */
    @Nullable
    private int[] tokensFor(int line) {
        if (doc.lineEnd(line) - doc.lineStart(line) > LONG_LINE_CHARS) {
            return null;
        }
        if (tokenCacheVersion != doc.version()) {
            tokenCache.clear();
            tokenCacheVersion = doc.version();
        }
        int[] tokens = tokenCache.get(line);
        if (tokens == null) {
            if (tokenCache.size() >= TOKEN_CACHE_LIMIT) {
                tokenCache.clear();
            }
            tokenScratch.clear();
            SnbtLexer.tokenizeLine(doc.chars(), doc.lineStart(line), doc.lineEnd(line), doc.lexStateAtLine(line), tokenScratch);
            tokens = tokenScratch.toIntArray();
            tokenCache.put(line, tokens);
        }
        return tokens;
    }

    /**
     * 一个显示行按词上色后的文字段（按版本号和行起点缓存；排版变了、行终点对不上就重拼）
     */
    @Nonnull
    private List<TextRun> runsFor(@Nonnull CharSequence text, int start, int end, int line, boolean highlight) {
        if (rowCacheVersion != doc.version() || rowCacheHighlight != highlight) {
            rowCache.clear();
            rowCacheVersion = doc.version();
            rowCacheHighlight = highlight;
        }
        RowRuns cached = rowCache.get(start);
        if (cached != null && cached.end == end) {
            return cached.runs;
        }
        if (rowCache.size() >= ROW_CACHE_LIMIT) {
            rowCache.clear();
        }
        List<TextRun> runs = buildRuns(text, start, end, highlight ? tokensFor(line) : null);
        rowCache.put(start, new RowRuns(end, runs));
        return runs;
    }

    /**
     * 把 [from, to) 按颜色切成几段
     * <p>
     * 交给字体的是 TextRun：画的时候 § 按普通字符画出来（字体画带 § 的字符串会把它当格式码吃掉，这里要原样显示）。
     * 落单的 surrogate 码元换成 U+FFFD，和量宽时一致。
     *
     * @param tokens 词；null = 不分词，只给 § 颜色码本身上色
     */
    @Nonnull
    private List<TextRun> buildRuns(@Nonnull CharSequence text, int from, int to, @Nullable int[] tokens) {
        if (from >= to) {
            return Collections.emptyList();
        }
        List<TextRun> runs = new ArrayList<>();
        StringBuilder pending = runText;
        pending.setLength(0);
        Integer pendingColor = null;
        int t = tokens == null ? 0 : firstToken(tokens, from);
        boolean afterSection = false;
        int length = text.length();
        int i = from;
        while (i < to) {
            char c = text.charAt(i);
            int size = 1;
            boolean pair = Character.isHighSurrogate(c) && i + 1 < to && Character.isLowSurrogate(text.charAt(i + 1));
            if (pair) {
                size = 2;
            }
            Integer color;
            if (tokens != null) {
                while (t < tokens.length && tokens[t + 1] <= i) {
                    t += 3;
                }
                color = t < tokens.length && tokens[t] <= i ? colorOfToken(tokens[t + 2]) : null;
            } else {
                // 大文本不分词：只给 § 颜色码本身上色
                color = null;
                if (afterSection && ColorCodes.isLegalCode(c)) {
                    color = CODE_COLORS[codeIndex(c)];
                } else if (c == ColorCodes.SECTION && i + 1 < length && ColorCodes.isLegalCode(text.charAt(i + 1))) {
                    color = SECTION_COLORS[codeIndex(text.charAt(i + 1))];
                }
                afterSection = c == ColorCodes.SECTION;
            }
            if (pending.length() > 0 && color != pendingColor) {
                runs.add(new TextRun(pending.toString(), pendingColor, 0));
                pending.setLength(0);
            }
            pendingColor = color;
            if (pair) {
                pending.append(c).append(text.charAt(i + 1));
            } else {
                pending.append(Character.isSurrogate(c) ? REPLACEMENT : c);
            }
            i += size;
        }
        if (pending.length() > 0) {
            runs.add(new TextRun(pending.toString(), pendingColor, 0));
        }
        return runs;
    }

    private static int codeIndex(char code) {
        int index = ColorCodes.colorIndex(code);
        return index >= 0 ? index + 1 : ColorCodes.isReset(code) ? 0 : 17;
    }

    @Nonnull
    private static Integer colorOfToken(int packed) {
        int kind = SnbtLexer.kindOf(packed);
        int arg = SnbtLexer.argOf(packed);
        switch (kind) {
            case SnbtLexer.TINTED:
                return TINT_COLORS[arg];
            case SnbtLexer.SECTION:
                return SECTION_COLORS[arg + 1];
            case SnbtLexer.SECTION_CODE:
                return CODE_COLORS[arg + 1];
            default:
                return KIND_COLORS[kind];
        }
    }

    /**
     * 第一个终点 > offset 的词（三个 int 一组，二分）
     */
    private static int firstToken(@Nonnull int[] tokens, int offset) {
        int low = 0;
        int high = tokens.length / 3;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (tokens[mid * 3 + 1] <= offset) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low * 3;
    }

    /**
     * 光标贴着括号时找另一半（光标右边的优先）；按版本号 + 光标位置缓存，不变就不重算
     */
    private void updateBracketMatch() {
        int cursor = doc.cursor();
        if (bracketVersion == doc.version() && bracketCursor == cursor) {
            return;
        }
        bracketVersion = doc.version();
        bracketCursor = cursor;
        bracketAt = -1;
        bracketMatch = -1;
        bracketBad = false;
        if (!isHighlighting()) {
            // 大文本和语法高亮一样不做：光标停在根括号上时要从头扫到尾（2MB 约 10 毫秒），
            // 而且文本每改一次、光标每挪一次都可能再扫一遍
            return;
        }
        int at = BracketMatcher.isStructural(doc, cursor) ? cursor
                : cursor > 0 && BracketMatcher.isStructural(doc, cursor - 1) ? cursor - 1 : -1;
        if (at < 0) {
            return;
        }
        bracketAt = at;
        bracketMatch = BracketMatcher.findMatch(doc, at);
        bracketBad = bracketMatch < 0 || BracketMatcher.partner(doc.charAt(at)) != doc.charAt(bracketMatch);
    }

    // 鼠标

    private boolean within(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getX() + width && mouseY >= getY() && mouseY < getY() + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !active || button != 0 || !within(mouseX, mouseY)) {
            return false;
        }
        ensureLayout();
        long now = Ui.millis();
        caretEpoch = now;
        lastAutoScroll = now;
        preferredX = -1;
        // 点在上下边框那两个像素里时按首尾可见行算，不然会选到框外半行（拖动时才需要越界，见 continueDrag）
        double clickY = Math.max(textTop, Math.min(textBottom - 1, mouseY));

        if (maxScroll() > 0 && mouseX >= getX() + width - SCROLLBAR_HIT) {
            int thumbY = thumbY();
            int thumbHeight = thumbHeight();
            if (mouseY >= thumbY && mouseY < thumbY + thumbHeight) {
                dragGrab = mouseY - thumbY;
            } else {
                // 点在滑轨空白处：滑块中心跳到鼠标位置，接着可以直接拖
                dragGrab = thumbHeight / 2.0;
                scrollToThumb(mouseY - dragGrab);
            }
            drag = Drag.SCROLLBAR;
            return true;
        }

        if (mouseX < getX() + 2 + gutterWidth) {
            int line = doc.lineOf(layout.rowStart(rowAt(clickY)));
            int anchorLine = Screen.hasShiftDown() ? doc.lineOf(doc.anchor()) : line;
            SnbtEditing.selectLines(doc, anchorLine, line);
            dragLine = anchorLine;
            drag = Drag.GUTTER;
            return true;
        }

        boolean shift = Screen.hasShiftDown();
        // 只放光标时落在软折行末尾会画到下一行开头，往回收一个字符；Shift 扩选要能选到行末，不收
        int offset = shift ? offsetAt(mouseX, clickY) : caretAt(mouseX, clickY);
        if (now - lastClickTime < MULTI_CLICK_MS && Math.abs(offset - lastClickOffset) <= 1) {
            clickCount = clickCount % 3 + 1;
        } else {
            clickCount = 1;
        }
        lastClickTime = now;
        lastClickOffset = offset;
        if (clickCount == 2) {
            int[] word = SnbtEditing.wordAt(doc.chars(), offset);
            doc.setSelection(word[0], word[1]);
            drag = Drag.NONE;
        } else if (clickCount == 3) {
            int line = doc.lineOf(offset);
            SnbtEditing.selectLines(doc, line, line);
            drag = Drag.NONE;
        } else {
            // Shift+点 = 从原来的固定端扩选；普通点 = 光标移过去、选区清掉
            doc.moveCursor(offset, shift);
            drag = Drag.TEXT;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // 界面会把"拖动"转给当前焦点；点了不抢焦点的按钮之后拖鼠标也会转到这里，所以只认自己发起的拖动
        if (button != 0 || drag == Drag.NONE) {
            return false;
        }
        if (!Mouse.isButtonDown(0)) {
            drag = Drag.NONE;
            return false;
        }
        ensureLayout();
        applyDrag(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && drag != Drag.NONE) {
            drag = Drag.NONE;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible) {
            return false;
        }
        ensureLayout();
        setScroll(scrollPx - (int) Math.round(delta * WHEEL_ROWS * ROW_HEIGHT));
        if (drag == Drag.TEXT || drag == Drag.GUTTER) {
            applyDrag(mouseX, mouseY);
        }
        return true;
    }

    private void applyDrag(double mouseX, double mouseY) {
        caretEpoch = Ui.millis();
        switch (drag) {
            case SCROLLBAR:
                scrollToThumb(mouseY - dragGrab);
                break;
            case TEXT:
                doc.moveCursor(offsetAt(mouseX, mouseY), true);
                break;
            case GUTTER:
                SnbtEditing.selectLines(doc, dragLine, doc.lineOf(layout.rowStart(rowAt(mouseY))));
                break;
            default:
                break;
        }
    }

    /**
     * 每帧调用：按住拖到框外时自动滚动并继续扩选；鼠标在框外松开时界面不会通知这里，靠查按键状态收尾
     */
    private void continueDrag(int mouseX, int mouseY) {
        if (!Mouse.isButtonDown(0)) {
            drag = Drag.NONE;
            return;
        }
        long now = Ui.millis();
        long elapsed = Math.min(100L, now - lastAutoScroll);
        lastAutoScroll = now;
        if (drag == Drag.SCROLLBAR) {
            return;
        }
        int outside = mouseY < textTop ? mouseY - textTop : mouseY > textBottom ? mouseY - textBottom : 0;
        if (outside == 0) {
            return;
        }
        // 离框越远滚得越快
        int speed = 120 + 12 * Math.abs(outside);
        int amount = (int) Math.max(1, speed * elapsed / 1000);
        setScroll(scrollPx + (outside < 0 ? -amount : amount));
        applyDrag(mouseX, mouseY);
    }

    private void scrollToThumb(double thumbTop) {
        int travel = height - 2 - thumbHeight();
        if (travel <= 0) {
            return;
        }
        double fraction = (thumbTop - (getY() + 1)) / travel;
        setScroll((int) Math.round(fraction * maxScroll()));
    }

    /**
     * 纵坐标对应的显示行（框外的夹到首尾）
     */
    private int rowAt(double mouseY) {
        int row = (int) Math.floor((mouseY - textTop + scrollPx) / ROW_HEIGHT);
        return Math.max(0, Math.min(layout.rowCount() - 1, row));
    }

    /**
     * 屏幕坐标对应的文本下标（拖选用：软折行的行末也能选到）
     */
    private int offsetAt(double mouseX, double mouseY) {
        int row = rowAt(mouseY);
        CharSequence text = doc.chars();
        return layout.offsetAt(text, layout.rowStart(row), layout.rowEnd(text, row), mouseX - textLeft);
    }

    /**
     * 屏幕坐标对应的光标位置（放光标用：不会落在软折行末尾，见 {@link WrapLayout#caretOffsetAt}）
     */
    private int caretAt(double mouseX, double mouseY) {
        return layout.caretOffsetAt(doc.chars(), rowAt(mouseY), mouseX - textLeft);
    }

    // 键盘

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || !isFocused()) {
            return false;
        }
        boolean ctrl = Screen.hasControlDown();
        boolean shift = Screen.hasShiftDown();
        boolean alt = Screen.hasAltDown();
        caretEpoch = Ui.millis();
        ensureLayout();

        if (ctrl && alt && keyCode >= Keys.KEY_A && keyCode <= Keys.KEY_Z) {
            // Windows 上 AltGr 等于左 Ctrl + 右 Alt，不少键盘布局靠它打字（波兰语 AltGr+Z = ż、AltGr+S = ś），
            // 字符随后从 charTyped 进来；按键本身吃掉，免得被当成 Ctrl 组合键落到界面的 Ctrl+S 保存、Ctrl+Z 整体撤销上
            return true;
        }
        if (ctrl && !alt) {
            switch (keyCode) {
                case Keys.KEY_Z:
                    if (!readOnly) {
                        if (shift) {
                            doc.redo();
                        } else {
                            doc.undo();
                        }
                    }
                    return true;
                case Keys.KEY_Y:
                    if (!readOnly) {
                        doc.redo();
                    }
                    return true;
                case Keys.KEY_A:
                    doc.selectAll();
                    return true;
                case Keys.KEY_C:
                    copySelection();
                    return true;
                case Keys.KEY_X:
                    copySelection();
                    if (!readOnly && doc.hasSelection()) {
                        SnbtEditing.deleteSelection(doc, TextDocument.EditKind.COMMAND);
                    }
                    return true;
                case Keys.KEY_V:
                    if (!readOnly) {
                        SnbtEditing.paste(doc, Ui.getClipboard());
                    }
                    return true;
                case Keys.KEY_D:
                    if (!readOnly) {
                        SnbtEditing.duplicateLines(doc);
                    }
                    return true;
                default:
                    // 其余 Ctrl 组合键往下走（方向键等），或者留给页面 / 界面（Ctrl+S、Ctrl+F……）
                    break;
            }
        }

        switch (keyCode) {
            case Keys.KEY_ENTER:
            case Keys.KEY_KP_ENTER:
                if (ctrl) {
                    // Ctrl+Enter = 应用，页面处理
                    return false;
                }
                if (!readOnly) {
                    SnbtEditing.newline(doc);
                }
                return true;
            case Keys.KEY_BACKSPACE:
                if (!readOnly) {
                    SnbtEditing.backspace(doc, ctrl);
                }
                return true;
            case Keys.KEY_DELETE:
                if (shift && !ctrl) {
                    // Shift+Delete = 剪切（Windows 的老习惯）
                    copySelection();
                    if (!readOnly && doc.hasSelection()) {
                        SnbtEditing.deleteSelection(doc, TextDocument.EditKind.COMMAND);
                    }
                } else if (!readOnly) {
                    SnbtEditing.deleteForward(doc, ctrl);
                }
                return true;
            case Keys.KEY_TAB:
                if (ctrl) {
                    // Ctrl+Tab 切页，界面处理
                    return false;
                }
                if (!readOnly) {
                    if (shift) {
                        SnbtEditing.outdent(doc);
                    } else {
                        SnbtEditing.indent(doc);
                    }
                }
                return true;
            case Keys.KEY_LEFT:
                moveHorizontal(-1, ctrl, shift);
                return true;
            case Keys.KEY_RIGHT:
                moveHorizontal(1, ctrl, shift);
                return true;
            case Keys.KEY_UP:
            case Keys.KEY_DOWN: {
                boolean up = keyCode == Keys.KEY_UP;
                if (alt && !ctrl) {
                    if (!readOnly) {
                        SnbtEditing.moveLines(doc, up);
                    }
                } else if (ctrl) {
                    setScroll(scrollPx + (up ? -ROW_HEIGHT : ROW_HEIGHT));
                } else {
                    moveVertical(up ? -1 : 1, shift);
                }
                return true;
            }
            case Keys.KEY_PAGE_UP:
            case Keys.KEY_PAGE_DOWN: {
                // 视图和光标一起翻一页，光标留在屏幕上同一个位置
                int rows = keyCode == Keys.KEY_PAGE_UP ? -visibleRows() : visibleRows();
                setScroll(scrollPx + rows * ROW_HEIGHT);
                moveVertical(rows, shift);
                return true;
            }
            case Keys.KEY_HOME:
                moveTo(ctrl ? 0 : SnbtEditing.smartHome(doc, doc.cursor()), shift);
                return true;
            case Keys.KEY_END:
                moveTo(ctrl ? doc.length() : doc.lineEnd(doc.lineOf(doc.cursor())), shift);
                return true;
            case Keys.KEY_INSERT:
                // Shift+Insert 粘贴、Ctrl+Insert 复制；单按 Insert 不做改写模式，吃掉
                if (shift && !ctrl) {
                    if (!readOnly) {
                        SnbtEditing.paste(doc, Ui.getClipboard());
                    }
                } else if (ctrl && !shift) {
                    copySelection();
                }
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible || !isFocused() || !SnbtEditing.isTypeable(codePoint)) {
            return false;
        }
        if (!readOnly) {
            ensureLayout();
            SnbtEditing.typeChar(doc, codePoint);
            preferredX = -1;
            caretEpoch = Ui.millis();
            // 键入右括号 / 闭引号时可能只是挪了光标，文本没变，不会触发改动回调，这里补一次滚动跟随
            revealOffset(doc.cursor(), false);
        }
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            drag = Drag.NONE;
            doc.sealHistory();
        } else {
            caretEpoch = Ui.millis();
        }
    }

    private void moveTo(int pos, boolean extend) {
        doc.moveCursor(pos, extend);
        preferredX = -1;
        revealOffset(pos, false);
    }

    private void moveHorizontal(int direction, boolean word, boolean extend) {
        CharSequence text = doc.chars();
        int length = text.length();
        if (doc.hasSelection() && !extend && !word) {
            // 有选区时不带 Shift 按左右：光标落到选区对应的一端，选区消掉
            moveTo(direction < 0 ? doc.selectionStart() : doc.selectionEnd(), false);
            return;
        }
        int pos = doc.cursor();
        int target;
        if (word) {
            target = direction < 0 ? SnbtEditing.previousWordBoundary(text, pos) : SnbtEditing.nextWordBoundary(text, pos);
        } else {
            target = Math.max(0, Math.min(length, pos + direction));
            // 两个码元组成的字符（surrogate pair）当一个字符走
            if (target > 0 && target < length && Character.isLowSurrogate(text.charAt(target))
                    && Character.isHighSurrogate(text.charAt(target - 1))) {
                target += direction;
            }
        }
        moveTo(target, extend);
    }

    /**
     * 上下移动：按显示行走，横坐标记住第一次按下时的位置（经过短行再回到长行时列不丢）
     */
    private void moveVertical(int rows, boolean extend) {
        CharSequence text = doc.chars();
        int cursor = doc.cursor();
        int row = layout.rowOf(cursor);
        if (preferredX < 0) {
            preferredX = layout.measure(text, layout.rowStart(row), cursor);
        }
        int target = row + rows;
        int pos;
        if (target < 0) {
            pos = 0;
        } else if (target >= layout.rowCount()) {
            pos = text.length();
        } else {
            // 不落在软折行末尾：那里按约定画在下一行开头，再按一次下方向键会越过一行
            pos = layout.caretOffsetAt(text, target, preferredX);
        }
        float keep = preferredX;
        doc.moveCursor(pos, extend);
        preferredX = keep;
        revealOffset(pos, false);
    }

    private void copySelection() {
        if (doc.hasSelection()) {
            Ui.setClipboard(doc.selectedText());
        }
    }

    // 配色工具

    /**
     * § 颜色在深色底上看得清：太暗的颜色（黑、深蓝、深灰……）往白色提亮
     */
    private static int readable(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        float luminance = (0.299F * r + 0.587F * g + 0.114F * b) / 255F;
        float lift = Math.max(0F, Math.min(0.45F, (0.5F - luminance) * 0.8F));
        return mix(rgb, 0xFFFFFF, lift);
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    // 字符宽度

    /**
     * 按码点查字体宽度并缓存（基本平面用数组）
     * <p>
     * 宽度取自 {@link Font#width(char, boolean)}：1.12.2 的字体渲染器逐字画、每个字前进的宽度取整，和这里查到的一致；
     * § 也按字形宽度算（字体的字符串接口会把它当格式码）。
     * 1.12.2 的渲染器不认 surrogate pair，两个码元各画各的，所以补充平面的字符宽度是两个码元的宽度之和。
     */
    private static final class FontWidths implements CharWidths {

        private final Font font;
        private final float[] bmp = new float[65536];

        FontWidths(@Nonnull Font font) {
            this.font = font;
            Arrays.fill(bmp, -1F);
        }

        @Override
        public float width(int codePoint) {
            if (codePoint >= 0 && codePoint < bmp.length) {
                return charWidth((char) codePoint);
            }
            if (Character.isValidCodePoint(codePoint)) {
                return charWidth(Character.highSurrogate(codePoint)) + charWidth(Character.lowSurrogate(codePoint));
            }
            return charWidth(REPLACEMENT);
        }

        private float charWidth(char c) {
            float width = bmp[c];
            if (width < 0) {
                width = font.width(c, false);
                bmp[c] = width;
            }
            return width;
        }
    }
}
