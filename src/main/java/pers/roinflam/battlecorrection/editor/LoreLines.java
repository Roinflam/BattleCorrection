package pers.roinflam.battlecorrection.editor;

import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Lore 的行模型：载入、编辑、写回，不依赖界面，可以单独测
 * <p>
 * 每一行记着载入时的原文（display.Lore 里那串 § 字符串）。写回时没动过的行原样写原文，逐字节一致：
 * 编辑文本放不下的控制字符（换行、制表符）还在，原文里重复或多余的代码（{@code §r§6} 里那个 §r 这类）也不会被规范化掉；
 * 动过的行才按编辑文本重新生成。
 * <p>
 * "没动过"看结果不看操作：当前编辑文本和"用当前基础样式解码原文"的结果一样就算没动。所以改了又改回去、
 * 上移再下移，写出去的都还是原文。切"原版样式"开关时编辑文本不变，显示效果不受基础样式影响的行（一开头就写了颜色码）
 * 照样保留原文，只有显示效果真的会变的行才重新生成。
 * <p>
 * 基础样式见 {@link LegacyComponents#start}：原版样式下没写颜色码的字跟着提示框加的 §5§o（紫色斜体），
 * 否则存的时候在最前面加 §r（白色、不斜体）。
 */
public final class LoreLines {

    /**
     * 一行
     */
    private static final class Line {
        /**
         * 载入时的原文；新加的行为 null
         */
        @Nullable
        final String raw;
        /**
         * 原文里编辑文本表达不了的东西（新加的行为空）
         */
        @Nonnull
        final Set<LegacyComponents.Lossy> lossy;
        /**
         * 原文按两种基础样式解码出的编辑文本：[0] = §r 基础样式，[1] = 原版样式；用到才算
         */
        @Nullable
        final String[] decoded;

        /**
         * 当前编辑文本
         */
        @Nonnull
        String editable;

        /**
         * 生成缓存：按 genFor（编辑文本，比身份）和 genVanilla 生成的 § 字符串
         */
        @Nullable
        String genFor;
        boolean genVanilla;
        @Nullable
        String genStored;

        /**
         * "没动过"的判断缓存：按 keptFor（编辑文本，比身份）和 keptVanilla
         */
        @Nullable
        String keptFor;
        boolean keptVanilla;
        boolean kept;

        Line(@Nullable String raw, @Nonnull String editable) {
            this.raw = raw;
            this.lossy = raw == null ? Collections.<LegacyComponents.Lossy>emptySet() : LegacyComponents.lossyOf(raw);
            this.decoded = raw == null ? null : new String[2];
            this.editable = editable;
        }

        /**
         * 新加的一行
         */
        static Line fresh(@Nonnull String editable) {
            return new Line(null, editable);
        }

        /**
         * 原文按某种基础样式解码的编辑文本（新加的行返回当前编辑文本）
         */
        @Nonnull
        String decodedFor(boolean vanilla) {
            String[] cache = decoded;
            if (cache == null || raw == null) {
                return editable;
            }
            int slot = vanilla ? 1 : 0;
            String text = cache[slot];
            if (text == null) {
                text = LegacyComponents.decode(raw, vanilla, LegacyComponents.LORE_PARENT).editable();
                cache[slot] = text;
            }
            return text;
        }

        /**
         * 写出去的是不是原文
         * <p>
         * 编辑文本等于"按当前基础样式解码原文"的结果，就是没动过。另一种情况是刚切了"原版样式"开关：
         * 编辑文本还是按另一种基础样式解出来的，如果它在两种基础样式下显示得一模一样（一开头就是颜色码，
         * 例如 {@code &6金色}），切开关对这一行没有任何效果，照样写原文；编辑文本不换，开关切回去时也还是原文。
         */
        boolean kept(boolean vanilla) {
            if (raw == null) {
                return false;
            }
            String current = editable;
            if (keptFor != current || keptVanilla != vanilla) {
                keptFor = current;
                keptVanilla = vanilla;
                kept = current.equals(decodedFor(vanilla))
                        || (current.equals(decodedFor(!vanilla))
                        && LegacyComponents.sameLook(current, vanilla, !vanilla, LegacyComponents.LORE_PARENT));
            }
            return kept;
        }

        /**
         * 按编辑文本生成（带缓存：编辑文本没换时返回同一个字符串对象，预览靠它判断要不要重算）
         */
        @Nonnull
        String generate(boolean vanilla) {
            String current = editable;
            String stored = genStored;
            if (genFor == current && genVanilla == vanilla && stored != null) {
                return stored;
            }
            stored = LegacyComponents.encode(current, vanilla);
            genStored = stored;
            genFor = current;
            genVanilla = vanilla;
            return stored;
        }
    }

    /**
     * 多行粘贴 / 拆行的结果：光标该落在哪一行的哪个位置
     */
    public static final class Caret {
        private final int line;
        private final int caret;

        /**
         * @param line  行下标
         * @param caret 光标位置
         */
        public Caret(int line, int caret) {
            this.line = line;
            this.caret = caret;
        }

        public int line() {
            return line;
        }

        public int caret() {
            return caret;
        }
    }

    private final List<Line> lines;
    private boolean vanilla;

    private LoreLines(@Nonnull List<Line> lines, boolean vanilla) {
        this.lines = lines;
        this.vanilla = vanilla;
    }

    // ═══════════════════════════════════════════════════════════════
    // 载入 / 写回
    // ═══════════════════════════════════════════════════════════════

    /**
     * 空模型
     *
     * @param vanilla 是否原版样式
     * @return 模型
     */
    @Nonnull
    public static LoreLines empty(boolean vanilla) {
        return new LoreLines(new ArrayList<Line>(), vanilla);
    }

    /**
     * 从 display.Lore 载入
     *
     * @param lore 列表（元素是 § 字符串）；null = 没有 Lore
     * @return 模型
     */
    @Nonnull
    public static LoreLines fromTag(@Nullable NBTTagList lore) {
        List<String> raws = new ArrayList<>(lore == null ? 0 : lore.tagCount());
        if (lore != null) {
            for (int i = 0; i < lore.tagCount(); i++) {
                raws.add(lore.getStringTagAt(i));
            }
        }
        return load(raws);
    }

    /**
     * 载入，自动判断用不用原版样式
     * <p>
     * 两种基础样式都解一遍，选编辑文本总长度短的那种（代码少 = 更贴近原作者的写法）：
     * 原版写的、没有代码的 Lore 选原版样式，插件写的 {@code §r...} 选 §r 基础样式。
     * 一样长（每行一开头就是颜色码，两种写法显示一样）时选 §r 基础样式，之后新加的行没写颜色码时是白色，和大多数服务器一致。
     * 两种解码结果都缓存着，之后切开关时判断"没动过"不用再解。
     *
     * @param raws 每行原文
     * @return 模型
     */
    @Nonnull
    public static LoreLines load(@Nonnull List<String> raws) {
        List<Line> lines = new ArrayList<>(raws.size());
        long white = 0;
        long purple = 0;
        for (String raw : raws) {
            Line line = new Line(raw, "");
            lines.add(line);
            white += line.decodedFor(false).length();
            purple += line.decodedFor(true).length();
        }
        boolean vanilla = purple < white;
        for (Line line : lines) {
            line.editable = line.decodedFor(vanilla);
        }
        return new LoreLines(lines, vanilla);
    }

    /**
     * 载入，指定基础样式
     *
     * @param raws    每行原文
     * @param vanilla 是否原版样式
     * @return 模型
     */
    @Nonnull
    public static LoreLines load(@Nonnull List<String> raws, boolean vanilla) {
        List<Line> lines = new ArrayList<>(raws.size());
        for (String raw : raws) {
            Line line = new Line(raw, "");
            line.editable = line.decodedFor(vanilla);
            lines.add(line);
        }
        return new LoreLines(lines, vanilla);
    }

    /**
     * 写回用的列表：没动过的行是原文，其余按编辑文本生成
     *
     * @return 列表（元素是 § 字符串）
     */
    @Nonnull
    public NBTTagList toTag() {
        NBTTagList tag = new NBTTagList();
        for (int i = 0; i < lines.size(); i++) {
            tag.appendTag(new NBTTagString(stored(i)));
        }
        return tag;
    }

    /**
     * 写回用的字符串列表
     *
     * @return 每行 § 字符串
     */
    @Nonnull
    public List<String> storedLines() {
        List<String> result = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            result.add(stored(i));
        }
        return result;
    }

    /**
     * 物品上现在的 Lore 是不是就是这个模型会写出去的东西
     *
     * @param lore 物品上的列表；null = 没有
     * @return true = 一致
     */
    public boolean matches(@Nullable NBTTagList lore) {
        int size = lore == null ? 0 : lore.tagCount();
        if (size != lines.size()) {
            return false;
        }
        for (int i = 0; i < size; i++) {
            if (!lore.getStringTagAt(i).equals(stored(i))) {
                return false;
            }
        }
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // 读
    // ═══════════════════════════════════════════════════════════════

    public int size() {
        return lines.size();
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /**
     * 是否原版样式（没写颜色码的字紫色斜体）
     */
    public boolean vanilla() {
        return vanilla;
    }

    /**
     * 行首没写代码的字是什么样子（拆行、并行补代码时用）
     */
    @Nonnull
    public LegacyComponents.Look start() {
        return LegacyComponents.start(vanilla, LegacyComponents.LORE_PARENT);
    }

    /**
     * 编辑文本
     *
     * @param index 行下标
     * @return 编辑文本
     */
    @Nonnull
    public String editable(int index) {
        return lines.get(index).editable;
    }

    /**
     * 写出去的是不是原文（没动过）
     *
     * @param index 行下标
     * @return true = 原文
     */
    public boolean kept(int index) {
        return lines.get(index).kept(vanilla);
    }

    /**
     * 原文里编辑文本表达不了的东西（新加的行为空）
     *
     * @param index 行下标
     * @return 集合
     */
    @Nonnull
    public Set<LegacyComponents.Lossy> lossy(int index) {
        return lines.get(index).lossy;
    }

    /**
     * 这一行原文里表达不了的东西会不会在写回时丢掉（有这类东西、而且这一行要重新生成）
     *
     * @param index 行下标
     * @return true = 会丢
     */
    public boolean losing(int index) {
        Line line = lines.get(index);
        return !line.lossy.isEmpty() && !line.kept(vanilla);
    }

    /**
     * 写出去的 § 字符串
     * <p>
     * 内容不变时返回同一个字符串对象（原文、或者生成缓存），预览按对象身份判断要不要重算。
     *
     * @param index 行下标
     * @return § 字符串
     */
    @Nonnull
    public String stored(int index) {
        Line line = lines.get(index);
        if (line.kept(vanilla) && line.raw != null) {
            return line.raw;
        }
        return line.generate(vanilla);
    }

    // ═══════════════════════════════════════════════════════════════
    // 改
    // ═══════════════════════════════════════════════════════════════

    /**
     * 切换基础样式（编辑文本不变，没写颜色码的字变成白色 / 紫色斜体）
     *
     * @param vanilla true = 原版样式
     */
    public void setVanilla(boolean vanilla) {
        this.vanilla = vanilla;
    }

    /**
     * 改一行
     *
     * @param index    行下标
     * @param editable 新的编辑文本
     * @return true = 真的变了（输入框移动光标也会回调，内容没变时什么都不做）
     */
    public boolean set(int index, @Nonnull String editable) {
        Line line = lines.get(index);
        if (line.editable.equals(editable)) {
            return false;
        }
        line.editable = editable;
        return true;
    }

    /**
     * 插入一行
     *
     * @param index    位置
     * @param editable 编辑文本
     */
    public void insert(int index, @Nonnull String editable) {
        lines.add(index, Line.fresh(editable));
    }

    /**
     * 删除一行
     *
     * @param index 行下标
     */
    public void remove(int index) {
        lines.remove(index);
    }

    /**
     * 把一行挪到另一个位置（原文跟着走）
     *
     * @param from 原位置
     * @param to   新位置
     */
    public void move(int from, int to) {
        if (from == to) {
            return;
        }
        Line line = lines.remove(from);
        lines.add(to, line);
    }

    /**
     * 在光标处断行（回车）：选中的字先删掉，光标后面的字挪到新的下一行，样式不变
     *
     * @param index    行下标
     * @param selStart 选区起点（没选中时等于光标）
     * @param selEnd   选区终点
     * @return 光标该去的地方（新的下一行）
     */
    @Nonnull
    public Caret breakLine(int index, int selStart, int selEnd) {
        String text = lines.get(index).editable;
        int start = clamp(Math.min(selStart, selEnd), text.length());
        int end = clamp(Math.max(selStart, selEnd), text.length());
        if (start == end && start == text.length()) {
            // 行尾回车：这一行原样不动（不重新拼，原文还在），下面加一个空行
            lines.add(index + 1, Line.fresh(""));
            return new Caret(index + 1, 0);
        }
        if (start == end && start == 0) {
            // 行首回车：在上面插一个空行，这一行整个挪下去，原文跟着走
            lines.add(index, Line.fresh(""));
            return new Caret(index + 1, 0);
        }
        return paste(index, start, end, Arrays.asList("", ""));
    }

    /**
     * 把这一行并到上一行末尾（行首退格）
     * <p>
     * 这一行是空的就直接删掉，上一行原样不动；上一行是空的就删上一行，这一行原样不动。都不空时用 {@link LegacyEdits#join}，
     * 中间按需补回到行首样式的代码，两段字的样式都不变。
     *
     * @param index 行下标（必须大于 0）
     * @return 光标该去的地方
     */
    @Nonnull
    public Caret joinWithPrevious(int index) {
        Line current = lines.get(index);
        Line previous = lines.get(index - 1);
        if (current.editable.isEmpty()) {
            lines.remove(index);
            return new Caret(index - 1, previous.editable.length());
        }
        if (previous.editable.isEmpty()) {
            lines.remove(index - 1);
            return new Caret(index - 1, 0);
        }
        LegacyEdits.Result joined = LegacyEdits.join(previous.editable, current.editable, start());
        previous.editable = joined.text();
        lines.remove(index);
        return new Caret(index - 1, joined.start());
    }

    /**
     * 往一行里粘贴多行文本
     * <p>
     * 和文本编辑器一样：选区换成粘贴的内容，第一段接在光标前面的字后头，最后一段后面接上光标后面原来的字，中间各占一行。
     * 光标后面那些字在原文里是什么样式，挪到新行后还是什么样式（{@link LegacyEdits#split} 在开头补代码，
     * 接到最后一段后面时 {@link LegacyEdits#join} 按需补回到行首样式的代码）。粘贴的每一段按剪贴板文本处理：
     * § 码换成 & 码、不成码的 § 变成字面的 &、控制字符去掉（{@link LegacyText#fromClipboard}）。
     *
     * @param index     行下标
     * @param selStart  选区起点
     * @param selEnd    选区终点
     * @param clipboard 按行拆好的剪贴板文本（原样，§ 还没转换）；只有一段时就是普通的单行插入
     * @return 光标该去的地方（粘贴内容的末尾）
     */
    @Nonnull
    public Caret paste(int index, int selStart, int selEnd, @Nonnull List<String> clipboard) {
        Line line = lines.get(index);
        String text = line.editable;
        int start = clamp(Math.min(selStart, selEnd), text.length());
        int end = clamp(Math.max(selStart, selEnd), text.length());
        if (clipboard.isEmpty()) {
            return new Caret(index, start);
        }
        if (clipboard.size() == 1) {
            // 单行：和输入框自己的粘贴一样
            String before = text.substring(0, start);
            String after = text.substring(end);
            String inserted = LegacyText.guardInsertion(before, LegacyText.fromClipboard(clipboard.get(0), after), after);
            line.editable = before + inserted + after;
            return new Caret(index, start + inserted.length());
        }
        LegacyEdits.Split head = LegacyEdits.split(text, start);
        LegacyEdits.Split tail = LegacyEdits.split(text, end);
        // 光标后面没有字了就不用带样式代码（不然新行开头会多一串没用的代码）
        String rest = tail.right().substring(tail.caret()).isEmpty() ? "" : tail.right();

        String first = LegacyText.fromClipboard(clipboard.get(0), "");
        line.editable = head.left() + LegacyText.guardInsertion(head.left(), first, "");

        int at = index;
        for (int i = 1; i < clipboard.size() - 1; i++) {
            lines.add(++at, Line.fresh(LegacyText.fromClipboard(clipboard.get(i), "")));
        }
        String last = LegacyText.fromClipboard(clipboard.get(clipboard.size() - 1), "");
        LegacyEdits.Result joined = LegacyEdits.join(last, rest, start());
        lines.add(++at, Line.fresh(joined.text()));
        // 最后一段是空的（回车、或者粘贴的文本以换行结尾）：新行就是挪下来的那些字，光标放在补的样式代码后面，
        // 接着打字和在原来那个位置打字是同一个样式
        int caret = last.isEmpty() && !rest.isEmpty() ? tail.caret() : joined.start();
        return new Caret(at, caret);
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(max, value));
    }

    /**
     * 这一行在物品提示框里的样子：ItemStack#getTooltip 给每行 Lore 加 §5§o，GuiScreen#getItemToolTip 再给首行以外的行加 §7
     * <p>
     * 拿去 {@code Component.literal} 画出来，就和提示框里那一行一模一样。
     *
     * @param stored 写进物品的那一行
     * @return 提示框里的那一行
     */
    @Nonnull
    public static String tooltipLine(@Nonnull String stored) {
        return TextFormatting.GRAY.toString() + TextFormatting.DARK_PURPLE + TextFormatting.ITALIC + stored;
    }
}
