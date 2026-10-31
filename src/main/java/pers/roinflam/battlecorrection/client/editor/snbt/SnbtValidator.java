package pers.roinflam.battlecorrection.client.editor.snbt;

import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.regex.Pattern;

/**
 * SNBT 校验：用原版 JsonToNBT 解析，失败时给出出错位置的行列号和原因
 * <p>
 * 能不能解析只听 JsonToNBT 的，和"应用"用的是同一个解析器，所以这里说有效，应用时就一定能解析过去；
 * 结果里带着解析出来的 NBT，文本没再改动的话应用时直接复用，2MB 的文本不用在主线程再解析一遍。
 * <p>
 * 1.12.2 的 NBTException 只有一句拼好的英文（"原因 at: ...前 35 个字符<--[HERE]"），不带出错位置。
 * 所以解析失败时再用 {@link Reader} 按 JsonToNBT 的读法原样走一遍（只记类型、不建标签），停在同一个地方，
 * 拿到位置和原因；原因同时给出翻译键，界面按当前语言显示。
 * 纯数据处理，可以在后台线程对文本快照调用。
 */
public final class SnbtValidator {

    /**
     * 嵌套太深导致栈溢出时的消息键（原版解析器是递归的，没有深度限制）
     */
    public static final String KEY_TOO_DEEP = "gui.battlecorrection.editor.snbt.error.too_deep";

    private static final String ERROR = "gui.battlecorrection.editor.snbt.error.";
    private static final Object[] NO_ARGS = new Object[0];

    /**
     * 校验结果
     */
    public static final class Result {

        @Nullable
        private final NBTTagCompound tag;
        private final int offset;
        private final int line;
        private final int column;
        @Nonnull
        private final String message;
        @Nullable
        private final String messageKey;
        @Nonnull
        private final Object[] messageArgs;

        /**
         * @param tag         解析成功时的 NBT
         * @param offset      出错位置（全文下标），成功时 -1
         * @param line        出错行（从 1 起）
         * @param column      出错列（从 1 起）
         * @param message     出错原因（原版 JsonToNBT 的英文原文，不含位置那一截），成功时为空串
         * @param messageKey  原因的翻译键（界面按当前语言显示），没有时为 null，界面直接显示 message
         * @param messageArgs 翻译参数
         */
        public Result(@Nullable NBTTagCompound tag, int offset, int line, int column, @Nonnull String message,
                      @Nullable String messageKey, @Nonnull Object... messageArgs) {
            this.tag = tag;
            this.offset = offset;
            this.line = line;
            this.column = column;
            this.message = message;
            this.messageKey = messageKey;
            this.messageArgs = messageArgs;
        }

        public boolean ok() {
            return tag != null;
        }

        @Nullable
        public NBTTagCompound tag() {
            return tag;
        }

        public int offset() {
            return offset;
        }

        public int line() {
            return line;
        }

        public int column() {
            return column;
        }

        @Nonnull
        public String message() {
            return message;
        }

        @Nullable
        public String messageKey() {
            return messageKey;
        }

        @Nonnull
        public Object[] messageArgs() {
            return messageArgs;
        }
    }

    private SnbtValidator() {
    }

    /**
     * 解析
     *
     * @param text SNBT 文本
     * @return 结果
     */
    @Nonnull
    public static Result validate(@Nonnull String text) {
        try {
            return new Result(JsonToNBT.getTagFromJson(text), -1, 0, 0, "", null, NO_ARGS);
        } catch (NBTException e) {
            return diagnose(text, e);
        } catch (StackOverflowError e) {
            return error(text, 0, "", KEY_TOO_DEEP, NO_ARGS);
        } catch (RuntimeException e) {
            // JsonToNBT 自己只会抛 NBTException；万一碰上别的，照样当解析失败，不让它冒到界面上去
            return error(text, text.length(), String.valueOf(e), null, NO_ARGS);
        }
    }

    @Nonnull
    private static Result diagnose(@Nonnull String text, @Nonnull NBTException e) {
        Problem problem = locate(text);
        if (problem == null) {
            // 照着走一遍却没找到错（不该发生）：显示原版的原文，位置放在文末
            String message = String.valueOf(e.getMessage());
            int at = message.lastIndexOf(" at: ");
            return error(text, text.length(), at >= 0 ? message.substring(0, at) : message, null, NO_ARGS);
        }
        return error(text, Math.min(problem.marker, text.length()), problem.message, problem.key, problem.args);
    }

