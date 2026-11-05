package pers.roinflam.battlecorrection.client.editor.snbt;

/**
 * 单个字符的显示宽度（像素）
 * <p>
 * 排版逻辑只通过它拿宽度，所以可以脱离字体单独测试；游戏里的实现按字体逐字查表缓存。
 */
@FunctionalInterface
public interface CharWidths {

    /**
     * @param codePoint 码点（落单的 surrogate 码元由调用方换成 U+FFFD）
     * @return 前进宽度
     */
    float width(int codePoint);
}
