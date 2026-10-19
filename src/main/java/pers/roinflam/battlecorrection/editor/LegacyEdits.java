package pers.roinflam.battlecorrection.editor;

import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 对编辑文本（{@code &} 码写法）做的整段改写：渐变、清除代码、拆行、并行
 * <p>
 * 都是纯字符串运算，界面只管把结果放回输入框。共同的讲究：
 * <ul>
 *   <li>选区按记号对齐：选区边界落在 {@code &6} 中间时，把整个代码算进来，不会拆出半个代码</li>
 *   <li>改完选区后，选区后面的文字保持原来的样式：选区里删掉的代码可能影响后面，必要时补一段"恢复代码"</li>
 *   <li>输出经过 {@link LegacyText.Writer}，字面的 & 该转义的转义</li>
 * </ul>
 * 1.12.2 的 {@code &r} 回到的是白色，不是这一行的基础样式（原版 Lore 是紫色斜体），所以要回到行首样式的地方
 * 写的是 {@link LegacyComponents#restoreCode}，需要调用方给出行首的样子（{@link LegacyComponents#start}）。
 */
public final class LegacyEdits {

    /**
     * 下划线、删除线：带着这两种格式的空格是看得见的
     */
    private static final int VISIBLE_ON_SPACE = LegacyComponents.formatBit(TextFormatting.UNDERLINE)
            | LegacyComponents.formatBit(TextFormatting.STRIKETHROUGH);

    /**
     * 五种格式的位全集：调用方传进来的格式掩码先和它取交集，多余的位不能混进"格式变没变"的比较里
     */
    private static final int ALL_FORMATS = LegacyComponents.formatBit(TextFormatting.BOLD)
            | LegacyComponents.formatBit(TextFormatting.ITALIC)
            | VISIBLE_ON_SPACE
            | LegacyComponents.formatBit(TextFormatting.OBFUSCATED);

    /**
     * 改写结果
     */
    public static final class Result {
        @Nonnull
        private final String text;
        private final int start;
        private final int end;

        /**
         * @param text  新的完整文本
         * @param start 改写过的那一段在新文本里的起点（放回选区用）
         * @param end   终点
         */
        public Result(@Nonnull String text, int start, int end) {
            this.text = text;
            this.start = start;
            this.end = end;
        }

        @Nonnull
        public String text() {
            return text;
        }

        public int start() {
            return start;
        }

        public int end() {
            return end;
        }
    }

    /**
     * 代码状态：颜色从哪来（null = 行首的基础样式，否则是最近那个颜色 / 重置 / 认不出的代码的原文）+ 之后打开的格式
     */
    private static final class CodeState {
        @Nullable
        String colorCode;
        int colorRgb;
        int added;

        void apply(@Nonnull LegacyText.Token token, @Nonnull String text) {
            switch (token.type()) {
                case COLOR:
                case RESET:
                case CUSTOM:
                    colorCode = text.substring(token.start(), token.end());
                    colorRgb = token.rgb();
                    added = 0;
                    break;
                case FORMAT:
                    added |= LegacyComponents.formatBit(token.format());
                    break;
                default:
                    break;
            }
        }

        int rgb(@Nonnull LegacyComponents.Look start) {
            return colorCode == null ? start.rgb() : colorRgb;
        }

        int mask(@Nonnull LegacyComponents.Look start) {
            return colorCode == null ? start.mask() | added : added;
        }

        boolean isBase(@Nonnull LegacyComponents.Look start) {
            return rgb(start) == start.rgb() && mask(start) == start.mask();
        }

        /**
         * 从任意状态回到这个状态的代码
         */
        @Nonnull
        String restore(@Nonnull LegacyComponents.Look start) {
            if (colorCode == null) {
                return LegacyComponents.restoreCode(start) + LegacyComponents.formatCodes(added & ~start.mask());
            }
            return colorCode + LegacyComponents.formatCodes(added);
        }

        /**
         * 一行开头（本来就在基础样式上）接上这个状态的代码
         */
        @Nonnull
        String fromBase() {
            return (colorCode == null ? "" : colorCode) + LegacyComponents.formatCodes(added);
        }
    }

    private LegacyEdits() {
    }

    /**
     * 两色渐变：颜色从 from 线性过渡到 to，等于两个色标的 {@link #gradient(String, int, int, int[], int, LegacyComponents.Look)}
     *
     * @param text     编辑文本
     * @param selStart 选区起点
     * @param selEnd   选区终点；和起点相同 = 没选，作用于全文
     * @param from     起点颜色
     * @param to       终点颜色
     * @param start    这一行行首的样子
     * @return 改写结果
     */
    @Nonnull
    public static Result gradient(@Nonnull String text, int selStart, int selEnd, int from, int to,
                                  @Nonnull LegacyComponents.Look start) {
        return gradient(text, selStart, selEnd, new int[]{from, to}, 0, start);
    }

    /**
     * 渐变：选区里每个看得见的字取渐变上对应位置的颜色，换成最接近的原版颜色后插颜色码
     * <p>
     * 颜色沿着等距分布的色标分段线性插值，再换成 16 色之一（{@link ColorMath#sampleLegacy}），所以相邻几个字常常落到同一个颜色上，
     * 这时只写一次代码。1.12.2 的文字没有十六进制颜色，这样做出来的就是原版里常见的"彩虹字"。
     * <p>
     * 选区里原有的颜色码去掉（反正要被渐变盖掉），格式码（{@code &l} 等）会被颜色码清掉，所以逐字补在颜色码后面；
     * 选区开始前已经打开的格式也算上。空白字符不占渐变的步数，沿用前一个字的颜色（带下划线 / 删除线时按位置取色）。
     * 认不出的 § 代码原样留在原位，它后面的字重新写一次颜色码。
     * <p>
     * 第一个字是第一个色标的颜色、最后一个字是最后一个色标的颜色；只有一个色标时整段是同一个颜色（调色盘"给选中的字换颜色"就是这么用的）。
     * formats 是额外要打开的格式（调色盘的 B / I / U / S 开关），和选区里原有的格式一起逐字补在颜色码后面；
     * 选区后面的文字照旧恢复原来的样式，不会被这些格式波及。
     *
     * @param text     编辑文本
     * @param selStart 选区起点
     * @param selEnd   选区终点；和起点相同 = 没选，作用于全文
     * @param stops    色标（至少一个），等距分布
     * @param formats  额外打开的格式，位掩码同 {@link LegacyComponents#formatBit}；0 = 不加
     * @param start    这一行行首的样子（选区后面的字原来跟着基础样式时，靠它写恢复代码）
     * @return 改写结果
     */
    @Nonnull
    public static Result gradient(@Nonnull String text, int selStart, int selEnd, @Nonnull int[] stops, int formats,
                                  @Nonnull LegacyComponents.Look start) {
        if (stops.length == 0) {
            throw new IllegalArgumentException("gradient needs at least one colour stop");
        }
        formats &= ALL_FORMATS;
        List<LegacyText.Token> tokens = LegacyText.tokenize(text);
        int[] range = snap(tokens, selStart, selEnd);
        int first = range[0];
        int last = range[1];

        LegacyText.Writer writer = new LegacyText.Writer(text.length() * 3 + 16);
        CodeState state = new CodeState();
        for (int i = 0; i < first; i++) {
            emit(writer, tokens.get(i), text);
            state.apply(tokens.get(i), text);
        }
        int active = state.added | formats;

        int steps = 0;
        for (int i = first; i < last; i++) {
            LegacyText.Token token = tokens.get(i);
            if (token.visible() && !Character.isWhitespace(token.ch()) && !pairedLow(tokens, i)) {
                steps++;
            }
        }

        int startMark = writer.mark();
        int step = 0;
        int lastRgb = -1;
        int lastActive = -1;
        boolean wroteText = false;
        for (int i = first; i < last; i++) {
            LegacyText.Token token = tokens.get(i);
            state.apply(token, text);
            switch (token.type()) {
                case COLOR:
                case RESET:
                    active = formats;
                    break;
                case FORMAT:
                    active |= LegacyComponents.formatBit(token.format());
                    break;
                case CUSTOM:
                    // 它会把颜色和格式都清掉：原样留着，后面的字重新写颜色码
                    writer.raw(text.substring(token.start(), token.end()));
                    active = formats;
                    lastRgb = -1;
                    break;
                case STRAY:
                    writer.raw(text.substring(token.start(), token.end()));
                    break;
                case TEXT:
                case ESCAPE: {
                    char c = token.ch();
                    if (i > first && pairedLow(tokens, i)) {
                        // surrogate 对的后一半：和前一半是同一个字，中间插代码会把它拆成两个乱码
                        writer.literal(c);
                        continue;
                    }
                    int rgb;
                    if (Character.isWhitespace(c)) {
                        // 空白不占渐变的步数：沿用前一个字的颜色（没有就用起点色），只在格式变了时才写代码。
                        // 空格的粗体会改变宽度、下划线和删除线看得见，所以格式必须对
                        rgb = lastRgb >= 0 && (active & VISIBLE_ON_SPACE) == 0 ? lastRgb
                                : ColorMath.sampleLegacy(stops, steps <= 1 ? 0.0 : (double) Math.min(step, steps - 1) / (steps - 1));
                    } else {
                        rgb = ColorMath.sampleLegacy(stops, steps <= 1 ? 0.0 : (double) step / (steps - 1));
                        step++;
                    }
                    if (rgb != lastRgb || active != lastActive) {
                        writer.code(LegacyText.colorCode(rgb) + LegacyComponents.formatCodes(active));
                        lastRgb = rgb;
                        lastActive = active;
                    }
                    writer.literal(c);
                    wroteText = true;
                    break;
                }
                default:
                    break;
            }
        }
        int endMark = writer.mark();
        // 选区后面的文字原来是什么样式就恢复成什么样式
        if (wroteText) {
            appendRestore(writer, tokens, last, state, start);
        } else if (last < tokens.size()) {
            CodeState before = stateBefore(tokens, first, text);
            if (!sameState(before, state, start)) {
                appendRestore(writer, tokens, last, state, start);
            }
        }
        for (int i = last; i < tokens.size(); i++) {
            emit(writer, tokens.get(i), text);
        }
        int[] marks = {startMark, endMark};
        String result = writer.build("", marks);
        return new Result(result, marks[0], marks[1]);
    }

    /**
     * 清除代码：去掉选区里所有颜色 / 格式 / 重置代码，选中的字回到基础样式
     * <p>
     * 选区前面的代码仍然会影响到选区，所以选区开始时不在基础样式上就补一段回到行首样式的代码
     * （§r 基础样式是 {@code &r}，原版 Lore 是 {@code &5&o}）；选区后面的文字保持原样式。
     *
     * @param text     编辑文本
     * @param selStart 选区起点
     * @param selEnd   选区终点；和起点相同 = 没选，作用于全文
     * @param start    这一行行首的样子
     * @return 改写结果
     */
    @Nonnull
    public static Result stripCodes(@Nonnull String text, int selStart, int selEnd, @Nonnull LegacyComponents.Look start) {
        List<LegacyText.Token> tokens = LegacyText.tokenize(text);
        int[] range = snap(tokens, selStart, selEnd);
        int first = range[0];
        int last = range[1];

        LegacyText.Writer writer = new LegacyText.Writer(text.length() + 8);
        CodeState state = new CodeState();
        for (int i = 0; i < first; i++) {
            emit(writer, tokens.get(i), text);
            state.apply(tokens.get(i), text);
        }
        CodeState atStart = stateBefore(tokens, first, text);
        int startMark = writer.mark();
        boolean resetPending = !atStart.isBase(start);
        boolean wroteText = false;
        for (int i = first; i < last; i++) {
            LegacyText.Token token = tokens.get(i);
            state.apply(token, text);
            if (token.visible()) {
                if (resetPending) {
                    writer.code(LegacyComponents.restoreCode(start));
                    resetPending = false;
                }
                writer.literal(token.ch());
                wroteText = true;
            }
        }
        int endMark = writer.mark();
        // 写过字的话现在处于基础样式；一个字都没有的话代码全删了，状态停在选区开始前
        CodeState now = wroteText ? new CodeState() : atStart;
        if (!sameState(now, state, start)) {
            appendRestore(writer, tokens, last, state, start);
        }
        for (int i = last; i < tokens.size(); i++) {
            emit(writer, tokens.get(i), text);
        }
        int[] marks = {startMark, endMark};
        String result = writer.build("", marks);
        return new Result(result, marks[0], marks[1]);
    }

    /**
     * 取一段示例文字（调色盘的渐变预览用）：选区（没选中就是全文）里看得见的字，格式码照留，颜色码去掉
     * <p>
     * 选区开始前已经打开的格式补在最前面；选区里的颜色码换成 {@code &r}（当时有格式开着才写）：颜色码本来会把前面的格式关掉，
     * 直接删掉的话后面的字会错带上格式，换成 {@code &r} 效果一样又不带颜色。拿结果整段做 {@link #gradient}，
     * 每个字的格式和对原文选区做渐变时一致（字数被截短时，颜色按截短后的字数重新分布）。
     * 认不出的代码和落单的 § 不要。surrogate 对算一个字，不会被截成半个。
     *
     * @param text     编辑文本
     * @param selStart 选区起点
     * @param selEnd   选区终点；和起点相同 = 没选，取全文
     * @param maxChars 最多取几个字（按 Unicode 码点算）
     * @return 编辑文本；选区里一个字都没有时返回空串
     */
    @Nonnull
    public static String sample(@Nonnull String text, int selStart, int selEnd, int maxChars) {
        List<LegacyText.Token> tokens = LegacyText.tokenize(text);
        int[] range = snap(tokens, selStart, selEnd);
        CodeState before = stateBefore(tokens, range[0], text);
        LegacyText.Writer writer = new LegacyText.Writer(Math.min(text.length(), maxChars * 4) + 16);
        writer.code(LegacyComponents.formatCodes(before.added));
        // 示例里没有颜色，&r 只用来关格式：当前没有打开的格式就不用写
        int open = before.added;
        int count = 0;
        boolean any = false;
        for (int i = range[0]; i < range[1]; i++) {
            LegacyText.Token token = tokens.get(i);
            switch (token.type()) {
                case COLOR:
                case RESET:
                case CUSTOM:
                    if (open != 0) {
                        writer.code("&r");
                        open = 0;
                    }
                    break;
                case FORMAT:
                    writer.raw(text.substring(token.start(), token.end()));
                    open |= LegacyComponents.formatBit(token.format());
                    break;
                case TEXT:
                case ESCAPE:
                    if (i > range[0] && pairedLow(tokens, i)) {
                        writer.literal(token.ch());
                        continue;
                    }
                    if (count >= maxChars) {
                        return writer.build();
                    }
                    writer.literal(token.ch());
                    count++;
                    any = true;
                    break;
                default:
                    break;
            }
        }
        return any ? writer.build() : "";
    }

    /**
     * 拆行结果
     */
    public static final class Split {
        @Nonnull
        private final String left;
        @Nonnull
        private final String right;
        private final int caret;

        /**
         * @param left  前半行
         * @param right 后半行（开头补了光标处的样式代码）
         * @param caret 后半行里光标该放的位置（补的代码后面，接着打字就是原来的样式）
         */
        public Split(@Nonnull String left, @Nonnull String right, int caret) {
            this.left = left;
            this.right = right;
            this.caret = caret;
        }

        @Nonnull
        public String left() {
            return left;
        }

        @Nonnull
        public String right() {
            return right;
        }

        public int caret() {
            return caret;
        }
    }

    /**
     * 在光标处把一行拆成两行：后半行开头补上光标处的样式，拆完两行显示得和原来一样
     * <p>
     * 两行用的是同一个基础样式，后半行从行首样式出发，补上的代码和原文里光标前面那一串一样就行，不用知道基础样式是什么。
     *
     * @param text   编辑文本
     * @param cursor 光标位置（落在代码或 surrogate 对中间时往后挪到它结束）
     * @return 拆行结果
     */
    @Nonnull
    public static Split split(@Nonnull String text, int cursor) {
        List<LegacyText.Token> tokens = LegacyText.tokenize(text);
        int at = text.length();
        int index = tokens.size();
        for (int i = 0; i < tokens.size(); i++) {
            LegacyText.Token token = tokens.get(i);
            if (token.start() >= cursor && !pairedLow(tokens, i)) {
                at = token.start();
                index = i;
                break;
            }
        }
        CodeState state = new CodeState();
        for (int i = 0; i < index; i++) {
            state.apply(tokens.get(i), text);
        }
        // 前半行重新拼一遍：末尾单独的 & 原来后面跟着别的字，拆开后要重新判断转义
        LegacyText.Writer left = new LegacyText.Writer(at + 2);
        for (int i = 0; i < index; i++) {
            emit(left, tokens.get(i), text);
        }
        // 后半行从记号边界开始，单独扫描结果和原来一致，开头补上光标处的样式即可
        String prefix = state.fromBase();
        return new Split(left.build(), prefix + text.substring(at), prefix.length());
    }

    /**
     * 把下一行接到这一行后面：中间补回到行首样式的代码（如果这一行末尾不在基础样式上），保证下一行的字样式不变
     * <p>
     * 下一行一开头就是颜色码或 {@code &r} 时不用补，它自己会把样式定下来。
     *
     * @param first  这一行
     * @param second 下一行
     * @param start  行首的样子（两行用的是同一个基础样式）
     * @return 合并结果；start = end = 两行接缝处（放光标用）
     */
    @Nonnull
    public static Result join(@Nonnull String first, @Nonnull String second, @Nonnull LegacyComponents.Look start) {
        List<LegacyText.Token> tokens = LegacyText.tokenize(first);
        CodeState state = new CodeState();
        for (LegacyText.Token token : tokens) {
            state.apply(token, first);
        }
        boolean needsReset = !state.isBase(start) && !second.isEmpty() && !startsWithReset(second);
        String tail = (needsReset ? LegacyComponents.restoreCode(start) : "") + second;
        // 第一行末尾单独的 & 原来是行尾，接上第二行后可能被读成转义或代码，重新拼一遍判断
        LegacyText.Writer writer = new LegacyText.Writer(first.length() + 4);
        for (LegacyText.Token token : tokens) {
            emit(writer, token, first);
        }
        String left = writer.build(tail);
        return new Result(left + tail, left.length(), left.length());
    }

    /**
     * 编辑文本的第一个记号是不是颜色 / 重置 / 认不出的代码（会把之前的样式全部盖掉）
     */
    private static boolean startsWithReset(@Nonnull String text) {
        LegacyText.Scanner scanner = LegacyText.scanEditable(text);
        return scanner.next() && scanner.token().resetsFormats();
    }

    /**
     * 选区对齐到记号边界
     *
     * @return {第一个记号下标, 最后一个记号下标 + 1}
     */
    @Nonnull
    private static int[] snap(@Nonnull List<LegacyText.Token> tokens, int selStart, int selEnd) {
        int start = Math.min(selStart, selEnd);
        int end = Math.max(selStart, selEnd);
        if (start == end) {
            return new int[]{0, tokens.size()};
        }
        int first = tokens.size();
        int last = 0;
        for (int i = 0; i < tokens.size(); i++) {
            LegacyText.Token token = tokens.get(i);
            if (token.end() > start && token.start() < end) {
                first = Math.min(first, i);
                last = i + 1;
            }
        }
        if (first >= last) {
            return new int[]{0, 0};
        }
        // 选区边界落在 surrogate 对中间时把整个字算进来
        if (pairedLow(tokens, first)) {
            first--;
        }
        if (last < tokens.size() && pairedLow(tokens, last)) {
            last++;
        }
        return new int[]{first, last};
    }

    /**
     * 这个记号是不是 surrogate 对的后一半（前一个记号是紧挨着的高位 surrogate）
     * <p>
     * emoji 这类字在编辑文本里是两个 TEXT 记号（高位 + 低位 surrogate），中间不能插代码、也不能从中间拆开。
     */
    private static boolean pairedLow(@Nonnull List<LegacyText.Token> tokens, int index) {
        if (index <= 0 || index >= tokens.size()) {
            return false;
        }
        LegacyText.Token token = tokens.get(index);
        LegacyText.Token previous = tokens.get(index - 1);
        return token.type() == LegacyText.TokenType.TEXT && Character.isLowSurrogate(token.ch())
                && previous.type() == LegacyText.TokenType.TEXT && Character.isHighSurrogate(previous.ch());
    }

    /**
     * 原样写回一个记号（字面字符重新判断转义，代码照抄原文）
     */
    private static void emit(@Nonnull LegacyText.Writer writer, @Nonnull LegacyText.Token token, @Nonnull String text) {
        if (token.visible()) {
            writer.literal(token.ch());
        } else {
            writer.raw(text.substring(token.start(), token.end()));
        }
    }

    /**
     * 选区后面还有字、而且后面第一个记号不是自带颜色 / 重置的，补恢复代码
     */
    private static void appendRestore(@Nonnull LegacyText.Writer writer, @Nonnull List<LegacyText.Token> tokens,
                                      int next, @Nonnull CodeState state, @Nonnull LegacyComponents.Look start) {
        if (next >= tokens.size() || tokens.get(next).resetsFormats()) {
            return;
        }
        writer.code(state.restore(start));
    }

    @Nonnull
    private static CodeState stateBefore(@Nonnull List<LegacyText.Token> tokens, int index, @Nonnull String text) {
        CodeState state = new CodeState();
        for (int i = 0; i < index; i++) {
            state.apply(tokens.get(i), text);
        }
        return state;
    }

    /**
     * 两个状态显示出来是不是一样（颜色和格式都相同）
     * <p>
     * 认不出的代码在原版字体里就是白色加清格式，但有的模组拿它配了自己的效果，所以只认原文完全相同的同一个代码。
     */
    private static boolean sameState(@Nonnull CodeState a, @Nonnull CodeState b, @Nonnull LegacyComponents.Look start) {
        boolean customA = a.colorCode != null && isCustom(a.colorCode);
        boolean customB = b.colorCode != null && isCustom(b.colorCode);
        if (customA || customB) {
            return customA && customB && a.colorCode.equals(b.colorCode) && a.added == b.added;
        }
        return a.rgb(start) == b.rgb(start) && a.mask(start) == b.mask(start);
    }

    /**
     * 是不是认不出的代码（§ 加 0-9 a-f k-o r 以外的字符）
     */
    private static boolean isCustom(@Nonnull String code) {
        return code.length() == 2 && code.charAt(0) == LegacyText.SECTION && !LegacyText.isCodeChar(code.charAt(1));
    }
}
