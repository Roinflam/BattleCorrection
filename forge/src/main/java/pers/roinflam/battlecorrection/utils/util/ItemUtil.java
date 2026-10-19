package pers.roinflam.battlecorrection.utils.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * 物品工具类
 */
public class ItemUtil {

    /**
     * 聊天消息里一个物品名最多占多少字符的 JSON（超了就不带悬停提示）
     * <p>
     * 聊天包整条 JSON 不能超过 262144 个字符，这里只给物品留一半，另一半留给消息本身。
     */
    public static final int CHAT_ITEM_JSON_LIMIT = 131072;

    /**
     * 聊天消息里显示的物品名：平时和原版 {@link ItemStack#getDisplayName()} 一样带悬停提示，NBT 太大时不带
     * <p>
     * 原版会把整件物品的 NBT 以文本形式塞进悬停提示。聊天包的 JSON 超过上限时服务端编码失败，
     * 这条消息被直接丢掉（玩家什么也收不到，服务端日志里多一条报错）。
     * 编辑器能存上 MB 的 NBT，所以 JSON 超过 {@link #CHAT_ITEM_JSON_LIMIT} 的物品只显示名字，颜色、斜体照旧；
     * 自定义名字本身就长得离谱时退回物品的原名。
     *
     * @param stack 物品
     * @return 能放心发出去的物品名
     */
    @Nonnull
    public static Component chatName(@Nonnull ItemStack stack) {
        Component name = stack.getDisplayName();
        if (jsonLength(name) <= CHAT_ITEM_JSON_LIMIT) {
            return name;
        }
        MutableComponent plain = name.copy().withStyle(style -> style.withHoverEvent(null));
        if (jsonLength(plain) <= CHAT_ITEM_JSON_LIMIT) {
            return plain;
        }
        return ComponentUtils.wrapInSquareBrackets(stack.getItem().getName(stack))
                .withStyle(stack.getRarity().getStyleModifier());
    }

    private static int jsonLength(@Nonnull Component component) {
        return Component.Serializer.toJson(component).length();
    }

}
