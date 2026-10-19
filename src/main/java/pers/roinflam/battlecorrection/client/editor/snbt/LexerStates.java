package pers.roinflam.battlecorrection.client.editor.snbt;

import javax.annotation.Nonnull;
import java.util.Arrays;

/**
 * 各行行首的分词状态缓存（见 {@link SnbtLexer}）
 * <p>
 * 只往前推到用得着的那一行：文本改动后，改动所在行之后的状态全部作废，下次有人问到第 N 行时再从作废点往下推。
 * 在可视范围附近打字时，要推的只有几行；滚到文末才会把中间的补齐，而且补一次就缓存住了。
 */
final class LexerStates {

    private int[] states = new int[64];
    /**
     * states[0, valid) 是可信的
     */
    private int valid = 1;

    /**
     * 第 line 行（含）之后的状态作废；第 line 行的行首状态只取决于它前面的文本，保留
     *
     * @param line 改动所在行
     */
    void invalidateFrom(int line) {
        if (valid > line + 1) {
            valid = line + 1;
        }
    }

    /**
     * 全部作废
     */
    void reset() {
        valid = 1;
    }

    /**
     * 第 line 行行首的状态
     *
     * @param doc  文档
     * @param line 行号
     * @return 状态
     */
    int stateAt(@Nonnull TextDocument doc, int line) {
        if (line < valid) {
            return states[line];
        }
        if (line >= states.length) {
            states = Arrays.copyOf(states, Math.max(line + 1, states.length * 2));
        }
        CharSequence text = doc.chars();
        int state = states[valid - 1];
        for (int l = valid - 1; l < line; l++) {
            state = SnbtLexer.scanState(text, doc.lineStart(l), doc.lineEnd(l), state);
            states[l + 1] = state;
        }
        valid = line + 1;
        return state;
    }
}
