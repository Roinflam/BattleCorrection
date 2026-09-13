package pers.roinflam.battlecorrection.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.roinflam.battlecorrection.compat.CurioMark;
import pers.roinflam.battlecorrection.config.ConfigAttribute;

import javax.annotation.Nullable;

/**
 * 修补 Curios 饰品栏界面的 Shift 快速移动：让打了饰品栏标记的物品也能一键放进饰品栏
 * <p>
 * 修补了什么：Curios 的 {@code CuriosContainer#quickMoveStack}（Shift 点击）判断一件物品能不能进饰品栏时，
 * 用的是 {@code CuriosApi.getItemStackSlots}——只认物品标签（curios:ring 这种）和实现了 ICurio 的物品，
 * 不走 CurioEquipEvent。我们的标记是写在物品 NBT 上的、靠 CurioEquipEvent 放行，所以手动拖能放，Shift 不行。
 * <p>
 * 怎么修：在方法开头拦一下，来源格子里的物品如果带标记，就直接调原版的 moveItemStackTo 往饰品栏区域塞。
 * moveItemStackTo 逐格调 Slot#mayPlace，而 mayPlace 最终走的是 CurioEquipEvent，标记自然生效。
 * 没塞进去（没有匹配的饰品栏、都满了）就不拦，交还给 Curios 原逻辑。
 * <p>
 * 格子布局（Curios 5.14）：0 合成产物，1-4 合成，5-8 盔甲，9-35 背包，36-44 快捷栏，45 副手，46 起饰品栏。
 * 只处理从背包/快捷栏/副手往饰品栏放，饰品栏往外拿走原逻辑。
 * <p>
 * @Pseudo：没装 Curios 时找不到目标类，Mixin 会静默跳过而不是崩。
 * 继承 AbstractContainerMenu 是为了能调它 protected 的 moveItemStackTo；构造器只是语法需要，Mixin 不会用它。
 */
@Pseudo
@Mixin(targets = "top.theillusivec4.curios.common.inventory.container.CuriosContainer")
public abstract class CuriosContainerMixin extends AbstractContainerMenu {

    private static final int INVENTORY_START = 9;
    private static final int CURIOS_START = 46;

    protected CuriosContainerMixin(@Nullable MenuType<?> type, int containerId) {
        super(type, containerId);
    }

    /**
     * Shift 点击带标记的物品：先试着放进饰品栏
     *
     * @param player 玩家
     * @param index  被点击的格子序号
     * @param cir    回调
     */
    @Inject(method = "quickMoveStack(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private void battlecorrection$quickMoveMarked(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (!ConfigAttribute.CURIO_MARK_ENABLED.get()) {
            return;
        }
        // 只管背包区（含快捷栏、副手）→ 饰品栏这一个方向
        if (index < INVENTORY_START || index >= CURIOS_START || this.slots.size() <= CURIOS_START) {
            return;
        }
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return;
        }
        ItemStack stack = slot.getItem();
        if (!CurioMark.isMarked(stack)) {
            return;
        }

        ItemStack original = stack.copy();
        if (!this.moveItemStackTo(stack, CURIOS_START, this.slots.size(), false)) {
            return;
        }
        // 下面这几步照抄原版 quickMoveStack 的收尾，保证来源格子状态正确
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        slot.onTake(player, stack);
        cir.setReturnValue(original);
    }
}
