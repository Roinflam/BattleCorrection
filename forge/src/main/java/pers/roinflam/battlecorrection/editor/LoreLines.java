package pers.roinflam.battlecorrection.editor;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Lore 的行模型：载入、编辑、写回，不依赖界面，可以单独测
 * <p>
 * 每一行记着载入时的原文（display.Lore 里那串 JSON）。写回时没动过的行原样写原文，逐字节一致，
 * 点击 / 悬停事件、字体、翻译键这些编辑文本表达不了的东西都还在；动过的行才按编辑文本重新生成。
 * <p>
 * "没动过"看结果不看操作：当前编辑文本和"用当前基础样式解码原文"的结果一样就算没动。所以改了又改回去、
 * 上移再下移，写出去的都还是原文。切"原版样式"开关时编辑文本不变，显示效果不受基础样式影响的行（每个字都自己写了颜色）
 * 照样保留原文，只有显示效果真的会变的行才重新生成。
 * <p>
 * 原文不是合法 JSON 的行（插件直接写了 § 文本之类）按 § 文本读进来。原版显示提示框时一遇到这种行就把整个 Lore 删掉，
 * 原样留着只会继续坏，所以只要写回，这种行就改写成合法的 JSON。一行都没动时页面根本不写，物品保持原样。
 * 原文是 {@code ""} 或 {@code null} 的行不算坏：原版只是跳过它，没改成别的字就原样写回。
 * <p>
 * 基础样式和外层样式见 {@link LegacyComponents#loreBase} / {@link LegacyComponents#LORE_PARENT}。
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
         * 原文解析出的组件；新加的行、原文不是合法 JSON 时为 null
         */
        @Nullable
        final Component parsed;
        /**
         * 原文不是合法 JSON，按 § 文本读进来的
         */
        final boolean plain;
        /**
         * 原文解析出来是 null（空串、"null"）：原版跳过这一行，编辑文本是空的
         */
        final boolean nullJson;
        /**
         * 原文里编辑文本表达不了的东西；合法 JSON 的行在第一次解码时顺带算出（省一遍拍平），之前为 null
         */
        @Nullable
        Set<LegacyComponents.Lossy> lossy;
        /**
         * 原文按两种基础样式解码出的编辑文本：[0] = 白色不斜体，[1] = 原版样式；用到才算
         */
        @Nullable
        final String[] decoded;

        /**
         * 当前编辑文本
         */
        @Nonnull
        String editable;

        /**
         * 生成缓存：按 genFor（编辑文本，比身份）和 genVanilla 生成的组件与 JSON
         */
        @Nullable
        String genFor;
        boolean genVanilla;
        @Nullable
        MutableComponent genComponent;
        @Nullable
        String genJson;

        /**
         * "没动过"的判断缓存：按 keptFor（编辑文本，比身份）和 keptVanilla
         */
        @Nullable
        String keptFor;
        boolean keptVanilla;
        boolean kept;

        Line(@Nullable String raw, @Nullable Component parsed, boolean plain, boolean nullJson,
             @Nullable Set<LegacyComponents.Lossy> lossy, @Nonnull String editable) {
            this.raw = raw;
            this.parsed = parsed;
            this.plain = plain;
            this.nullJson = nullJson;
            this.lossy = lossy;
            this.decoded = parsed == null ? null : new String[2];
            this.editable = editable;
        }

        /**
         * 新加的一行
         */
        static Line fresh(@Nonnull String editable) {
            return new Line(null, null, false, false, Collections.emptySet(), editable);
        }

        /**
         * 原文按某种基础样式解码的编辑文本（只对合法 JSON 的原文有意义）
         */
        @Nonnull
        String decodedFor(boolean vanilla) {
            String[] cache = decoded;
            if (cache == null || parsed == null) {
                return editable;
            }
            int slot = vanilla ? 1 : 0;
            String text = cache[slot];
            if (text == null) {
                LegacyComponents.Decoded result = LegacyComponents.decode(parsed, LegacyComponents.loreBase(vanilla),
                        LegacyComponents.LORE_PARENT);
                text = result.editable();
                cache[slot] = text;
                if (lossy == null) {
                    lossy = result.lossy();
                }
            }
            return text;
        }

        /**
         * 原文里表达不了的东西
         */
        @Nonnull
        Set<LegacyComponents.Lossy> lossy(boolean vanilla) {
            if (lossy == null) {
                decodedFor(vanilla);
            }
            Set<LegacyComponents.Lossy> result = lossy;
            return result == null ? Collections.emptySet() : result;
        }

        /**
         * 写出去的是不是原文
         * <p>
         * 编辑文本等于"按当前基础样式解码原文"的结果，就是没动过。另一种情况是刚切了"原版样式"开关：
         * 编辑文本还是按另一种基础样式解出来的，如果它在两种基础样式下显示得一模一样（每个字都自己写了颜色，
         * 例如 {@code &5&oHover me}），切开关对这一行没有任何效果，照样写原文，悬停、字体、翻译键都不丢；
         * 编辑文本不换，开关切回去时也还是原文。
         */
        boolean kept(boolean vanilla) {
            if (parsed == null) {
                // 新加的行没有原文；原文不是合法 JSON 的行一写就改写；原文是 null 的行没打字就照旧
                return nullJson && editable.isEmpty();
            }
            String current = editable;
            if (keptFor != current || keptVanilla != vanilla) {
                keptFor = current;
                keptVanilla = vanilla;
                kept = current.equals(decodedFor(vanilla))
                        || (current.equals(decodedFor(!vanilla))
                        && LegacyComponents.sameLook(current, LegacyComponents.loreBase(vanilla),
                        LegacyComponents.loreBase(!vanilla), LegacyComponents.LORE_PARENT));
            }
            return kept;
        }

        /**
         * 按编辑文本生成（带缓存）
         */
        void generate(boolean vanilla) {
            String current = editable;
            if (genFor == current && genVanilla == vanilla && genJson != null) {
                return;
            }
            MutableComponent component = LegacyComponents.toComponent(current, LegacyComponents.loreBase(vanilla),
                    LegacyComponents.LORE_PARENT);
            genComponent = component;
            genJson = Component.Serializer.toJson(component);
            genFor = current;
            genVanilla = vanilla;
        }
    }

    /**
     * 多行粘贴 / 拆行的结果：光标该落在哪一行的哪个位置
     *
     * @param line  行下标
     * @param caret 光标位置
     */
    public record Caret(int line, int caret) {
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
        return new LoreLines(new ArrayList<>(), vanilla);
    }

    /**
     * 从 display.Lore 载入
     *
     * @param lore 列表（元素是 JSON 字符串）；null = 没有 Lore
     * @return 模型
     */
    @Nonnull
    public static LoreLines fromTag(@Nullable ListTag lore) {
        List<String> raws = new ArrayList<>(lore == null ? 0 : lore.size());
        if (lore != null) {
            for (int i = 0; i < lore.size(); i++) {
                raws.add(lore.getString(i));
            }
        }
        return load(raws);
    }

    /**
     * 载入，自动判断用不用原版样式
     * <p>
     * 两种基础样式都解一遍，选编辑文本总长度短的那种（代码少 = 更贴近原作者的写法）：
     * 自己存的 {@code {"italic":false,"color":"white",...}} 选白色，原版写的 {@code "..."} 选原版样式，
     * Spigot 那种根是空的、子组件白色不斜体的也选白色。一样长时看第一行的根样式是不是什么都没写。
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
        boolean firstBlank = false;
        for (int i = 0; i < raws.size(); i++) {
            Line line = read(raws.get(i));
            lines.add(line);
            if (line.parsed != null) {
                white += line.decodedFor(false).length();
                purple += line.decodedFor(true).length();
                if (i == 0) {
                    firstBlank = LegacyComponents.isBlank(line.parsed.getStyle());
                }
            }
        }
        boolean vanilla = purple < white || (purple == white && firstBlank);
        for (Line line : lines) {
            if (line.parsed != null) {
                line.editable = line.decodedFor(vanilla);
            }
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
            Line line = read(raw);
            if (line.parsed != null) {
                line.editable = line.decodedFor(vanilla);
            }
            lines.add(line);
        }
        return new LoreLines(lines, vanilla);
    }

    /**
     * 读一行原文（编辑文本先放解码前的占位，载入时按选定的基础样式填）
     */
    @Nonnull
    private static Line read(@Nonnull String raw) {
        MutableComponent parsed = parse(raw);
        if (parsed == null) {
            // 原版对解析出 null 的行（"null"、空串）什么都不显示，对解析失败的行整个 Lore 都不显示；
            // 前者当空行，后者按 § 文本处理：§ 码变 & 码，不成码的 § 变成字面的 &，一个字都不吞，
            // 编辑文本里也不会留下输入框打不出来的 §。原文里的 & 是普通字符（"R&D" 这种），必要时转义成 &&，
            // 不能像粘贴那样把不带 § 的文本当成 & 写法，否则 &D 会被读成颜色码
            boolean nullJson = isNullJson(raw);
            String editable = nullJson ? "" : LegacyText.fromStored(raw);
            return new Line(raw, null, !nullJson, nullJson, Collections.emptySet(), editable);
        }
        return new Line(raw, parsed, false, false, null, "");
    }

    /**
     * 解析一行 JSON
     *
     * @param json JSON
     * @return 组件；不合法或者是 null 时返回 null
     */
    @Nullable
    public static MutableComponent parse(@Nonnull String json) {
        try {
            return Component.Serializer.fromJson(json);
        } catch (RuntimeException ignored) {
            // 物品数据来自任意来源，JSON 结构不对时 Gson 会抛各种运行时异常，原版这里也是全接住
            return null;
        }
    }

    private static boolean isNullJson(@Nonnull String raw) {
        String trimmed = raw.trim();
        return trimmed.isEmpty() || trimmed.equals("null");
    }

    /**
     * 写回用的列表：没动过的行是原文，其余按编辑文本生成
     *
     * @return 列表（元素是 JSON 字符串）
     */
    @Nonnull
    public ListTag toTag() {
        ListTag tag = new ListTag();
        for (int i = 0; i < lines.size(); i++) {
            tag.add(StringTag.valueOf(json(i)));
        }
        return tag;
    }

    /**
     * 写回用的字符串列表
     *
     * @return 每行 JSON
     */
    @Nonnull
    public List<String> jsons() {
        List<String> result = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            result.add(json(i));
        }
        return result;
    }

    /**
     * 物品上现在的 Lore 是不是就是这个模型会写出去的东西
     *
     * @param lore 物品上的列表；null = 没有
     * @return true = 一致
     */
    public boolean matches(@Nullable ListTag lore) {
        int size = lore == null ? 0 : lore.size();
        if (size != lines.size()) {
            return false;
        }
        for (int i = 0; i < size; i++) {
            if (!lore.getString(i).equals(json(i))) {
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
     * 原文里编辑文本表达不了的东西（新加的行、原文不合法的行为空）
     *
     * @param index 行下标
     * @return 集合
     */
    @Nonnull
    public Set<LegacyComponents.Lossy> lossy(int index) {
        return lines.get(index).lossy(vanilla);
    }

    /**
     * 原文是不是不合法的 JSON（按 § 文本读进来的）
     *
     * @param index 行下标
     * @return true = 不合法
     */
    public boolean plain(int index) {
        return lines.get(index).plain;
    }

    /**
     * 这一行原文里表达不了的东西会不会在写回时丢掉（有这类东西、而且这一行要重新生成）
     *
     * @param index 行下标
     * @return true = 会丢
     */
    public boolean losing(int index) {
        Line line = lines.get(index);
        return !line.lossy(vanilla).isEmpty() && !line.kept(vanilla);
    }

    /**
     * 写出去的 JSON
     *
     * @param index 行下标
     * @return JSON
     */
    @Nonnull
    public String json(int index) {
        Line line = lines.get(index);
        if (line.kept(vanilla)) {
            return line.raw == null ? "" : line.raw;
        }
        line.generate(vanilla);
        return line.genJson == null ? "" : line.genJson;
    }

    /**
     * 写出去的那一行对应的组件（预览用，和写进物品的是同一份转换结果；不要改它）
     *
     * @param index 行下标
     * @return 组件
     */
    @Nonnull
    public Component component(int index) {
        Line line = lines.get(index);
        if (line.kept(vanilla) && line.parsed != null) {
            return line.parsed;
        }
        line.generate(vanilla);
        return line.genComponent == null ? Component.empty() : line.genComponent;
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
        return paste(index, start, end, List.of("", ""));
    }

    /**
     * 把这一行并到上一行末尾（行首退格）
     * <p>
     * 这一行是空的就直接删掉，上一行原样不动；上一行是空的就删上一行，这一行原样不动。都不空时用 {@link LegacyEdits#join}，
     * 中间按需补 {@code &r}，两段字的样式都不变。
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
        LegacyEdits.Result joined = LegacyEdits.join(previous.editable, current.editable);
        previous.editable = joined.text();
        lines.remove(index);
        return new Caret(index - 1, joined.start());
    }

    /**
     * 往一行里粘贴多行文本
     * <p>
     * 和文本编辑器一样：选区换成粘贴的内容，第一段接在光标前面的字后头，最后一段后面接上光标后面原来的字，中间各占一行。
     * 光标后面那些字在原文里是什么样式，挪到新行后还是什么样式（{@link LegacyEdits#split} 在开头补代码，
     * 接到最后一段后面时 {@link LegacyEdits#join} 按需补 {@code &r}）。粘贴的每一段按剪贴板文本处理：
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
        LegacyEdits.Result joined = LegacyEdits.join(last, rest);
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
     * 给预览套上外层样式
     *
     * @param component 组件
     * @return 包了一层的组件，画出来就是提示框里的样子
     */
    @Nonnull
    public static MutableComponent previewOf(@Nonnull Component component) {
        return LegacyComponents.wrap(component, LegacyComponents.LORE_PARENT);
    }
}
