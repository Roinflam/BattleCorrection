package pers.roinflam.battlecorrection.editor;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * 物品编辑器的目标：编辑的是哪一格的物品
 * <p>
 * 三种定位方式：
 * <ul>
 *   <li>MAIN_HAND：主手物品（命令、没开界面时按快捷键）</li>
 *   <li>INVENTORY：玩家背包里的某一格（0-35 主背包和快捷栏，36-39 盔甲，40 副手）</li>
 *   <li>MENU_SLOT：玩家当前打开的容器（箱子等）里的某一格，用容器 ID + 格子序号定位</li>
 * </ul>
 * 只存"位置"不存物品本身，两端各自按位置去自己那份数据里取。
 * 服务端保存时用同一个位置写回，所以这里的所有下标都必须做边界检查，网络数据不可信。
 *
 * @param kind        定位方式
 * @param containerId 容器 ID（仅 MENU_SLOT 有效，其余为 -1）
 * @param index       格子下标（MAIN_HAND 为 -1）
 */
public record ItemEditTarget(Kind kind, int containerId, int index) {

    /**
     * 定位方式
     */
    public enum Kind {
        MAIN_HAND,
        INVENTORY,
        MENU_SLOT
    }

    /**
     * 主手
     *
     * @return 目标
     */
    @Nonnull
    public static ItemEditTarget mainHand() {
        return new ItemEditTarget(Kind.MAIN_HAND, -1, -1);
    }

    /**
     * 玩家背包某一格
     *
     * @param index 背包下标
     * @return 目标
     */
    @Nonnull
    public static ItemEditTarget inventory(int index) {
        return new ItemEditTarget(Kind.INVENTORY, -1, index);
    }

    /**
     * 当前打开的容器某一格
     *
     * @param containerId 容器 ID（AbstractContainerMenu#containerId）
     * @param slotIndex   格子序号（Slot#index）
     * @return 目标
     */
    @Nonnull
    public static ItemEditTarget menuSlot(int containerId, int slotIndex) {
        return new ItemEditTarget(Kind.MENU_SLOT, containerId, slotIndex);
    }

    /**
     * 写入网络包
     *
     * @param buf 缓冲
     */
    public void write(@Nonnull FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeVarInt(containerId);
        buf.writeVarInt(index);
    }

    /**
     * 从网络包读取
     *
     * @param buf 缓冲
     * @return 目标
     */
    @Nonnull
    public static ItemEditTarget read(@Nonnull FriendlyByteBuf buf) {
        Kind kind = buf.readEnum(Kind.class);
        int containerId = buf.readVarInt();
        int index = buf.readVarInt();
        return new ItemEditTarget(kind, containerId, index);
    }

    /**
     * 取目标位置上的物品
     * <p>
     * 位置无效（下标越界、容器 ID 对不上）时返回空物品，不抛异常。
     *
     * @param player 玩家（两端都可以）
     * @return 物品；无效时为 ItemStack.EMPTY
     */
    @Nonnull
    public ItemStack get(@Nonnull Player player) {
        switch (kind) {
            case MAIN_HAND:
                return player.getMainHandItem();
            case INVENTORY: {
                Inventory inventory = player.getInventory();
                if (index < 0 || index >= inventory.getContainerSize()) {
                    return ItemStack.EMPTY;
                }
                return inventory.getItem(index);
            }
            case MENU_SLOT: {
                AbstractContainerMenu menu = player.containerMenu;
                if (menu == null || menu.containerId != containerId || index < 0 || index >= menu.slots.size()) {
                    return ItemStack.EMPTY;
                }
                return menu.getSlot(index).getItem();
            }
            default:
                return ItemStack.EMPTY;
        }
    }

    /**
     * 把物品写回目标位置（服务端用）
     *
     * @param player 玩家
     * @param stack  新物品
     * @return true = 写入成功；false = 位置已失效
     */
    public boolean set(@Nonnull Player player, @Nonnull ItemStack stack) {
        switch (kind) {
            case MAIN_HAND:
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                return true;
            case INVENTORY: {
                Inventory inventory = player.getInventory();
                if (index < 0 || index >= inventory.getContainerSize()) {
                    return false;
                }
                inventory.setItem(index, stack);
                return true;
            }
            case MENU_SLOT: {
                AbstractContainerMenu menu = player.containerMenu;
                if (menu == null || menu.containerId != containerId || index < 0 || index >= menu.slots.size()) {
                    return false;
                }
                menu.getSlot(index).set(stack);
                menu.broadcastChanges();
                return true;
            }
            default:
                return false;
        }
    }
}
