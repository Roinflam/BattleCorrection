package pers.roinflam.battlecorrection.client.editor;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagLongArray;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;
import pers.roinflam.battlecorrection.client.editor.ui.Component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * NBT 的 12 种标签类型：ID、显示名、树里的短标记、默认值、文本 ↔ 标签的互转
 * <p>
 * 原版没有把这些放在一个枚举里，树编辑器要按类型建节点、解析输入，集中在这里方便。
 * {@link #parse(String)} 和 {@link #toEditable(NBTBase)} 是编辑框里那种"裸"文本（不带 5b、1.5f 这种后缀），
 * 数组用逗号分隔。
 * <p>
 * 1.12.2 的 NBTTagLongArray 没有取数据的方法，{@link #longArray} 先反射读字段，读不到再解析 toString 的输出。
 */
public enum NbtType {

    COMPOUND(Constants.NBT.TAG_COMPOUND, "compound", "{}"),
    LIST(Constants.NBT.TAG_LIST, "list", "[]"),
    STRING(Constants.NBT.TAG_STRING, "string", "str"),
    BYTE(Constants.NBT.TAG_BYTE, "byte", "b"),
    SHORT(Constants.NBT.TAG_SHORT, "short", "s"),
    INT(Constants.NBT.TAG_INT, "int", "i"),
    LONG(Constants.NBT.TAG_LONG, "long", "l"),
    FLOAT(Constants.NBT.TAG_FLOAT, "float", "f"),
    DOUBLE(Constants.NBT.TAG_DOUBLE, "double", "d"),
    BYTE_ARRAY(Constants.NBT.TAG_BYTE_ARRAY, "byte_array", "b[]"),
    INT_ARRAY(Constants.NBT.TAG_INT_ARRAY, "int_array", "i[]"),
    LONG_ARRAY(Constants.NBT.TAG_LONG_ARRAY, "long_array", "l[]");

    /**
     * 按类型 ID 查表；树每帧每行都要查类型，values() 每次都会复制一份数组，不能在那里用
     */
    private static final NbtType[] BY_ID = new NbtType[Constants.NBT.TAG_LONG_ARRAY + 1];

    /**
     * NBTTagLongArray 的私有字段 data（开发环境是 MCP 名，正式环境是 SRG 名）；找不到为 null
     */
    @Nullable
    private static final Field LONG_ARRAY_DATA = findLongArrayField();

    static {
        for (NbtType type : values()) {
            BY_ID[type.id] = type;
        }
    }

    private final byte id;
    private final String key;
    private final String badge;

    NbtType(int id, @Nonnull String key, @Nonnull String badge) {
        this.id = (byte) id;
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
    public static NbtType byId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : null;
    }

    /**
     * 是不是数字（byte / short / int / long / float / double）
     *
     * @return true = 数字
     */
    public boolean isNumber() {
        return this == BYTE || this == SHORT || this == INT || this == LONG || this == FLOAT || this == DOUBLE;
    }

    /**
     * 整数类型的取值范围文字（"-128 ~ 127"），给输入提示用
     *
     * @return 范围；不是整数类型返回 null
     */
    @Nullable
    public String integerRange() {
        switch (this) {
            case BYTE:
                return Byte.MIN_VALUE + " ~ " + Byte.MAX_VALUE;
            case SHORT:
                return Short.MIN_VALUE + " ~ " + Short.MAX_VALUE;
            case INT:
                return Integer.MIN_VALUE + " ~ " + Integer.MAX_VALUE;
            case LONG:
                return Long.MIN_VALUE + " ~ " + Long.MAX_VALUE;
            default:
                return null;
        }
    }

    /**
     * 一个标签对应的类型
     *
     * @param tag 标签
     * @return 类型；未知返回 null（正常不会）
     */
    @Nullable
    public static NbtType of(@Nonnull NBTBase tag) {
        return byId(tag.getId());
    }

    /**
     * 这个类型的默认值标签（新建节点用）
     *
     * @return 新标签
     */
    @Nonnull
    public NBTBase createDefault() {
        switch (this) {
            case COMPOUND:
                return new NBTTagCompound();
            case LIST:
                return new NBTTagList();
            case STRING:
                return new NBTTagString("");
            case BYTE:
                return new NBTTagByte((byte) 0);
            case SHORT:
                return new NBTTagShort((short) 0);
            case INT:
                return new NBTTagInt(0);
            case LONG:
                return new NBTTagLong(0L);
            case FLOAT:
                return new NBTTagFloat(0.0F);
            case DOUBLE:
                return new NBTTagDouble(0.0D);
            case BYTE_ARRAY:
                return new NBTTagByteArray(new byte[0]);
            case INT_ARRAY:
                return new NBTTagIntArray(new int[0]);
            default:
                return new NBTTagLongArray(new long[0]);
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
    public static String toEditable(@Nonnull NBTBase tag) {
        if (tag instanceof NBTTagCompound || tag instanceof NBTTagList) {
            return null;
        }
        if (tag instanceof NBTTagString) {
            return ((NBTTagString) tag).getString();
        }
        if (tag instanceof NBTPrimitive) {
            return numberText((NBTPrimitive) tag);
        }
        if (tag instanceof NBTTagByteArray) {
            return joinNumbers(((NBTTagByteArray) tag).getByteArray());
        }
        if (tag instanceof NBTTagIntArray) {
            return joinNumbers(((NBTTagIntArray) tag).getIntArray());
        }
        if (tag instanceof NBTTagLongArray) {
            return joinNumbers(longArray((NBTTagLongArray) tag));
        }
        return tag.toString();
    }

    /**
     * 数字标签的值，不带后缀（按标签本身的类型写：5、1.5、1.0E10）
     *
     * @param tag 数字标签
     * @return 文本
     */
    @Nonnull
    public static String numberText(@Nonnull NBTPrimitive tag) {
        switch (tag.getId()) {
            case Constants.NBT.TAG_BYTE:
                return Byte.toString(tag.getByte());
            case Constants.NBT.TAG_SHORT:
                return Short.toString(tag.getShort());
            case Constants.NBT.TAG_INT:
                return Integer.toString(tag.getInt());
            case Constants.NBT.TAG_FLOAT:
                return Float.toString(tag.getFloat());
            case Constants.NBT.TAG_DOUBLE:
                return Double.toString(tag.getDouble());
            default:
                return Long.toString(tag.getLong());
        }
    }

    /**
     * 数组标签的元素个数
     *
     * @param tag 标签
     * @return 个数；不是数组返回 -1
     */
    public static int arraySize(@Nonnull NBTBase tag) {
        if (tag instanceof NBTTagByteArray) {
            return ((NBTTagByteArray) tag).getByteArray().length;
        }
        if (tag instanceof NBTTagIntArray) {
            return ((NBTTagIntArray) tag).getIntArray().length;
        }
        if (tag instanceof NBTTagLongArray) {
            return longArray((NBTTagLongArray) tag).length;
        }
        return -1;
    }

    /**
     * 读 long 数组的数据（只读，别改返回的数组：反射拿到的是标签里的原数组）
     *
     * @param tag 标签
     * @return 数据
     */
    @Nonnull
    public static long[] longArray(@Nonnull NBTTagLongArray tag) {
        Field field = LONG_ARRAY_DATA;
        if (field != null) {
            try {
                Object data = field.get(tag);
                if (data instanceof long[]) {
                    return (long[]) data;
                }
            } catch (IllegalAccessException ignored) {
                // 退回解析文本
            }
        }
        return parseLongArrayText(tag.toString());
    }

    @Nullable
    private static Field findLongArrayField() {
        for (String name : new String[]{"data", "field_193587_b"}) {
            try {
                Field field = NBTTagLongArray.class.getDeclaredField(name);
                if (field.getType() == long[].class) {
                    field.setAccessible(true);
                    return field;
                }
            } catch (NoSuchFieldException | SecurityException ignored) {
                // 换下一个名字
            }
        }
        // 两个名字都没有（别的映射）：按类型找唯一的 long[] 字段
        try {
            for (Field field : NBTTagLongArray.class.getDeclaredFields()) {
                if (field.getType() == long[].class) {
                    field.setAccessible(true);
                    return field;
                }
            }
        } catch (SecurityException ignored) {
            // 只能解析文本了
        }
        return null;
    }

    /**
     * 解析 NBTTagLongArray#toString 的输出（{@code [L;1L,2L]}）
     *
     * @param text 文本
     * @return 数据；格式不对时返回能读出来的部分
     */
    @Nonnull
    static long[] parseLongArrayText(@Nonnull String text) {
        int start = text.indexOf(';');
        int end = text.lastIndexOf(']');
        if (start < 0 || end <= start + 1) {
            return new long[0];
        }
        String[] parts = text.substring(start + 1, end).split(",");
        long[] result = new long[parts.length];
        int count = 0;
        for (String part : parts) {
            String number = part.trim();
            if (number.endsWith("L") || number.endsWith("l")) {
                number = number.substring(0, number.length() - 1);
            }
            long value;
            try {
                value = Long.parseLong(number);
            } catch (NumberFormatException e) {
                continue;
            }
            result[count++] = value;
        }
        if (count == result.length) {
            return result;
        }
        long[] trimmed = new long[count];
        System.arraycopy(result, 0, trimmed, 0, count);
        return trimmed;
    }

    /**
     * 编辑框文本 → 这个类型的标签
     *
     * @param text 文本
     * @return 标签
     * @throws IllegalArgumentException 文本不合法时抛出，消息可直接显示给用户
     */
    @Nonnull
    public NBTBase parse(@Nonnull String text) {
        String trimmed = text.trim();
        try {
            switch (this) {
                case STRING:
                    return new NBTTagString(text);
                case BYTE: {
                    if (trimmed.equalsIgnoreCase("true")) {
                        return new NBTTagByte((byte) 1);
                    }
                    if (trimmed.equalsIgnoreCase("false")) {
                        return new NBTTagByte((byte) 0);
                    }
                    return new NBTTagByte((byte) checkRange(Long.parseLong(trimmed), Byte.MIN_VALUE, Byte.MAX_VALUE));
                }
                case SHORT:
                    return new NBTTagShort((short) checkRange(Long.parseLong(trimmed), Short.MIN_VALUE, Short.MAX_VALUE));
                case INT:
                    return new NBTTagInt((int) checkRange(Long.parseLong(trimmed), Integer.MIN_VALUE, Integer.MAX_VALUE));
                case LONG:
                    return new NBTTagLong(Long.parseLong(trimmed));
                case FLOAT:
                    return new NBTTagFloat(Float.parseFloat(trimmed));
                case DOUBLE:
                    return new NBTTagDouble(Double.parseDouble(trimmed));
                case BYTE_ARRAY: {
                    List<Long> values = splitNumbers(trimmed);
                    byte[] result = new byte[values.size()];
                    for (int i = 0; i < result.length; i++) {
                        result[i] = (byte) checkRange(values.get(i), Byte.MIN_VALUE, Byte.MAX_VALUE);
                    }
                    return new NBTTagByteArray(result);
                }
                case INT_ARRAY: {
                    List<Long> values = splitNumbers(trimmed);
                    int[] result = new int[values.size()];
                    for (int i = 0; i < result.length; i++) {
                        result[i] = (int) checkRange(values.get(i), Integer.MIN_VALUE, Integer.MAX_VALUE);
                    }
                    return new NBTTagIntArray(result);
                }
                case LONG_ARRAY: {
                    List<Long> values = splitNumbers(trimmed);
                    long[] result = new long[values.size()];
                    for (int i = 0; i < result.length; i++) {
                        result[i] = values.get(i);
                    }
                    return new NBTTagLongArray(result);
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
