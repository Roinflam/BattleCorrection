package pers.roinflam.battlecorrection.client.editor.snbt;

import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagEnd;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLongArray;
import net.minecraft.nbt.NBTTagString;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SNBT 的格式化（缩进换行）和压缩（压成一行）
 * <p>
 * 1.12.2 没有带缩进的 SNBT 输出（NBTBase.toString 只出一行，而且键是哈希顺序），格式化是自己写的：
 * 排版照后来版本原版的 SnbtPrinterTagVisitor（键按字母排序，每个键值、每个列表元素各占一行，数组写在一行里），
 * 每个值的写法照 1.12.2 各个 NBTTag*.toString——字符串一律双引号、只转义 \ 和 "；键名只由 A-Z a-z 0-9 . _ + - 组成时不加引号；
 * 数字带 b s L f d 后缀，int 不带；数组写成 [B; 1B, 2B]、[I; 1, 2]、[L; 1L, 2L]。这样写出来的文本 JsonToNBT 一定读得回同样的值。
 * 唯一的例外是浮点数的无穷大：toString 写成 Infinityf，JsonToNBT 会把它读成字符串，这里写成 1e39f / 1e309d 这种读回来正好是无穷大的写法。
 * NaN 没有能读回来的写法，照 toString 原样写 NaNf / NaNd。
 * <p>
 * 缩进是两格：编辑区不宽，四格缩进到第三四层就挤得没地方放内容了；编辑时回车自动缩进也是两格，两边一致。
 * <p>
 * 压缩不走 tag.toString()（键的顺序会被打乱成哈希顺序），而是在原文上去掉字符串外面的空白，保留原来的书写顺序和数字写法。
 * 合法 SNBT 里字符串外的空白只起分隔作用，去掉不改语义；保险起见压完再解析一遍和原结果比，不一致就退回自己的单行输出。
 * 纯数据处理，可以在后台线程调用。
 */
public final class SnbtFormatter {

    /**
     * 缩进单位
     */
    public static final String INDENT = "  ";

    /**
     * 键名不加引号的条件（和 1.12.2 NBTTagCompound.handleEscape 一致）
     */
    private static final Pattern SIMPLE_KEY = Pattern.compile("[A-Za-z0-9._+-]+");

    /**
     * 原版格式化器给结构文件定的几处固定键序和不缩进的位置（路径写法同原版："{}" 是复合标签、"[]" 是列表）
     */
    private static final Map<String, List<String>> KEY_ORDER = new HashMap<>();
    private static final Set<String> NO_INDENTATION = new HashSet<>(Arrays.asList(
            "{}.size.[]", "{}.data.[].{}", "{}.palette.[].{}", "{}.entities.[].{}"));
    /**
     * 上面这些路径最多几段，更深的不用拼路径去查
     */
    private static final int SPECIAL_PATH_DEPTH = 4;

    static {
        KEY_ORDER.put("{}", Arrays.asList("DataVersion", "author", "size", "data", "entities", "palette", "palettes"));
        KEY_ORDER.put("{}.data.[].{}", Arrays.asList("pos", "state", "nbt"));
        KEY_ORDER.put("{}.entities.[].{}", Arrays.asList("blockPos", "pos"));
    }

    /**
     * NBTTagLongArray 的数据字段（1.12.2 没给取值方法），找不到时退回解析 toString
     */
    @Nullable
    private static final Field LONG_ARRAY_DATA = findLongArrayField();

    private SnbtFormatter() {
    }

    /**
     * 格式化成带缩进换行的 SNBT（键按字母排序）
     *
     * @param tag NBT
     * @return 文本
     */
    @Nonnull
    public static String pretty(@Nonnull NBTTagCompound tag) {
        StringBuilder out = new StringBuilder();
        write(out, tag, INDENT, 0, new ArrayList<>());
        return out.toString();
    }

    /**
     * 写成一行（键按字母排序，字符串外面没有空白）
     *
     * @param tag NBT
     * @return 文本
     */
    @Nonnull
    public static String singleLine(@Nonnull NBTTagCompound tag) {
        StringBuilder out = new StringBuilder();
        write(out, tag, "", 0, new ArrayList<>());
        return stripWhitespace(out);
    }

    /**
     * 压成一行
     *
     * @param text   原文（必须是能解析成 parsed 的合法 SNBT）
     * @param parsed 原文解析出来的 NBT
     * @return 一行的 SNBT
     */
    @Nonnull
    public static String compact(@Nonnull String text, @Nonnull NBTTagCompound parsed) {
        String stripped = stripWhitespace(text);
        try {
            if (JsonToNBT.getTagFromJson(stripped).equals(parsed)) {
                return stripped;
            }
        } catch (NBTException | StackOverflowError ignored) {
            // 理论上不会走到；万一去空白改变了语义，就用自己的单行输出兜底
        }
        return singleLine(parsed);
    }

