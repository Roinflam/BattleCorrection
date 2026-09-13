package pers.roinflam.battlecorrection.mixin;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.roinflam.battlecorrection.compat.CurioMark;

/**
 * 修补 {@link LivingEntity#getEquipmentSlotForItem(ItemStack)}：带饰品栏标记的防具不再被当成防具
 * <p>
 * 修补了什么：这个静态方法是原版判断"一件物品该穿在哪个装备槽"的唯一入口（1.20 起放在 LivingEntity 上，
 * 1.19 及之前在 Mob 上；Forge 的 IForgeItem#canEquip 默认实现也是调它），
 * 背包里往盔甲格拖拽（ArmorSlot.mayPlace → canEquip）、Shift 快捷穿戴（InventoryMenu.quickMoveStack）、
 * 手持右键穿戴（Equipable.swapWithEquipmentSlot）、发射器给实体穿装备、怪物捡装备（canTakeItem）
 * 全都从它取结果。这里在它返回之后检查：返回的是盔甲类槽位（头/胸/腿/脚），
 * 并且物品带着本模组的饰品栏标记，就把结果改成主手，等于告诉原版"这东西不是防具"。
 * <p>
 * 为什么这样做：需求是"标记过的防具只能当饰品，不能再穿到盔甲栏"。
 * Forge 提供的 IForgeItem#canEquip / #getEquipmentSlot 只能在物品类里重写，
 * 对原版 ArmorItem 和其他模组的防具实例改不了；而 Forge 事件里没有能拦截"放进盔甲格"的事件，
 * 所以只能在这个总入口上动手。改返回值而不是重写整个方法，原版和 Forge 的其余逻辑照常。
 * <p>
 * 不影响属性：物品自身的属性（ItemStack#getAttributeModifiers）是按传入的槽位直接算的，不经过这个方法，
 * 所以 {@code CurioMarkEvents} 依然能读到防具原本的护甲、韧性等属性并搬到饰品栏上。
 * <p>
 * 总开关关闭、或没装 Curios 时不改动返回值（判断在 {@link CurioMark#isCurioOnly(ItemStack)} 里），
 * 这样标记过的防具至少还能照常穿，不会变成哪都放不进的废品。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    /**
     * 在 getEquipmentSlotForItem 返回前检查并改写结果
     * <p>
     * 这个方法调用非常频繁（每次背包点击、怪物 AI 捡东西都会调），所以先比较槽位类型（枚举比较，几乎无开销），
     * 绝大多数调用在第一行就返回；只有原本会进盔甲栏的物品才去读 NBT 判断标记。
     *
     * @param stack 物品
     * @param cir   返回值回调
     */
    @Inject(
            method = "getEquipmentSlotForItem(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/EquipmentSlot;",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void battlecorrection$stripArmorSlot(ItemStack stack, CallbackInfoReturnable<EquipmentSlot> cir) {
        EquipmentSlot slot = cir.getReturnValue();
        if (slot == null || slot.getType() != EquipmentSlot.Type.ARMOR) {
            return;
        }
        if (CurioMark.isCurioOnly(stack)) {
            cir.setReturnValue(EquipmentSlot.MAINHAND);
        }
    }
}