    /**
     * 按 JsonToNBT 的读法找出第一个错误
     *
     * @param text 文本
     * @return 错误；能解析时返回 null；嵌套太深读不完时也返回 null
     */
    @Nullable
    static Problem locate(@Nonnull String text) {
        try {
            return new Reader(text).find();
        } catch (StackOverflowError e) {
            return null;
        }
    }

    @Nonnull
    private static Result error(@Nonnull String text, int offset, @Nonnull String reason, @Nullable String key,
                                @Nonnull Object[] args) {
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        return new Result(null, offset, line, offset - lineStart + 1, reason, key, args);
    }

    // 照 JsonToNBT 走一遍

    /**
     * 一处解析错误
     */
    static final class Problem {
        /**
         * JsonToNBT 的原因原文（NBTException 消息里 " at: " 之前那一截）
         */
        final String message;
        /**
         * 翻译键
         */
        final String key;
        /**
         * 翻译参数
         */
        final Object[] args;
        /**
         * JsonToNBT 报错时自己的游标（它拼进消息的那一截就截到这里）
         */
        final int cursor;
        /**
         * 编辑框里标红的位置：出错的那个字符（多数情况下就在游标前一格，或者是出错元素的开头）
         */
        final int marker;

        Problem(@Nonnull String message, @Nonnull String key, @Nonnull Object[] args, int cursor, int marker) {
            this.message = message;
            this.key = key;
            this.args = args;
            this.cursor = cursor;
            this.marker = marker;
        }

        /**
         * 拼出和 NBTException.getMessage() 一样的文字（核对用）
         */
        @Nonnull
        String vanillaMessage(@Nonnull String text) {
            StringBuilder out = new StringBuilder(message).append(" at: ");
            int end = Math.min(text.length(), cursor);
            if (end > 35) {
                out.append("...");
            }
            out.append(text, Math.max(0, end - 35), end);
            return out.append("<--[HERE]").toString();
        }
    }

    /**
     * 解析中止（带着错误往外抛）
     */
    private static final class Failure extends Exception {

        private static final long serialVersionUID = 1L;

        final Problem problem;

        Failure(@Nonnull Problem problem) {
            super(null, null, false, false);
            this.problem = problem;
        }
    }

    /**
     * JsonToNBT 的逐行照搬：同样的方法、同样的顺序、同样的游标移动，每个值只返回类型编号
     * <p>
     * 报错的地方和原因都和原版一一对应；另外记下更适合在编辑框里标出来的位置（{@link Problem#marker}）。
     */
    private static final class Reader {

