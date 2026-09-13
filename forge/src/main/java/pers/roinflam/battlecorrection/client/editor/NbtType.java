package pers.roinflam.battlecorrection.client.editor;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * NBT 的 12 种标签类型：ID、显示名、树里的短标记、默认值、文本 ↔ 标签的互转
 * <p>
 * 原版没有把这些放在一个枚举里，树编辑器要按类型建节点、解析输入，集中在这里方便。
 * {@link #parse(String)} 和 {@link #toEditable(Tag)} 是编辑框里那种"裸"文本（不带 5b、1.5f 这种后缀），
 * 数组用逗号分隔。
 */
public enum NbtType {

    COMPOUND(Tag.TAG_COMPOUND, "compound", "{}"),
    LIST(Tag.TAG_LIST, "list", "[]"),
    STRING(Tag.TAG_STRING, "string", "str"),
    BYTE(Tag.TAG_BYTE, "byte", "b"),
    SHORT(Tag.TAG_SHORT, "short", "s"),
    INT(Tag.TAG_INT, "int", "i"),
    LONG(Tag.TAG_LONG, "long", "l"),
    FLOAT(Tag.TAG_FLOAT, "float", "f"),
    DOUBLE(Tag.TAG_DOUBLE, "double", "d"),
    BYTE_ARRAY(Tag.TAG_BYTE_ARRAY, "byte_array", "b[]"),
    INT_ARRAY(Tag.TAG_INT_ARRAY, "int_array", "i[]"),
    LONG_ARRAY(Tag.TAG_LONG_ARRAY, "long_array", "l[]");

    private final byte id;
    private final String key;
    private final String badge;

    NbtType(byte id, @Nonnull String key, @Nonnull String badge) {
        this.id = id;
        this.key = key;
        this.badge = badge;
    }

    /**
     * 原版的标签类型 ID
     *
     * @return ID
     */
    public byte id() {
        return id;
    }

    /**
     * 树里显示在名称前面的短标记
     *
     * @return 标记
     */
    @Nonnull
    public String badge() {
        return badge;
    }

    /**
     * 显示名
     *
     * @return 组件
     */
    @Nonnull
    public Component displayName() {
        return Component.translatable("gui.battlecorrection.editor.tree.type." + key);
    }

    /**
     * 是不是容器（复合标签或列表）
     *
     * @return true = 容器
     */
    public boolean isContainer() {
        return this == COMPOUND || this == LIST;
    }

    /**
     * 是不是数组
     *
     * @return true = 数组
     */
    public boolean isArray() {
        return this == BYTE_ARRAY || this == INT_ARRAY || this == LONG_ARRAY;
    }

    /**
     * 按类型 ID 查
     *
     * @param id 类型 ID
     * @return 类型；未知 ID 返回 null
     */
    @Nullable
    public static NbtType byId(byte id) {
        for (NbtType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return null;
    }

    /**
     * 一个标签对应的类型
     *
     * @param tag 标签
     * @return 类型；未知返回 null（正常不会）
     */
    @Nullable
    public static NbtType of(@Nonnull Tag tag) {
        return byId(tag.getId());
    }

    /**
     * 这个类型的默认值标签（新建节点用）
     *
     * @return 新标签
     */
    @Nonnull
    public Tag createDefault() {
        switch (this) {
            case COMPOUND:
                return new CompoundTag();
            case LIST:
                return new ListTag();
            case STRING:
                return StringTag.valueOf("");
            case BYTE:
                return ByteTag.valueOf((byte) 0);
            case SHORT:
                return ShortTag.valueOf((short) 0);
            case INT:
                return IntTag.valueOf(0);
            case LONG:
                return LongTag.valueOf(0L);
            case FLOAT:
                return FloatTag.valueOf(0.0F);
            case DOUBLE:
                return DoubleTag.valueOf(0.0D);
            case BYTE_ARRAY:
                return new ByteArrayTag(new byte[0]);
            case INT_ARRAY:
                return new IntArrayTag(new int[0]);
            default:
                return new LongArrayTag(new long[0]);
        }
    }

    /**
     * 标签 → 编辑框里的文本
     * <p>
     * 容器返回 null（不能在编辑框里改）；数字不带后缀；数组用 ", " 分隔。
     *
     * @param tag 标签
     * @return 文本；容器返回 null
     */
    @Nullable
    public static String toEditable(@Nonnull Tag tag) {
        if (tag instanceof CompoundTag || tag instanceof ListTag) {
            return null;
        }
        if (tag instanceof StringTag) {
            return tag.getAsString();
        }
        if (tag instanceof NumericTag numeric) {
            return numeric.getAsNumber().toString();
        }
        if (tag instanceof ByteArrayTag array) {
            return joinNumbers(array.getAsByteArray());
        }
        if (tag instanceof IntArrayTag array) {
            return joinNumbers(array.getAsIntArray());
        }
        if (tag instanceof LongArrayTag array) {
            return joinNumbers(array.getAsLongArray());
        }
        return tag.getAsString();
    }

    /**
     * 编辑框文本 → 这个类型的标签
     *
     * @param text 文本
     * @return 标签
     * @throws IllegalArgumentException 文本不合法时抛出，消息可直接显示给用户
     */
    @Nonnull
    public Tag parse(@Nonnull String text) {
        String trimmed = text.trim();
        try {
            switch (this) {
                case STRING:
                    return StringTag.valueOf(text);
                case BYTE: {
                    if (trimmed.equalsIgnoreCase("true")) {
                        return ByteTag.valueOf((byte) 1);
                    }
                    if (trimmed.equalsIgnoreCase("false")) {
                        return ByteTag.valueOf((byte) 0);
                    }
                    return ByteTag.valueOf((byte) checkRange(Long.parseLong(trimmed), Byte.MIN_VALUE, Byte.MAX_VALUE));
                }
                case SHORT:
                    return ShortTag.valueOf((short) checkRange(Long.parseLong(trimmed), Short.MIN_VALUE, Short.MAX_VALUE));
                case INT:
                    return IntTag.valueOf((int) checkRange(Long.parseLong(trimmed), Integer.MIN_VALUE, Integer.MAX_VALUE));
                case LONG:
                    return LongTag.valueOf(Long.parseLong(trimmed));
                case FLOAT:
                    return FloatTag.valueOf(Float.parseFloat(trimmed));
                case DOUBLE:
                    return DoubleTag.valueOf(Double.parseDouble(trimmed));
                case BYTE_ARRAY: {
                    List<Long> values = splitNumbers(trimmed);
                    byte[] result = new byte[values.size()];
                    for (int i = 0; i < result.length; i++) {
                        result[i] = (byte) checkRange(values.get(i), Byte.MIN_VALUE, Byte.MAX_VALUE);
                    }
                    return new ByteArrayTag(result);
                }
                case INT_ARRAY: {
                    List<Long> values = splitNumbers(trimmed);
                    int[] result = new int[values.size()];
                    for (int i = 0; i < result.length; i++) {
                        result[i] = (int) checkRange(values.get(i), Integer.MIN_VALUE, Integer.MAX_VALUE);
                    }
                    return new IntArrayTag(result);
                }
                case LONG_ARRAY: {
                    List<Long> values = splitNumbers(trimmed);
                    long[] result = new long[values.size()];
                    for (int i = 0; i < result.length; i++) {
                        result[i] = values.get(i);
                    }
                    return new LongArrayTag(result);
                }
                default:
                    throw new IllegalArgumentException(key);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(text.trim(), e);
        }
    }

    /**
     * 范围检查
     *
     * @param value 值
     * @param min   下限
     * @param max   上限
     * @return 原值
     * @throws IllegalArgumentException 越界时抛出
     */
    private static long checkRange(long value, long min, long max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(value + " ∉ [" + min + ", " + max + "]");
        }
        return value;
    }

    /**
     * 按逗号/空格拆成整数列表
     *
     * @param text 文本
     * @return 整数列表（空文本返回空列表）
     */
    @Nonnull
    private static List<Long> splitNumbers(@Nonnull String text) {
        List<Long> result = new ArrayList<>();
        if (text.isEmpty()) {
            return result;
        }
        for (String part : text.split("[,\\s]+")) {
            if (!part.isEmpty()) {
                result.add(Long.parseLong(part));
            }
        }
        return result;
    }

    @Nonnull
    private static String joinNumbers(@Nonnull byte[] values) {
        StringBuilder result = new StringBuilder();
        for (byte value : values) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(value);
        }
        return result.toString();
    }

    @Nonnull
    private static String joinNumbers(@Nonnull int[] values) {
        StringBuilder result = new StringBuilder();
        for (int value : values) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(value);
        }
        return result.toString();
    }

    @Nonnull
    private static String joinNumbers(@Nonnull long[] values) {
        StringBuilder result = new StringBuilder();
        for (long value : values) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(value);
        }
        return result.toString();
    }
}
