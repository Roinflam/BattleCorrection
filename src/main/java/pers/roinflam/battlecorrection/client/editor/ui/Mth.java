package pers.roinflam.battlecorrection.client.editor.ui;

/**
 * 编辑器里常用的数学小工具
 * <p>
 * 1.12.2 的 MathHelper 方法名不同、也没有 long 版的 clamp，编辑器代码统一用这里的。
 */
public final class Mth {

    private Mth() {
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    public static long clamp(long value, long min, long max) {
        return value < min ? min : Math.min(value, max);
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    /**
     * 向下取整（负数也往小的方向取）
     */
    public static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    public static int floor(float value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    public static int ceil(double value) {
        int i = (int) value;
        return value > i ? i + 1 : i;
    }

    public static int ceil(float value) {
        int i = (int) value;
        return value > i ? i + 1 : i;
    }

    public static float lerp(float t, float from, float to) {
        return from + t * (to - from);
    }

    public static double lerp(double t, double from, double to) {
        return from + t * (to - from);
    }

    /**
     * 小数部分（0 ≤ 结果 &lt; 1）
     */
    public static double frac(double value) {
        return value - Math.floor(value);
    }

    /**
     * 整数除法向下取整（被除数为负时也往小的方向）
     */
    public static int floorDiv(int dividend, int divisor) {
        return Math.floorDiv(dividend, divisor);
    }

    public static int positiveModulo(int value, int modulus) {
        return Math.floorMod(value, modulus);
    }
}
