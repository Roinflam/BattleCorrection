package pers.roinflam.battlecorrection.editor;

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
 */
public final class LegacyEdits {

    /**
     * 下划线、删除线：带着这两种格式的空格是看得见的
     */
    private static final int VISIBLE_ON_SPACE = LegacyComponents.formatBit(net.minecraft.ChatFormatting.UNDERLINE)
            | LegacyComponents.formatBit(net.minecraft.ChatFormatting.STRIKETHROUGH);

    /**
     * 改写结果
     *
     * @param text  新的完整文本
     * @param start 改写过的那一段在新文本里的起点（放回选区用）
     * @param end   终点
     */
    public record Result(@Nonnull String text, int start, int end) {
    }

    /**
     * 代码状态：颜色从哪来（null = 基础样式，否则是最近那个颜色码的原文）+ 之后打开的格式
     */
    private static final class CodeState {
        @Nullable
        String colorCode;
        int added;

        void apply(@Nonnull LegacyText.Token token, @Nonnull String text) {
            switch (token.type()) {
                case COLOR, HEX -> {
                    colorCode = text.substring(token.start(), token.end());
                    added = 0;
                }
                case RESET -> {
                    colorCode = null;
                    added = 0;
                }
                case FORMAT -> added |= LegacyComponents.formatBit(token.format());
                default -> {
                }
            }
        }

        boolean isBase() {
            return colorCode == null && added == 0;
        }

