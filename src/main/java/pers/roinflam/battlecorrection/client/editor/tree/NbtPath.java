package pers.roinflam.battlecorrection.client.editor.tree;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * NBT 树里一个节点的位置：从根往下的一串"段"，段是复合标签的键名（String）或列表下标（Integer）
 * <p>
 * 树的展开 / 选中状态都按路径记。路径分段存，不拼成 {@code /tag/display/Lore/0} 这种字符串：
 * 拼起来的话键名里本身带 / 就分不清层级，分段存时键名里有什么字符都不会串。
 * <p>
 * 文本形式和 /data 命令的 NBT 路径一致：{@code tag.display.Lore[0]}，键名里有空格、点、引号、方括号、花括号
 * 或者是空串时用双引号包起来（里面的双引号和反斜杠前面加反斜杠）。解析只支持"键名 + 下标"，
 * /data 的过滤写法（{@code {...}}、{@code []}）在编辑器里没有意义，遇到就报错。
 * 下标可以是负数（从末尾数），{@link #normalize} 换算成正的。
 * <p>
 * 不可变；哈希值在构造时算好，放进 HashSet 查找不会重复遍历。
 */
public final class NbtPath {

    /**
     * 根
     */
    public static final NbtPath ROOT = new NbtPath(new Object[0]);

    private static final char QUOTE = '"';
    private static final char SINGLE_QUOTE = '\'';
    private static final char BACKSLASH = (char) 92;

    private final Object[] segments;
    private final int hash;

    private NbtPath(@Nonnull Object[] segments) {
        this.segments = segments;
        this.hash = Arrays.hashCode(segments);
    }

    /**
     * 路径解析失败
     */
    public static final class SyntaxException extends IllegalArgumentException {

        /**
         * 失败原因，界面按它挑提示文字
         */
        public enum Reason {
            /**
             * 引号没有闭合
             */
            UNTERMINATED_QUOTE("unterminated_quote"),
            /**
             * 引号里出现了不支持的转义
             */
            BAD_ESCAPE("bad_escape"),
            /**
             * 方括号里不是整数
             */
            BAD_INDEX("bad_index"),
            /**
             * /data 的过滤写法
             */
            UNSUPPORTED("unsupported"),
            /**
             * 两段之间缺了点
             */
            EXPECTED_DOT("expected_dot"),
            /**
             * 该有键名的地方是空的（开头就是点、末尾多一个点之类）
             */
            EMPTY_KEY("empty_key");

            private final String key;

            Reason(@Nonnull String key) {
                this.key = key;
            }

            /**
             * 翻译键后缀
             *
             * @return 后缀
             */
            @Nonnull
            public String key() {
                return key;
            }
        }

        private final Reason reason;
        private final int position;

        SyntaxException(@Nonnull Reason reason, int position) {
            super(reason.key() + "@" + position);
            this.reason = reason;
            this.position = position;
        }

        @Nonnull
        public Reason reason() {
            return reason;
        }

        /**
         * 出错的字符位置（从 0 数）
         *
         * @return 位置
         */
        public int position() {
            return position;
        }
    }

    // 构造与访问

    /**
     * 从一个段栈的前 length 段建路径（搜索时用，段栈会继续被改，所以这里复制一份）
     *
     * @param segments 段（String 或 Integer）
     * @param length   取前几段
     * @return 路径
     */
    @Nonnull
    static NbtPath of(@Nonnull Object[] segments, int length) {
        return length == 0 ? ROOT : new NbtPath(Arrays.copyOf(segments, length));
    }

    /**
     * 往下一层：复合标签里的键
     *
     * @param key 键名
     * @return 新路径
     */
    @Nonnull
    public NbtPath child(@Nonnull String key) {
        return append(key);
    }

    /**
     * 往下一层：列表里的下标
     *
     * @param index 下标
     * @return 新路径
     */
    @Nonnull
    public NbtPath child(int index) {
        return append(index);
    }

    @Nonnull
    private NbtPath append(@Nonnull Object segment) {
        Object[] next = Arrays.copyOf(segments, segments.length + 1);
        next[segments.length] = segment;
        return new NbtPath(next);
    }

    /**
     * 上一层
     *
     * @return 父路径；根返回 null
     */
    @Nullable
    public NbtPath parent() {
        if (segments.length == 0) {
            return null;
        }
        if (segments.length == 1) {
            return ROOT;
        }
        return new NbtPath(Arrays.copyOf(segments, segments.length - 1));
    }

    /**
     * 段数（根是 0）
     *
     * @return 段数
     */
    public int size() {
        return segments.length;
    }

    public boolean isRoot() {
        return segments.length == 0;
    }

    /**
     * 第 i 段是不是列表下标
     *
     * @param i 段序号
     * @return true = 下标
     */
    public boolean isIndex(int i) {
        return segments[i] instanceof Integer;
    }

    /**
     * 第 i 段的键名
     *
     * @param i 段序号
     * @return 键名；这一段是下标时返回 null
     */
    @Nullable
    public String key(int i) {
        return segments[i] instanceof String key ? key : null;
    }

    /**
     * 第 i 段的下标
     *
     * @param i 段序号
     * @return 下标；这一段是键名时返回 -1
     */
    public int index(int i) {
        return segments[i] instanceof Integer index ? index : -1;
    }

    /**
     * 最后一段的键名（列表元素、根返回 null）
     *
     * @return 键名
     */
    @Nullable
    public String lastKey() {
        return segments.length == 0 ? null : key(segments.length - 1);
    }

    /**
     * 是不是以 prefix 开头（相等也算）
     *
     * @param prefix 前缀
     * @return true = 是
     */
    public boolean startsWith(@Nonnull NbtPath prefix) {
        if (prefix.segments.length > segments.length) {
            return false;
        }
        for (int i = 0; i < prefix.segments.length; i++) {
            if (!segments[i].equals(prefix.segments[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * 把开头的 oldPrefix 换成 newPrefix（改名、移动之后迁移展开状态用）
     *
     * @param oldPrefix 旧前缀（调用方保证 {@link #startsWith} 成立）
     * @param newPrefix 新前缀
     * @return 新路径
     */
    @Nonnull
    public NbtPath replacePrefix(@Nonnull NbtPath oldPrefix, @Nonnull NbtPath newPrefix) {
        int tail = segments.length - oldPrefix.segments.length;
        Object[] next = Arrays.copyOf(newPrefix.segments, newPrefix.segments.length + tail);
        System.arraycopy(segments, oldPrefix.segments.length, next, newPrefix.segments.length, tail);
        return new NbtPath(next);
    }

    /**
     * 把第 i 段换成一个列表下标（列表增删、移动后挪下标用）
     *
     * @param i     段序号
     * @param index 新下标
     * @return 新路径
     */
    @Nonnull
    public NbtPath withIndex(int i, int index) {
        Object[] next = segments.clone();
        next[i] = index;
        return new NbtPath(next);
    }

    // 在标签里定位

    /**
     * 沿路径取标签
     *
     * @param root 根
     * @return 标签；路径不存在返回 null
     */
    @Nullable
    public Tag resolve(@Nonnull Tag root) {
        Tag current = root;
        for (Object segment : segments) {
            current = step(current, segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /**
     * 把负数下标换算成正的，并确认路径存在
     *
     * @param root 根
     * @return 规范化后的路径；不存在返回 null
     */
    @Nullable
    public NbtPath normalize(@Nonnull Tag root) {
        Object[] result = segments.clone();
        Tag current = root;
        for (int i = 0; i < result.length; i++) {
            Object segment = result[i];
            if (segment instanceof Integer index && index < 0 && current instanceof ListTag list) {
                result[i] = list.size() + index;
            }
            current = step(current, result[i]);
            if (current == null) {
                return null;
            }
        }
        return new NbtPath(result);
    }

    @Nullable
    private static Tag step(@Nonnull Tag current, @Nonnull Object segment) {
        if (segment instanceof String key) {
            return current instanceof CompoundTag compound ? compound.get(key) : null;
        }
        if (!(current instanceof ListTag list)) {
            return null;
        }
        int index = (Integer) segment;
        if (index < 0) {
            index += list.size();
        }
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    // 文本形式

    /**
     * /data 写法的文本，例如 {@code tag.display.Lore[0]}；根是空串
     */
    @Nonnull
    @Override
    public String toString() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            Object segment = segments[i];
            if (segment instanceof Integer index) {
                out.append('[').append(index.intValue()).append(']');
                continue;
            }
            if (i > 0) {
                out.append('.');
            }
            String key = (String) segment;
            if (needsQuotes(key)) {
                appendQuoted(out, key);
            } else {
                out.append(key);
            }
        }
        return out.toString();
    }

    /**
     * 这个键名在路径里要不要加引号（和原版 NbtPathArgument 认的裸键名字符一致）
     *
     * @param key 键名
     * @return true = 要
     */
    public static boolean needsQuotes(@Nonnull String key) {
        if (key.isEmpty()) {
            return true;
        }
        for (int i = 0; i < key.length(); i++) {
            if (!isUnquotedChar(key.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUnquotedChar(char c) {
        return c != ' ' && c != QUOTE && c != SINGLE_QUOTE && c != '[' && c != ']' && c != '.' && c != '{' && c != '}';
    }

    private static void appendQuoted(@Nonnull StringBuilder out, @Nonnull String key) {
        out.append(QUOTE);
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == QUOTE || c == BACKSLASH) {
                out.append(BACKSLASH);
            }
            out.append(c);
        }
        out.append(QUOTE);
    }

    /**
     * 解析 /data 写法的路径
     * <p>
     * 支持：裸键名、单 / 双引号键名（引号里只认反斜杠转义引号和反斜杠本身）、{@code [n]} 下标（可为负数）、
     * 段之间的点（下标前面的点可写可不写，和原版一样）。首尾空白忽略，空串 = 根。
     *
     * @param text 文本
     * @return 路径
     * @throws SyntaxException 格式不对
     */
    @Nonnull
    public static NbtPath parse(@Nonnull String text) {
        String s = text.trim();
        List<Object> result = new ArrayList<>();
        int pos = 0;
        int length = s.length();
        while (pos < length) {
            char c = s.charAt(pos);
            if (c == QUOTE || c == SINGLE_QUOTE) {
                StringBuilder key = new StringBuilder();
                pos = readQuoted(s, pos, key);
                result.add(key.toString());
            } else if (c == '[') {
                int close = s.indexOf(']', pos + 1);
                if (close < 0) {
                    throw new SyntaxException(SyntaxException.Reason.BAD_INDEX, pos);
                }
                String body = s.substring(pos + 1, close).trim();
                if (body.isEmpty() || body.charAt(0) == '{') {
                    throw new SyntaxException(SyntaxException.Reason.UNSUPPORTED, pos);
                }
                try {
                    result.add(Integer.parseInt(body));
                } catch (NumberFormatException e) {
                    throw new SyntaxException(SyntaxException.Reason.BAD_INDEX, pos + 1);
                }
                pos = close + 1;
            } else if (c == '{') {
                throw new SyntaxException(SyntaxException.Reason.UNSUPPORTED, pos);
            } else {
                int start = pos;
                while (pos < length && isUnquotedChar(s.charAt(pos))) {
                    pos++;
                }
                if (pos == start) {
                    throw new SyntaxException(c == '.' ? SyntaxException.Reason.EMPTY_KEY
                            : SyntaxException.Reason.EXPECTED_DOT, pos);
                }
                result.add(s.substring(start, pos));
            }
            if (pos < length && s.charAt(pos) == '{') {
                throw new SyntaxException(SyntaxException.Reason.UNSUPPORTED, pos);
            }
            // 段之间：下标前可以直接接，否则必须是点，点后面必须还有东西
            if (pos < length) {
                char next = s.charAt(pos);
                if (next == '.') {
                    pos++;
                    if (pos >= length) {
                        throw new SyntaxException(SyntaxException.Reason.EMPTY_KEY, pos);
                    }
                } else if (next != '[') {
                    throw new SyntaxException(SyntaxException.Reason.EXPECTED_DOT, pos);
                }
            }
        }
        return result.isEmpty() ? ROOT : new NbtPath(result.toArray());
    }

    /**
     * 读一段引号包着的键名
     *
     * @param s   文本
     * @param pos 开引号的位置
     * @param out 读出的键名
     * @return 闭引号后面的位置
     */
    private static int readQuoted(@Nonnull String s, int pos, @Nonnull StringBuilder out) {
        char quote = s.charAt(pos);
        int i = pos + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == BACKSLASH) {
                if (i + 1 >= s.length()) {
                    throw new SyntaxException(SyntaxException.Reason.UNTERMINATED_QUOTE, pos);
                }
                char escaped = s.charAt(i + 1);
                if (escaped != quote && escaped != BACKSLASH) {
                    throw new SyntaxException(SyntaxException.Reason.BAD_ESCAPE, i);
                }
                out.append(escaped);
                i += 2;
            } else if (c == quote) {
                return i + 1;
            } else {
                out.append(c);
                i++;
            }
        }
        throw new SyntaxException(SyntaxException.Reason.UNTERMINATED_QUOTE, pos);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NbtPath other) || other.hash != hash) {
            return false;
        }
        return Arrays.equals(segments, other.segments);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
