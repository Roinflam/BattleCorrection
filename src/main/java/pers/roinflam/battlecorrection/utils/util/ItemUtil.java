package pers.roinflam.battlecorrection.utils.util;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;

public class ItemUtil {

    /**
     * 聊天消息里一个物品名最多占多少字节的 JSON（超了就不带悬停提示）
     * <p>
     * 1.12.2 的聊天包整条 JSON 不能超过 32767 字节，这里只给物品留一半，另一半留给消息本身和管理员通知的外层。
     */
    public static final int CHAT_ITEM_JSON_LIMIT = 16384;

    @Nonnull
    public static Item registerItem(@Nonnull Item item, @Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        item.setUnlocalizedName(name);
        item.setRegistryName(name);
        item.setCreativeTab(creativeTabs);
//        BattleCorrection.proxy.registerItemRenderer(item, 0, "inventory");
        return item;
    }

    /**
     * 聊天消息里显示的物品名：平时和原版 {@link ItemStack#getTextComponent()} 一样带悬停提示，NBT 太大时不带
     * <p>
     * 原版会把整件物品的 NBT 以文本形式塞进悬停提示。聊天包的 JSON 超过 32767 字节时服务端编码失败，
     * 收到这条消息的玩家直接断线（命令的管理员通知会发给所有在线 OP，一起断）。
     * 编辑器能存几百 KB 的 NBT，所以 JSON 超过 {@link #CHAT_ITEM_JSON_LIMIT} 的物品只显示名字，颜色、斜体照旧；
     * 自定义名字本身就长得离谱时退回物品的原名。
     *
     * @param stack 物品
     * @return 能放心发出去的物品名
     */
    @Nonnull
    public static ITextComponent chatName(@Nonnull ItemStack stack) {
        ITextComponent name = stack.getTextComponent();
        if (jsonBytes(name) <= CHAT_ITEM_JSON_LIMIT) {
            return name;
        }
        name.getStyle().setHoverEvent(null);
        if (jsonBytes(name) <= CHAT_ITEM_JSON_LIMIT) {
            return name;
        }
        ITextComponent plain = new TextComponentString("[" + stack.getItem().getItemStackDisplayName(stack) + "]");
        plain.getStyle().setColor(name.getStyle().getColor());
        return plain;
    }

    private static int jsonBytes(@Nonnull ITextComponent component) {
        return ITextComponent.Serializer.componentToJson(component).getBytes(StandardCharsets.UTF_8).length;
    }

}