    /**
     * 去掉字符串外面的所有空白（引号里的内容原样保留，转义照常跳过）
     *
     * @param text 文本
     * @return 去掉空白后的文本
     */
    @Nonnull
    public static String stripWhitespace(@Nonnull CharSequence text) {
        int length = text.length();
        StringBuilder out = new StringBuilder(length);
        boolean quoted = false;
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (quoted) {
                out.append(c);
                if (c == '\\' && i + 1 < length) {
                    out.append(text.charAt(++i));
                } else if (c == '"') {
                    quoted = false;
                }
                continue;
            }
            if (c == '"') {
                quoted = true;
                out.append(c);
            } else if (!Character.isWhitespace(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    // 输出

    /**
     * 写一个值
     *
     * @param out         输出
     * @param tag         值
     * @param indentation 缩进单位（空串 = 整个写在一行里，元素之间用 ", " 隔开）
     * @param depth       层级
     * @param path        路径（只在查固定键序 / 不缩进的位置时用）
     */
    private static void write(@Nonnull StringBuilder out, @Nonnull NBTBase tag, @Nonnull String indentation, int depth,
                              @Nonnull List<String> path) {
        switch (tag.getId()) {
            case 5:
                out.append(floatText(((NBTTagFloat) tag).getFloat()));
                break;
            case 6:
                out.append(doubleText(((NBTTagDouble) tag).getDouble()));
                break;
            case 7:
                writeByteArray(out, ((NBTTagByteArray) tag).getByteArray());
                break;
            case 8:
                out.append(NBTTagString.quoteAndEscape(((NBTTagString) tag).getString()));
                break;
            case 9:
                writeList(out, (NBTTagList) tag, indentation, depth, path);
                break;
            case 10:
                writeCompound(out, (NBTTagCompound) tag, indentation, depth, path);
                break;
            case 11:
                writeIntArray(out, ((NBTTagIntArray) tag).getIntArray());
                break;
            case 12:
                writeLongArray(out, (NBTTagLongArray) tag);
                break;
            default:
                // 字节、短整数、整数、长整数：toString 就是 1b / 1s / 1 / 1L；TAG_End 不写东西
                if (!(tag instanceof NBTTagEnd)) {
                    out.append(tag);
                }
                break;
        }
    }

    private static void writeCompound(@Nonnull StringBuilder out, @Nonnull NBTTagCompound tag, @Nonnull String indentation,
                                      int depth, @Nonnull List<String> path) {
        if (tag.hasNoTags()) {
            out.append("{}");
            return;
        }
        out.append('{');
        path.add("{}");
        String pathString = pathString(path);
        String indent = pathString != null && NO_INDENTATION.contains(pathString) ? "" : indentation;
        if (!indent.isEmpty()) {
            out.append('\n');
        }
        List<String> keys = sortedKeys(tag, pathString);
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            path.add(key);
            repeat(out, indent, depth + 1);
            out.append(SIMPLE_KEY.matcher(key).matches() ? key : NBTTagString.quoteAndEscape(key)).append(": ");
            write(out, tag.getTag(key), indent, depth + 1, path);
            path.remove(path.size() - 1);
            if (i + 1 < keys.size()) {
                out.append(',').append(indent.isEmpty() ? " " : "\n");
            }
        }
        if (!indent.isEmpty()) {
            out.append('\n');
            repeat(out, indent, depth);
        }
        out.append('}');
        path.remove(path.size() - 1);
    }

    private static void writeList(@Nonnull StringBuilder out, @Nonnull NBTTagList tag, @Nonnull String indentation,
                                  int depth, @Nonnull List<String> path) {
        int count = tag.tagCount();
        if (count == 0) {
            out.append("[]");
            return;
        }
        out.append('[');
        path.add("[]");
        String pathString = pathString(path);
        String indent = pathString != null && NO_INDENTATION.contains(pathString) ? "" : indentation;
        if (!indent.isEmpty()) {
            out.append('\n');
        }
        for (int i = 0; i < count; i++) {
            repeat(out, indent, depth + 1);
            write(out, tag.get(i), indent, depth + 1, path);
            if (i + 1 < count) {
                out.append(',').append(indent.isEmpty() ? " " : "\n");
            }
        }
        if (!indent.isEmpty()) {
            out.append('\n');
            repeat(out, indent, depth);
        }
        out.append(']');
        path.remove(path.size() - 1);
    }

    private static void writeByteArray(@Nonnull StringBuilder out, @Nonnull byte[] data) {
        out.append("[B;");
        for (int i = 0; i < data.length; i++) {
            out.append(' ').append(data[i]).append('B');
            if (i + 1 < data.length) {
                out.append(',');
            }
        }
        out.append(']');
    }

    private static void writeIntArray(@Nonnull StringBuilder out, @Nonnull int[] data) {
        out.append("[I;");
        for (int i = 0; i < data.length; i++) {
            out.append(' ').append(data[i]);
            if (i + 1 < data.length) {
                out.append(',');
            }
        }
        out.append(']');
    }

    private static void writeLongArray(@Nonnull StringBuilder out, @Nonnull NBTTagLongArray tag) {
        long[] data = longArrayData(tag);
        if (data == null) {
            // 取不到数据就原样用 toString（[L;1L,2L]，JsonToNBT 照样读得回来）
            out.append(tag);
            return;
        }
        out.append("[L;");
        for (int i = 0; i < data.length; i++) {
            out.append(' ').append(data[i]).append('L');
            if (i + 1 < data.length) {
                out.append(',');
            }
        }
        out.append(']');
    }

    /**
     * 浮点数：和 toString 一样写成 1.5f，只有无穷大换成读得回来的写法
     */
    @Nonnull
    static String floatText(float value) {
        if (Float.isInfinite(value)) {
            return value > 0 ? "1e39f" : "-1e39f";
        }
        return value + "f";
    }

    @Nonnull
    static String doubleText(double value) {
        if (Double.isInfinite(value)) {
            return value > 0 ? "1e309d" : "-1e309d";
        }
        return value + "d";
    }

    /**
     * 键排序：固定键序里有的排前面，其余按字母（String 的自然顺序，大写字母在小写前面）
     */
    @Nonnull
    private static List<String> sortedKeys(@Nonnull NBTTagCompound tag, @Nullable String pathString) {
        List<String> order = pathString == null ? null : KEY_ORDER.get(pathString);
        if (order == null) {
            List<String> keys = new ArrayList<>(tag.getKeySet());
            Collections.sort(keys);
            return keys;
        }
        Set<String> rest = new LinkedHashSet<>(tag.getKeySet());
        List<String> keys = new ArrayList<>(rest.size());
        for (String key : order) {
            if (rest.remove(key)) {
                keys.add(key);
            }
        }
        List<String> others = new ArrayList<>(rest);
        Collections.sort(others);
        keys.addAll(others);
        return keys;
    }

    /**
     * 路径拼成 "{}.data.[].{}" 的样子；超过固定键序 / 不缩进能涉及的深度时返回 null（不用查）
     */
    @Nullable
    private static String pathString(@Nonnull List<String> path) {
        if (path.size() > SPECIAL_PATH_DEPTH) {
            return null;
        }
        return String.join(".", path);
    }

    private static void repeat(@Nonnull StringBuilder out, @Nonnull String unit, int count) {
        if (unit.isEmpty()) {
            return;
        }
        for (int i = 0; i < count; i++) {
            out.append(unit);
        }
    }

    // 长整数数组

    @Nullable
    private static Field findLongArrayField() {
        // 字段名在开发环境和正式环境不同（data / field_193587_b），按类型找唯一的 long[] 字段
        for (Field field : NBTTagLongArray.class.getDeclaredFields()) {
            if (field.getType() == long[].class) {
                try {
                    field.setAccessible(true);
                    return field;
                } catch (RuntimeException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * 长整数数组的数据
     *
     * @param tag 数组
     * @return 数据；取不到时返回 null
     */
    @Nullable
    public static long[] longArrayData(@Nonnull NBTTagLongArray tag) {
        if (LONG_ARRAY_DATA != null) {
            try {
                return (long[]) LONG_ARRAY_DATA.get(tag);
            } catch (IllegalAccessException | RuntimeException ignored) {
                // 退回解析 toString
            }
        }
        return parseLongArray(tag.toString());
    }

    /**
     * 解析 NBTTagLongArray.toString 的输出（[L;1L,2L]）
     */
    @Nullable
    private static long[] parseLongArray(@Nonnull String text) {
        if (!text.startsWith("[L;") || !text.endsWith("]")) {
            return null;
        }
        String body = text.substring(3, text.length() - 1);
        if (body.isEmpty()) {
            return new long[0];
        }
        String[] parts = body.split(",");
        long[] data = new long[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.endsWith("L") || part.endsWith("l")) {
                    part = part.substring(0, part.length() - 1);
                }
                data[i] = Long.parseLong(part);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return data;
    }
}
