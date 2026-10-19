package pers.roinflam.battlecorrection.compat;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import baubles.api.cap.BaublesCapabilities;
import baubles.api.cap.IBaublesItemHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 所有直接碰 Baubles API 的小操作都放在这里
 * <p>
 * 这个类引用了 Baubles 的类，只能在 {@link BaublesIntegration#isBaublesLoaded()} 为 true 时调用。
 * 对外的参数和返回值只用 Forge / 原版的类型（IItemHandlerModifiable 而不是 IBaublesItemHandler），
 * 调用方的字节码里就不会出现 Baubles 的类名，没装 Baubles 时调用方照样能加载。
 */
final class BaublesAccess {

    private BaublesAccess() {
    }

    /**
     * Baubles 的两个能力是否已经注册（Baubles 在自己的预初始化里注册）
     * <p>
     * 只带了 Baubles API 的整合包（别的模组把 API 打进自己的 jar）里类是有的，能力却是 null，
     * 这时当成没装 Baubles 处理。
     *
     * @return true = 能用
     */
    static boolean capabilitiesReady() {
        return BaublesCapabilities.CAPABILITY_BAUBLES != null && BaublesCapabilities.CAPABILITY_ITEM_BAUBLE != null;
    }

    /**
     * 玩家的饰品栏
     * <p>
     * 不用 BaublesApi.getBaublesHandler：它拿不到能力时会直接空指针（某些假玩家）。
     *
     * @param player 玩家
     * @return 饰品栏；拿不到时返回 null
     */
    @Nullable
    static IItemHandlerModifiable getHandler(@Nonnull EntityPlayer player) {
        IBaublesItemHandler handler = player.getCapability(BaublesCapabilities.CAPABILITY_BAUBLES, null);
        if (handler != null) {
            handler.setPlayer(player);
        }
        return handler;
    }

    /**
     * 物品是不是饰品（不管是原生的还是本模组标记的）
     *
     * @param stack 物品
     * @return true = 有饰品能力
     */
    static boolean isBauble(@Nonnull ItemStack stack) {
        return !stack.isEmpty() && stack.hasCapability(BaublesCapabilities.CAPABILITY_ITEM_BAUBLE, null);
    }

    /**
     * 物品是不是原生饰品（饰品能力不是本模组给的）
     *
     * @param stack 物品
     * @return true = 原生饰品
     */
    static boolean isNativeBauble(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof IBauble) {
            return true;
        }
        IBauble bauble = stack.getCapability(BaublesCapabilities.CAPABILITY_ITEM_BAUBLE, null);
        return bauble != null && !(bauble instanceof BaubleMarkCapability.MarkedBauble);
    }

    /**
     * 物品的饰品能力是不是本模组按标记给的
     *
     * @param stack 物品
     * @return true = 标记饰品
     */
    static boolean isMarkedBauble(@Nonnull ItemStack stack) {
        return !stack.isEmpty()
                && stack.getCapability(BaublesCapabilities.CAPABILITY_ITEM_BAUBLE, null) instanceof BaubleMarkCapability.MarkedBauble;
    }

    /**
     * 按 Baubles 自己的规则判断物品能不能放进某一格（饰品类型 + canEquip）
     *
     * @param handler 饰品栏（必须是 {@link #getHandler} 拿到的）
     * @param slot    格子序号
     * @param stack   物品
     * @param player  玩家
     * @return true = 能放
     */
    static boolean isItemValidForSlot(@Nonnull IItemHandler handler, int slot, @Nonnull ItemStack stack,
                                      @Nonnull EntityPlayer player) {
        return handler instanceof IBaublesItemHandler
                && ((IBaublesItemHandler) handler).isItemValidForSlot(slot, stack, player);
    }

    /**
     * 格子里原有的饰品能不能取下来（带诅咒之类的饰品会拒绝）
     *
     * @param stack  饰品
     * @param player 玩家
     * @return true = 能取下；没有饰品能力的物品也算能取下
     */
    static boolean canUnequip(@Nonnull ItemStack stack, @Nonnull EntityPlayer player) {
        IBauble bauble = stack.getCapability(BaublesCapabilities.CAPABILITY_ITEM_BAUBLE, null);
        return bauble == null || bauble.canUnequip(stack, player);
    }

    /**
     * 通知饰品"被戴上了"
     * <p>
     * Baubles 只在界面里拖放时调这两个回调，直接 setStackInSlot 不会调；
     * 程序里帮玩家戴上、摘下时要手动补上，不然原生饰品的戴上效果（加药水、加属性）不会触发。
     *
     * @param stack  饰品
     * @param player 玩家
     */
    static void onEquipped(@Nonnull ItemStack stack, @Nonnull EntityPlayer player) {
        IBauble bauble = stack.getCapability(BaublesCapabilities.CAPABILITY_ITEM_BAUBLE, null);
        if (bauble != null) {
            bauble.onEquipped(stack, player);
        }
    }

    /**
     * 通知饰品"被摘下了"
     *
     * @param stack  饰品
     * @param player 玩家
     */
    static void onUnequipped(@Nonnull ItemStack stack, @Nonnull EntityPlayer player) {
        IBauble bauble = stack.getCapability(BaublesCapabilities.CAPABILITY_ITEM_BAUBLE, null);
        if (bauble != null) {
            bauble.onUnequipped(stack, player);
        }
    }

    /**
     * 把 {@link BaubleMark#baubleTypeName} 的结果换成枚举
     *
     * @param name 枚举名
     * @return 饰品类型；名字不认识时按 TRINKET
     */
    @Nonnull
    static BaubleType toBaubleType(@Nullable String name) {
        if (name != null) {
            for (BaubleType type : BaubleType.values()) {
                if (type.name().equals(name)) {
                    return type;
                }
            }
        }
        return BaubleType.TRINKET;
    }
}
