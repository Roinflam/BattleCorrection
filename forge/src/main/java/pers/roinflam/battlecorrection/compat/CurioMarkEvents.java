package pers.roinflam.battlecorrection.compat;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.utils.LogUtil;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.event.CurioAttributeModifierEvent;
import top.theillusivec4.curios.api.event.CurioEquipEvent;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 饰品栏标记的 Curios 事件处理
 * <p>
 * 1. CurioEquipEvent：Curios 判断"这个格子能不能放这件物品"时触发。
 * 结果设为 ALLOW 会直接放行，不再看物品标签，所以对任何模组添加的饰品栏都有效，不需要数据包。
 * 2. CurioAttributeModifierEvent：Curios 计算"这件饰品给多少属性"时触发。
 * 这里把物品上原版格式的属性修饰符加进去，由 Curios 负责加到人身上、摘下时移除，
 * 原版属性（生命上限、护甲等）和本模组的 15 个属性都能生效。
 * <p>
 * 这个类引用了 Curios 的事件类，只能在装了 Curios 时由 {@link CuriosIntegration#init()} 手动注册，
 * 不能加 @Mod.EventBusSubscriber（否则没装 Curios 时启动就会找不到类而崩溃）。
 */
public final class CurioMarkEvents {

    private CurioMarkEvents() {
    }

    /**
     * 判断物品能否放入饰品栏：带有对应标记时放行
     * <p>
     * 两端都会触发（客户端用于界面预判），只看物品自身的 NBT，两端结果一致。
     * 总开关关闭后不再放行，但已经戴着的物品不会被强制摘下。
     *
     * @param evt Curios 装备判定事件
     */
    @SubscribeEvent
    public static void onCurioEquip(@Nonnull CurioEquipEvent evt) {
        // 其他模组已经明确拒绝时不覆盖
        if (evt.getResult() == Event.Result.DENY) {
            return;
        }
        if (!ConfigAttribute.CURIO_MARK_ENABLED.get()) {
            return;
        }
        if (CurioMark.canEquip(evt.getStack(), evt.getSlotContext().identifier())) {
            evt.setResult(Event.Result.ALLOW);
        }
    }

    /**
     * 计算饰品属性：把标记物品上的属性修饰符交给 Curios
     * <p>
     * 修饰符的 UUID 由"饰品栏格子的 UUID"和"原修饰符 UUID"混合得到：
     * 同一个格子每次算出来都一样（Curios 摘下时靠它精确移除），
     * 不同格子互不相同（两件一样的饰品戴在两个格子里会分别生效，不会互相覆盖）。
     * <p>
     * 这里不看总开关：属性始终跟着标记走，避免开关切换时"戴上时加了、摘下时没减"留下残留属性。
     *
     * @param evt Curios 属性计算事件
     */
    @SubscribeEvent
    public static void onCurioAttributeModifiers(@Nonnull CurioAttributeModifierEvent evt) {
        SlotContext slotContext = evt.getSlotContext();
        // 装饰栏只管外观，不给属性
        if (slotContext.cosmetic()) {
            return;
        }

        ItemStack stack = evt.getItemStack();
        if (!CurioMark.isMarked(stack)) {
            return;
        }

        List<CurioMark.ModifierEntry> entries = CurioMark.readModifiers(stack);
        if (entries.isEmpty()) {
            return;
        }

        UUID slotUuid = evt.getUuid() != null
                ? evt.getUuid()
                : UUID.nameUUIDFromBytes((slotContext.identifier() + "#" + slotContext.index())
                .getBytes(StandardCharsets.UTF_8));

        for (CurioMark.ModifierEntry entry : entries) {
            AttributeModifier original = entry.modifier();
            AttributeModifier slotModifier = new AttributeModifier(
                    mixUuid(slotUuid, original.getId()),
                    original.getName(),
                    original.getAmount(),
                    original.getOperation()
            );
            evt.addModifier(entry.attribute(), slotModifier);
        }

        if (LogUtil.isDetailed()) {
            LogUtil.debug(String.format("饰品栏标记物品生效 - 物品: %s, 饰品栏: %s#%d, 属性条数: %d",
                    stack.getHoverName().getString(), slotContext.identifier(), slotContext.index(), entries.size()));
        }
    }

    /**
     * 把两个 UUID 按位异或，得到稳定且互不冲突的新 UUID
     *
     * @param slotUuid     饰品栏格子的 UUID
     * @param modifierUuid 原修饰符的 UUID
     * @return 混合后的 UUID
     */
    @Nonnull
    private static UUID mixUuid(@Nonnull UUID slotUuid, @Nonnull UUID modifierUuid) {
        return new UUID(slotUuid.getMostSignificantBits() ^ modifierUuid.getMostSignificantBits(),
                slotUuid.getLeastSignificantBits() ^ modifierUuid.getLeastSignificantBits());
    }
}
