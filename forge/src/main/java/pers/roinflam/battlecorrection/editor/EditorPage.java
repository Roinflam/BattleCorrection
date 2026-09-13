package pers.roinflam.battlecorrection.editor;

import javax.annotation.Nonnull;

/**
 * 物品编辑器的页面（标签页）
 * <p>
 * 命令和快捷键用它告诉客户端"打开时先显示哪一页"，网络包里按序号传。
 * 这个枚举是公共代码，服务端也会加载，所以不能引用任何客户端类。
 * <p>
 * 顺序就是界面左侧标签页的顺序，别随意调换：网络包用 ordinal 传输，两端版本不同会错位。
 */
public enum EditorPage {

    /**
     * 显示名称
     */
    NAME("name"),

    /**
     * Lore（物品描述）
     */
    LORE("lore"),

    /**
     * 特殊标签（不可破坏、隐藏信息、耐久、数量等）
     */
    TAGS("tags"),

    /**
     * 附魔（下一批实现）
     */
    ENCHANTMENTS("enchantments"),

    /**
     * 属性（下一批实现）
     */
    ATTRIBUTES("attributes"),

    /**
     * 饰品栏（下一批实现）
     */
    CURIOS("curios"),

    /**
     * NBT 树（下一批实现）
     */
    TREE("tree"),

    /**
     * 原始 NBT 文本
     */
    RAW("raw"),

    /**
     * 玩家自己的饰品栏数量（测试用，和物品无关）
     */
    PLAYER_SLOTS("player_slots");

    private final String id;

    EditorPage(@Nonnull String id) {
        this.id = id;
    }

    /**
     * 页面ID（用于翻译键）
     *
     * @return 小写ID
     */
    @Nonnull
    public String id() {
        return id;
    }

    /**
     * 标签页名称的翻译键
     *
     * @return 翻译键
     */
    @Nonnull
    public String translationKey() {
        return "gui.battlecorrection.editor.tab." + id;
    }

    /**
     * 按序号取页面，越界时回落到第一页（网络数据不可信）
     *
     * @param ordinal 序号
     * @return 页面
     */
    @Nonnull
    public static EditorPage byOrdinal(int ordinal) {
        EditorPage[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return NAME;
        }
        return values[ordinal];
    }
}
