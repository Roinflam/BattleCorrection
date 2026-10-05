package pers.roinflam.battlecorrection.client.editor.tree;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;
import pers.roinflam.battlecorrection.client.editor.NbtType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigDecimal;

/**
 * 数值标签在 byte / short / int / long / float / double 之间互转，只做不丢值的转换
 * <p>
 * 规则：
 * <ul>
 *   <li>转整数类型：浮点数必须是整数（3.0 可以，3.5 不行），而且落在目标范围里</li>
 *   <li>整数转浮点：必须能精确表示（int 16777217 转 float 会变成 16777216，拒绝）</li>
 *   <li>double 转 float：只查范围，有效位数变少是 float 本来的样子，不拦</li>
 *   <li>float 转 double：按 float 的十进制写法转（0.1f → 0.1，而不是 0.10000000149…），看到的值不变</li>
 * </ul>
 * 拒绝时不抛异常，返回原因，由界面挑提示文字。
 */
public final class NumberConversion {

    /**
     * 不能转的原因
     */
    public enum Problem {
        /**
         * 有小数部分（或者是 NaN / 无穷），不能转成整数
         */
        NOT_INTEGER,
        /**
         * 超出目标类型的范围
         */
        OUT_OF_RANGE,
        /**
         * 目标类型表示不了这么精确的值
         */
        PRECISION
    }

    /**
     * 转换结果：成功时 tag 非空，失败时 problem 非空
     *
     * @param tag     新标签
     * @param problem 失败原因
     */
    public record Result(@Nullable Tag tag, @Nullable Problem problem) {

        @Nonnull
        static Result ok(@Nonnull Tag tag) {
            return new Result(tag, null);
        }

        @Nonnull
        static Result fail(@Nonnull Problem problem) {
            return new Result(null, problem);
        }
    }

    private static final double LONG_MIN_AS_DOUBLE = -9.223372036854775808E18;
    private static final double LONG_LIMIT_AS_DOUBLE = 9.223372036854775808E18;

    private NumberConversion() {
    }

    /**
     * 转换
     *
     * @param source 原标签
     * @param target 目标类型（必须是 {@link NbtType#isNumber()} 认的类型）
     * @return 结果
     */
    @Nonnull
    public static Result convert(@Nonnull NumericTag source, @Nonnull NbtType target) {
        boolean floating = source instanceof FloatTag || source instanceof DoubleTag;
        switch (target) {
            case BYTE:
            case SHORT:
            case INT:
            case LONG: {
                long value;
                if (floating) {
                    double d = source.getAsDouble();
                    if (!Double.isFinite(d) || d != Math.rint(d)) {
                        return Result.fail(Problem.NOT_INTEGER);
                    }
                    if (d < LONG_MIN_AS_DOUBLE || d >= LONG_LIMIT_AS_DOUBLE) {
                        return Result.fail(Problem.OUT_OF_RANGE);
                    }
                    value = (long) d;
                } else {
                    value = source.getAsLong();
                }
                return integer(value, target);
            }
            case FLOAT: {
                if (floating) {
                    double d = source.getAsDouble();
                    if (Double.isFinite(d) && Math.abs(d) > Float.MAX_VALUE) {
                        return Result.fail(Problem.OUT_OF_RANGE);
                    }
                    return Result.ok(FloatTag.valueOf((float) d));
                }
                long value = source.getAsLong();
                float f = (float) value;
                return exact(f, value) ? Result.ok(FloatTag.valueOf(f)) : Result.fail(Problem.PRECISION);
            }
            case DOUBLE: {
                if (source instanceof FloatTag) {
                    float f = source.getAsFloat();
                    return Result.ok(DoubleTag.valueOf(Float.isFinite(f) ? Double.parseDouble(Float.toString(f)) : f));
                }
                if (floating) {
                    return Result.ok(DoubleTag.valueOf(source.getAsDouble()));
                }
                long value = source.getAsLong();
                double d = (double) value;
                return exact(d, value) ? Result.ok(DoubleTag.valueOf(d)) : Result.fail(Problem.PRECISION);
            }
            default:
                throw new IllegalArgumentException(target.name());
        }
    }

    @Nonnull
    private static Result integer(long value, @Nonnull NbtType target) {
        switch (target) {
            case BYTE:
                return value < Byte.MIN_VALUE || value > Byte.MAX_VALUE
                        ? Result.fail(Problem.OUT_OF_RANGE) : Result.ok(ByteTag.valueOf((byte) value));
            case SHORT:
                return value < Short.MIN_VALUE || value > Short.MAX_VALUE
                        ? Result.fail(Problem.OUT_OF_RANGE) : Result.ok(ShortTag.valueOf((short) value));
            case INT:
                return value < Integer.MIN_VALUE || value > Integer.MAX_VALUE
                        ? Result.fail(Problem.OUT_OF_RANGE) : Result.ok(IntTag.valueOf((int) value));
            default:
                return Result.ok(LongTag.valueOf(value));
        }
    }

    /**
     * 浮点数是不是精确等于这个整数（用 BigDecimal 比，避开 long ↔ double 强转在 2^63 附近的饱和）
     */
    private static boolean exact(double d, long value) {
        return Double.isFinite(d) && new BigDecimal(d).compareTo(BigDecimal.valueOf(value)) == 0;
    }
}