        /**
         * 从任意状态回到这个状态的代码
         */
        @Nonnull
        String restore() {
            return (colorCode == null ? "&r" : colorCode) + LegacyComponents.formatCodes(added);
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
     * 渐变：选区里每个看得见的字前面插一个 {@code &#RRGGBB}，颜色从 from 线性过渡到 to
     * <p>
     * 选区里原有的颜色码去掉（反正要被渐变盖掉），格式码（{@code &l} 等）会被颜色码清掉，所以逐字补在颜色码后面；
     * 选区开始前已经打开的格式也算上。空白字符不占渐变的步数，沿用前一个字的颜色（带下划线 / 删除线时按位置取色）。
     * 相邻的字颜色和格式都一样时不重复写代码。
     *
     * @param text     编辑文本
     * @param selStart 选区起点
     * @param selEnd   选区终点；和起点相同 = 没选，作用于全文
     * @param from     起点颜色
     * @param to       终点颜色
     * @return 改写结果
     */
    @Nonnull
    public static Result gradient(@Nonnull String text, int selStart, int selEnd, int from, int to) {
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
        int active = state.added;

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
        for (int i = first; i < last; i++) {
            LegacyText.Token token = tokens.get(i);
            state.apply(token, text);
            switch (token.type()) {
                case COLOR, HEX, RESET -> active = 0;
                case FORMAT -> active |= LegacyComponents.formatBit(token.format());
                case CUSTOM, STRAY -> writer.raw(text.substring(token.start(), token.end()));
                case TEXT, ESCAPE -> {
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
                                : lerp(from, to, steps <= 1 ? 0.0 : (double) Math.min(step, steps - 1) / (steps - 1));
                    } else {
                        rgb = lerp(from, to, steps <= 1 ? 0.0 : (double) step / (steps - 1));
                        step++;
                    }
                    if (rgb != lastRgb || active != lastActive) {
                        writer.code(LegacyText.colorCode(rgb) + LegacyComponents.formatCodes(active));
                        lastRgb = rgb;
                        lastActive = active;
                    }
                    writer.literal(c);
                }
            }
        }
        int endMark = writer.mark();
        // 选区后面的文字原来是什么样式就恢复成什么样式
        if (lastRgb >= 0) {
            appendRestore(writer, tokens, last, state);
        } else if (last < tokens.size()) {
            CodeState before = stateBefore(tokens, first, text);
            if (!sameState(before, state)) {
                appendRestore(writer, tokens, last, state);
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
     * 选区前面的代码仍然会影响到选区，所以选区开始时不在基础样式上就补一个 {@code &r}；选区后面的文字保持原样式。
     *
     * @param text     编辑文本
     * @param selStart 选区起点
     * @param selEnd   选区终点；和起点相同 = 没选，作用于全文
     * @return 改写结果
     */
    @Nonnull
    public static Result stripCodes(@Nonnull String text, int selStart, int selEnd) {
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
        boolean resetPending = !atStart.isBase();
        boolean wroteText = false;
        for (int i = first; i < last; i++) {
            LegacyText.Token token = tokens.get(i);
            state.apply(token, text);
            if (token.visible()) {
                if (resetPending) {
                    writer.code("&r");
                    resetPending = false;
                }
                writer.literal(token.ch());
                wroteText = true;
            }
        }
        int endMark = writer.mark();
        // 写过字的话现在处于基础样式；一个字都没有的话代码全删了，状态停在选区开始前
        CodeState now = wroteText ? new CodeState() : atStart;
        if (!sameState(now, state)) {
            appendRestore(writer, tokens, last, state);
        }
        for (int i = last; i < tokens.size(); i++) {
            emit(writer, tokens.get(i), text);
        }
        int[] marks = {startMark, endMark};
        String result = writer.build("", marks);
        return new Result(result, marks[0], marks[1]);
    }

    /**
     * 拆行结果
     *
     * @param left  前半行
     * @param right 后半行（开头补了光标处的样式代码）
     * @param caret 后半行里光标该放的位置（补的代码后面，接着打字就是原来的样式）
     */
    public record Split(@Nonnull String left, @Nonnull String right, int caret) {
    }

    /**
     * 在光标处把一行拆成两行：后半行开头补上光标处的样式，拆完两行显示得和原来一样
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
     * 把下一行接到这一行后面：中间补 {@code &r}（如果这一行末尾不在基础样式上），保证下一行的字样式不变
     * <p>
     * 下一行一开头就是颜色码或 {@code &r} 时不用补，它自己会把样式定下来。
     *
     * @param first  这一行
     * @param second 下一行
     * @return 合并结果；start = end = 两行接缝处（放光标用）
     */
    @Nonnull
    public static Result join(@Nonnull String first, @Nonnull String second) {
        List<LegacyText.Token> tokens = LegacyText.tokenize(first);
        CodeState state = new CodeState();
        for (LegacyText.Token token : tokens) {
            state.apply(token, first);
        }
        boolean needsReset = !state.isBase() && !second.isEmpty() && !startsWithReset(second);
        String tail = (needsReset ? "&r" : "") + second;
        // 第一行末尾单独的 & 原来是行尾，接上第二行后可能被读成转义或代码，重新拼一遍判断
        LegacyText.Writer writer = new LegacyText.Writer(first.length() + 4);
        for (LegacyText.Token token : tokens) {
            emit(writer, token, first);
        }
        String left = writer.build(tail);
        return new Result(left + tail, left.length(), left.length());
    }

    /**
     * 编辑文本的第一个记号是不是颜色 / 十六进制 / 重置（会把之前的样式全部盖掉）
     */
    private static boolean startsWithReset(@Nonnull String text) {
        LegacyText.Scanner scanner = LegacyText.scanEditable(text);
        if (!scanner.next()) {
            return false;
        }
        LegacyText.TokenType type = scanner.type();
        return type == LegacyText.TokenType.COLOR || type == LegacyText.TokenType.HEX || type == LegacyText.TokenType.RESET;
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
     * emoji 这类字在编辑文本里是两个 TEXT 记号（高位 + 低位 surrogate），原版渲染时它们合成一个字；中间不能插代码、也不能从中间拆开。
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
                                      int next, @Nonnull CodeState state) {
        if (next >= tokens.size() || tokens.get(next).resetsFormats()) {
            return;
        }
        writer.code(state.restore());
    }

    @Nonnull
    private static CodeState stateBefore(@Nonnull List<LegacyText.Token> tokens, int index, @Nonnull String text) {
        CodeState state = new CodeState();
        for (int i = 0; i < index; i++) {
            state.apply(tokens.get(i), text);
        }
        return state;
    }

    private static boolean sameState(@Nonnull CodeState a, @Nonnull CodeState b) {
        return a.added == b.added && java.util.Objects.equals(a.colorCode, b.colorCode);
    }

    /**
     * RGB 线性插值
     */
    private static int lerp(int from, int to, double t) {
        int r = (int) Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }
}