        private static final Pattern DOUBLE_PATTERN_NOSUFFIX = Pattern.compile("[-+]?(?:[0-9]+[.]|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?", Pattern.CASE_INSENSITIVE);
        private static final Pattern DOUBLE_PATTERN = Pattern.compile("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?d", Pattern.CASE_INSENSITIVE);
        private static final Pattern FLOAT_PATTERN = Pattern.compile("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?f", Pattern.CASE_INSENSITIVE);
        private static final Pattern BYTE_PATTERN = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)b", Pattern.CASE_INSENSITIVE);
        private static final Pattern LONG_PATTERN = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)l", Pattern.CASE_INSENSITIVE);
        private static final Pattern SHORT_PATTERN = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)s", Pattern.CASE_INSENSITIVE);
        private static final Pattern INT_PATTERN = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)");

        private static final int TAG_BYTE = 1;
        private static final int TAG_SHORT = 2;
        private static final int TAG_INT = 3;
        private static final int TAG_LONG = 4;
        private static final int TAG_FLOAT = 5;
        private static final int TAG_DOUBLE = 6;
        private static final int TAG_BYTE_ARRAY = 7;
        private static final int TAG_STRING = 8;
        private static final int TAG_LIST = 9;
        private static final int TAG_COMPOUND = 10;
        private static final int TAG_INT_ARRAY = 11;
        private static final int TAG_LONG_ARRAY = 12;

        private final String string;
        private int cursor;

        Reader(@Nonnull String string) {
            this.string = string;
        }

        @Nullable
        Problem find() {
            try {
                readSingleStruct();
                return null;
            } catch (Failure failure) {
                return failure.problem;
            }
        }

        private void readSingleStruct() throws Failure {
            readStruct();
            skipWhitespace();
            if (canRead()) {
                int at = cursor;
                ++cursor;
                throw fail("Trailing data found", "trailing", at);
            }
        }

        /**
         * @return 键是不是空的
         */
        private boolean readKey() throws Failure {
            skipWhitespace();
            if (!canRead()) {
                throw fail("Expected key", "expected_key", cursor);
            }
            return peek() == '"' ? readQuotedString() : readString() == 0;
        }

        private int readTypedValue() throws Failure {
            skipWhitespace();
            if (peek() == '"') {
                readQuotedString();
                return TAG_STRING;
            }
            int start = cursor;
            if (readString() == 0) {
                throw fail("Expected value", "expected_value", cursor);
            }
            return type(string.substring(start, cursor));
        }

        /**
         * JsonToNBT.type：按顺序试各个数字写法，整数越界（NumberFormatException）退化成字符串
         */
        private static int type(@Nonnull String value) {
            try {
                if (FLOAT_PATTERN.matcher(value).matches()) {
                    Float.parseFloat(value.substring(0, value.length() - 1));
                    return TAG_FLOAT;
                }
                if (BYTE_PATTERN.matcher(value).matches()) {
                    Byte.parseByte(value.substring(0, value.length() - 1));
                    return TAG_BYTE;
                }
                if (LONG_PATTERN.matcher(value).matches()) {
                    Long.parseLong(value.substring(0, value.length() - 1));
                    return TAG_LONG;
                }
                if (SHORT_PATTERN.matcher(value).matches()) {
                    Short.parseShort(value.substring(0, value.length() - 1));
                    return TAG_SHORT;
                }
                if (INT_PATTERN.matcher(value).matches()) {
                    Integer.parseInt(value);
                    return TAG_INT;
                }
                if (DOUBLE_PATTERN.matcher(value).matches()) {
                    Double.parseDouble(value.substring(0, value.length() - 1));
                    return TAG_DOUBLE;
                }
                if (DOUBLE_PATTERN_NOSUFFIX.matcher(value).matches()) {
                    Double.parseDouble(value);
                    return TAG_DOUBLE;
                }
                if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                    return TAG_BYTE;
                }
            } catch (NumberFormatException ignored) {
                // 和原版一样：越界的整数当字符串
            }
            return TAG_STRING;
        }

        /**
         * @return 字符串内容是不是空的
         */
        private boolean readQuotedString() throws Failure {
            int start = ++cursor;
            boolean escaped = false;
            while (canRead()) {
                char c = pop();
                if (escaped) {
                    if (c != '\\' && c != '"') {
                        throw fail("Invalid escape of '" + c + "'", "invalid_escape", cursor - 1, visible(c));
                    }
                    escaped = false;
                } else {
                    if (c == '\\') {
                        escaped = true;
                        continue;
                    }
                    if (c == '"') {
                        return cursor - 1 == start;
                    }
                }
            }
            throw fail("Missing termination quote", "unterminated", cursor);
        }

        /**
         * @return 读到的字符数
         */
        private int readString() {
            int start = cursor;
            while (canRead() && SnbtLexer.isUnquotedChar(peek())) {
                ++cursor;
            }
            return cursor - start;
        }

        private int readValue() throws Failure {
            skipWhitespace();
            if (!canRead()) {
                throw fail("Expected value", "expected_value", cursor);
            }
            char c = peek();
            if (c == '{') {
                return readStruct();
            }
            return c == '[' ? readList() : readTypedValue();
        }

        private int readList() throws Failure {
            return canRead(2) && peek(1) != '"' && peek(2) == ';' ? readArrayTag() : readListTag();
        }

        private int readStruct() throws Failure {
            expect('{');
            skipWhitespace();
            while (canRead() && peek() != '}') {
                int keyStart = cursor;
                if (readKey()) {
                    throw fail("Expected non-empty key", "empty_key", keyStart);
                }
                expect(':');
                readValue();
                if (!hasElementSeparator()) {
                    break;
                }
                if (!canRead()) {
                    throw fail("Expected key", "expected_key", cursor);
                }
            }
            expect('}');
            return TAG_COMPOUND;
        }

        private int readListTag() throws Failure {
            expect('[');
            skipWhitespace();
            if (!canRead()) {
                throw fail("Expected value", "expected_value", cursor);
            }
            int listType = -1;
            while (peek() != ']') {
                int elementStart = cursor;
                int type = readValue();
                if (listType < 0) {
                    listType = type;
                } else if (type != listType) {
                    String inserted = NBTBase.getTagTypeName(type);
                    String expected = NBTBase.getTagTypeName(listType);
                    throw fail("Unable to insert " + inserted + " into ListTag of type " + expected, "mixed_list",
                            elementStart, inserted, expected);
                }
                if (!hasElementSeparator()) {
                    break;
                }
                if (!canRead()) {
                    throw fail("Expected value", "expected_value", cursor);
                }
            }
            expect(']');
            return TAG_LIST;
        }

        private int readArrayTag() throws Failure {
            expect('[');
            int typeAt = cursor;
            char type = pop();
            pop();
            skipWhitespace();
            if (!canRead()) {
                throw fail("Expected value", "expected_value", cursor);
            }
            if (type == 'B') {
                readArray(TAG_BYTE_ARRAY, TAG_BYTE);
                return TAG_BYTE_ARRAY;
            }
            if (type == 'L') {
                readArray(TAG_LONG_ARRAY, TAG_LONG);
                return TAG_LONG_ARRAY;
            }
            if (type == 'I') {
                readArray(TAG_INT_ARRAY, TAG_INT);
                return TAG_INT_ARRAY;
            }
            throw fail("Invalid array type '" + type + "' found", "invalid_array", typeAt, visible(type));
        }

        private void readArray(int arrayType, int elementType) throws Failure {
            while (true) {
                if (peek() != ']') {
                    int elementStart = cursor;
                    int type = readValue();
                    if (type != elementType) {
                        String inserted = NBTBase.getTagTypeName(type);
                        String array = NBTBase.getTagTypeName(arrayType);
                        throw fail("Unable to insert " + inserted + " into " + array, "mixed_array", elementStart,
                                inserted, array);
                    }
                    if (hasElementSeparator()) {
                        if (!canRead()) {
                            throw fail("Expected value", "expected_value", cursor);
                        }
                        continue;
                    }
                }
                expect(']');
                return;
            }
        }

        private void skipWhitespace() {
            while (canRead() && Character.isWhitespace(peek())) {
                ++cursor;
            }
        }

        private boolean hasElementSeparator() {
            skipWhitespace();
            if (canRead() && peek() == ',') {
                ++cursor;
                skipWhitespace();
                return true;
            }
            return false;
        }

        private void expect(char c) throws Failure {
            skipWhitespace();
            boolean can = canRead();
            if (can && peek() == c) {
                ++cursor;
                return;
            }
            // 原版这里报的游标是出错字符之后一格
            String got = can ? String.valueOf(peek()) : "<EOF>";
            Problem problem = can
                    ? new Problem("Expected '" + c + "' but got '" + got + "'", ERROR + "expected_char",
                    new Object[]{String.valueOf(c), visible(peek())}, cursor + 1, cursor)
                    : new Problem("Expected '" + c + "' but got '" + got + "'", ERROR + "expected_eof",
                    new Object[]{String.valueOf(c)}, cursor + 1, cursor);
            throw new Failure(problem);
        }

        private boolean canRead(int ahead) {
            return cursor + ahead < string.length();
        }

        private boolean canRead() {
            return canRead(0);
        }

        private char peek(int ahead) {
            return string.charAt(cursor + ahead);
        }

        private char peek() {
            return peek(0);
        }

        private char pop() {
            return string.charAt(cursor++);
        }

        @Nonnull
        private Failure fail(@Nonnull String message, @Nonnull String key, int marker, @Nonnull Object... args) {
            return new Failure(new Problem(message, ERROR + key, args, cursor, marker));
        }

        /**
         * 原因里引用的字符：换行、制表这类看不见的写成转义形式
         */
        @Nonnull
        private static String visible(char c) {
            switch (c) {
                case '\n':
                    return "\\n";
                case '\r':
                    return "\\r";
                case '\t':
                    return "\\t";
                default:
                    return c < 0x20 || c == 0x7F ? String.format("U+%04X", (int) c) : String.valueOf(c);
            }
        }
    }
}
