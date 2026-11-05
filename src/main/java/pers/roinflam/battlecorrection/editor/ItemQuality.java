package pers.roinflam.battlecorrection.editor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 手动最低品质标记
 * <p>
 * 提示框模组可以读物品 {@code tag} 根下的四个键：{@code Epic}、{@code Legendary}、{@code Myth}、{@code Genesis}，
 * 当作"最低品质"：自动判定（附魔数量、稀有度等）比它高就取自动的，比它低就至少提到它。
 * 判定规则和读取方保持一致：值是数字 → 非 0 才算；不是数字（字符串、compound 之类）→ 一律算有。
 * 四个键同时存在时按 创世 &gt; 神话 &gt; 传说 &gt; 史诗 取最高。
 * <p>
 * 写入时先把四个键都删掉再写目标那一个（{@code 1b}），所以保存后至多只有一个标记；
 * 选"不启用"只是删标记，自动判定该出的品质照样出。
 * 没装认这些键的提示框模组时它们不起作用，写了也无害。
 */
public enum ItemQuality {

    /**
     * 不启用：删掉全部手动标记，交回自动判定
     */
    NONE(null, "none", 0xAAAAAA),

    /**
     * 史诗
     */
    EPIC("Epic", "epic", 0xAA00AA),

    /**
     * 传说
     */
    LEGENDARY("Legendary", "legendary", 0xFFAA00),

    /**
     * 神话
     */
    MYTH("Myth", "myth", 0xFF5555),

    /**
     * 创世
     */
    GENESIS("Genesis", "genesis", 0x55FFFF);

    /**
     * 读取优先级：创世 > 神话 > 传说 > 史诗
     */
    private static final ItemQuality[] READ_ORDER = {GENESIS, MYTH, LEGENDARY, EPIC};

    @Nullable
    private final String nbtKey;
    private final String langKey;
    private final int color;

    ItemQuality(@Nullable String nbtKey, @Nonnull String langKey, int color) {
        this.nbtKey = nbtKey;
        this.langKey = langKey;
        this.color = color;
    }

    /**
     * 对应的 NBT 键名
     *
     * @return 键名；NONE 为 null
     */
    @Nullable
    public String nbtKey() {
        return nbtKey;
    }

    /**
     * 显示颜色（RGB，无透明度）
     *
     * @return 颜色
     */
    public int color() {
        return color;
    }

    /**
     * 带颜色的显示名
     *
     * @return 组件
     */
    @Nonnull
    public MutableComponent displayName() {
        return Component.translatable("gui.battlecorrection.editor.quality." + langKey)
                .withStyle(style -> style.withColor(color));
    }

    /**
     * 读物品当前的手动最低品质
     *
     * @param stack 物品
     * @return 档位；没有任何有效标记返回 NONE
     */
    @Nonnull
    public static ItemQuality read(@Nonnull ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return NONE;
        }
        for (ItemQuality quality : READ_ORDER) {
            if (hasMark(tag, quality.nbtKey)) {
                return quality;
            }
        }
        return NONE;
    }

    /**
     * 把手动最低品质写到物品上（先清四个键，再写目标一个）
     *
     * @param stack   物品
     * @param quality 目标档位
     */
    public static void apply(@Nonnull ItemStack stack, @Nonnull ItemQuality quality) {
        if (quality == NONE) {
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                clear(tag);
            }
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        clear(tag);
        tag.putBoolean(quality.nbtKey, true);
    }

    private static void clear(@Nonnull CompoundTag tag) {
        for (ItemQuality quality : READ_ORDER) {
            tag.remove(quality.nbtKey);
        }
    }

    /**
     * 和读取方的判定一致：数字非 0 算有，非数字一律算有
     */
    private static boolean hasMark(@Nonnull CompoundTag tag, @Nullable String key) {
        if (key == null || !tag.contains(key)) {
            return false;
        }
        Tag value = tag.get(key);
        if (value instanceof NumericTag numeric) {
            return numeric.getAsByte() != 0;
        }
        return true;
    }
}
