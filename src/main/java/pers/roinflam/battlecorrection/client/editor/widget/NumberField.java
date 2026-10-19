package pers.roinflam.battlecorrection.client.editor.widget;

import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.client.editor.ui.Font;
import pers.roinflam.battlecorrection.client.editor.ui.Keys;
import pers.roinflam.battlecorrection.client.editor.ui.Screen;

import javax.annotation.Nonnull;
import java.util.function.IntConsumer;

/**
 * 整数输入框（调色盘的 R / G / B 数值）：只收数字，超出范围按边界算
 * <p>
 * 打字时每改一位就生效（打 "2" "25" "255" 一路变过去），超过上限的数（比如 300）按上限算，失去焦点时框里的字改回实际的值。
 * 鼠标停在上面滚轮 ±1（Shift ±10），有焦点时上下方向键同理；左右方向键照常移动光标。
 * 外面同步数值用 {@link #show(int)}，不会触发回调。
 */
public class NumberField extends FlatEditBox {

    private final int min;
    private final int max;
    private final IntConsumer onChange;
    private int current;
    private boolean quiet;

    /**
     * @param font     字体
     * @param x        左
     * @param y        上
     * @param width    宽
     * @param height   高
     * @param message  朗读用的名字（也是空框的提示）
     * @param min      最小值
     * @param max      最大值
     * @param initial  初始值
     * @param onChange 用户改了数值
     */
    public NumberField(@Nonnull Font font, int x, int y, int width, int height, @Nonnull Component message,
                       int min, int max, int initial, @Nonnull IntConsumer onChange) {
        super(font, x, y, width, height, message, false);
        this.min = min;
        this.max = max;
        this.onChange = onChange;
        setMaxLength(Math.max(String.valueOf(max).length(), String.valueOf(min).length()));
        setFilter(text -> text.chars().allMatch(Character::isDigit));
        show(initial);
        setResponder(this::typed);
    }

    /**
     * 当前值
     *
     * @return 值
     */
    public int number() {
        return current;
    }

    /**
     * 显示一个值（外部同步用，不触发回调）；框里正好是同一个数时不动，免得打断正在输入的光标
     *
     * @param value 值
     */
    public void show(int value) {
        current = clamp(value);
        String text = String.valueOf(current);
        if (!text.equals(getValue())) {
            quiet = true;
            setValue(text);
            quiet = false;
        }
    }

    private void typed(@Nonnull String text) {
        if (quiet || text.isEmpty()) {
            return;
        }
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return;
        }
        set(value);
    }

    /**
     * 改成某个值并通知（夹在范围里）
     */
    private void set(int value) {
        int clamped = clamp(value);
        current = clamped;
        onChange.accept(clamped);
    }

    private int clamp(int value) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 增减若干，框里的字跟着变
     *
     * @param delta 增量
     */
    private void nudge(int delta) {
        set(current + delta);
        show(current);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            // 打了个超范围的数或者删空了：离开时框里改回实际生效的值
            show(current);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (canConsumeInput()) {
            if (keyCode == Keys.KEY_UP) {
                nudge(Screen.hasShiftDown() ? 10 : 1);
                return true;
            }
            if (keyCode == Keys.KEY_DOWN) {
                nudge(Screen.hasShiftDown() ? -10 : -1);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!active || !visible || delta == 0) {
            return false;
        }
        nudge((int) Math.signum(delta) * (Screen.hasShiftDown() ? 10 : 1));
        return true;
    }
}
