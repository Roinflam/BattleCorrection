package pers.roinflam.battlecorrection.compat;

import net.minecraft.core.NonNullList;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import com.google.common.collect.Multimap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.event.CurioAttributeModifierEvent;
import top.theillusivec4.curios.api.event.CurioEquipEvent;
import top.theillusivec4.curios.api.event.CurioUnequipEvent;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

/**
 * 饰品栏标记的 Curios 事件处理
 * <p>
 * 1. CurioEquipEvent：Curios 判断"这个格子能不能放这件物品"时触发。
 * 结果设为 ALLOW 会直接放行，不再看物品标签，所以对任何模组添加的饰品栏都有效，不需要数据包。
 * 2. CurioAttributeModifierEvent：Curios 算完"这件饰品给多少属性"之后触发，这里往上**追加**两类属性
 * （addModifier，不替换原有的，所以饰品原本的护甲、幸运，以及其他模组动态算出来的属性全部保留）：
 * a) 本模组命令写上去的附加属性（BattleCorrectionCurioModifiers）；
 * b) 物品自身的属性：防具的护甲/韧性、武器的攻击力、AttributeModifiers NBT、
 * 其他模组通过 ItemAttributeModifierEvent 加上去的属性——也就是物品提示框里显示的那些。
 * Curios 原本不认原版格式的属性，这一步把它们真正加到实体身上。标记物品一律追加；
 * 没标记的普通饰品按配置 curioNbtAttributes 决定追加范围（全部 / 只本模组属性 / 不追加）。
 * 以前这类属性是每隔几 tick 扫一遍饰品栏现算出来的，饰品多了拉弓吃东西会周期性掉帧；
 * 改成真修饰符后只在饰品变动时算一次，平时读属性就是一次查表。
 * 3. ItemAttributeModifierEvent（Forge）：原版每次问"这件物品在某个装备槽给什么属性"都会触发。
 * 标记物品在这里被清空，所以拿在手上、穿在身上不给属性，提示框里也不再有"在主手时："那一段；
 * 只有 2b 那条路（带着收集标志）读的时候放行。
 * 4. PlayerInteractEvent.RightClickItem：手持标记物品右键时优先放进饰品栏，先于原版的盔甲穿戴、吃喝等使用效果。
 * <p>
 * 这个类引用了 Curios 的事件类，只能在装了 Curios 时由 {@link CuriosIntegration#init()} 手动注册，
 * 不能加 @Mod.EventBusSubscriber（否则没装 Curios 时启动就会找不到类而崩溃）。
 * 第 3 条虽然是 Forge 事件，也跟着这个类一起注册：没装 Curios 时标记物品哪都放不进，清了属性就成废品。
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
        ItemStack stack = evt.getStack();
        String slotId = evt.getSlotContext().identifier();
        if (CurioMark.canEquip(stack, slotId)) {
            evt.setResult(Event.Result.ALLOW);
        } else if (CurioMark.isMarked(stack) && LogUtil.isDetailed()) {
            // 排查"标了却放不进"用：打印物品上标的是哪些、想放的是哪个格子
            LivingEntity entity = evt.getSlotContext().entity();
            LogUtil.debug(String.format("标记物品 %s 不能放进饰品栏 %s：标记为 %s（%s）",
                    stack.getHoverName().getString(), slotId, CurioMark.getSlots(stack),
                    entity == null ? "无实体" : (entity.level().isClientSide() ? "客户端" : "服务端")));
        }
    }

    /**
     * 追加属性：本模组的附加属性 + 标记物品自身的属性
     * <p>
     * 用 addModifier 往 Curios 算好的属性表后面加，不动原有条目，所以饰品自己的 Curios 属性（含其他模组
     * 按配置或联动算出来的动态属性）都不受影响。
     * <p>
     * 这里不看总开关：属性始终跟着 NBT 走。Curios 摘下饰品时会重新算一遍属性表去移除，
     * 如果戴上和摘下之间开关被切了，算出来的两份对不上，就会留下摘不掉的残留属性。
     * 同理，物品自身属性只看"有没有标记"，不看开关。
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
        boolean hasCustom = CurioMark.hasModifiers(stack);
        // 标记物品自身属性一律追加；普通饰品按配置
        ConfigAttribute.CurioNbtAttributes mode = CurioMark.isMarked(stack)
                ? ConfigAttribute.CurioNbtAttributes.ALL
                : ConfigAttribute.CURIO_NBT_ATTRIBUTES.get();
        if (!hasCustom && mode == ConfigAttribute.CurioNbtAttributes.OFF) {
            return;
        }

        String slotId = slotContext.identifier();
        UUID slotUuid = evt.getUuid();
        int applied = 0;

        // a) 命令写上去的附加属性
        if (hasCustom) {
            applied += appendCustomModifiers(evt, stack, slotId, slotUuid);
        }
        // b) 物品自身的属性
        if (mode != ConfigAttribute.CurioNbtAttributes.OFF) {
            applied += appendItemModifiers(evt, stack, slotUuid,
                    mode == ConfigAttribute.CurioNbtAttributes.MOD_ONLY);
        }

        if (applied > 0 && LogUtil.isDetailed()) {
            LogUtil.debug(String.format("追加饰品属性 - 物品: %s, 饰品栏: %s#%d, 条数: %d",
                    stack.getHoverName().getString(), slotId, slotContext.index(), applied));
        }
    }

    /**
     * 追加本模组 NBT 里的附加属性
     *
     * @param evt      Curios 属性计算事件
     * @param stack    物品
     * @param slotId   当前饰品栏ID
     * @param slotUuid 当前格子的 UUID
     * @return 追加的条数
     */
    private static int appendCustomModifiers(@Nonnull CurioAttributeModifierEvent evt, @Nonnull ItemStack stack,
                                             @Nonnull String slotId, @Nonnull UUID slotUuid) {
        int applied = 0;
        for (CurioMark.ModifierEntry entry : CurioMark.getModifiers(stack)) {
            if (!entry.appliesTo(slotId)) {
                continue;
            }
            evt.addModifier(entry.attribute(), forSlot(slotUuid, entry.modifier()));
            applied++;
        }
        return applied;
    }

    /**
     * 追加物品自身的属性（原版格式）
     * <p>
     * 收集逻辑见 {@link CurioMark#collectItemModifiers(ItemStack)}，这里负责两件事：
     * 按范围过滤，再换成"属于这个格子"的 UUID 追加。
     * <p>
     * 去重：如果 Curios 自己算出来的属性表（{@link CurioAttributeModifierEvent#getOriginalModifiers()}）里
     * 已经有同属性、同运算方式、同数值的一条，就跳过。有些饰品模组会把同一份属性既写进
     * ICurio 又写进原版路径，不去重就翻倍；只和 Curios 原表比，不和本模组命令追加的那批比，
     * 命令追加的属性本来就是要叠上去的。
     *
     * @param evt      Curios 属性计算事件
     * @param stack    物品
     * @param slotUuid 当前格子的 UUID
     * @param modOnly  true = 只追加本模组的属性（battlecorrection:*）
     * @return 追加的条数
     */
    private static int appendItemModifiers(@Nonnull CurioAttributeModifierEvent evt, @Nonnull ItemStack stack,
                                           @Nonnull UUID slotUuid, boolean modOnly) {
        int applied = 0;
        Multimap<Attribute, AttributeModifier> original = evt.getOriginalModifiers();
        for (CurioMark.ItemModifier item : CurioMark.collectItemModifiers(stack)) {
            Attribute attribute = item.attribute();
            if (modOnly && !isModAttribute(attribute)) {
                continue;
            }
            if (isDuplicate(original, attribute, item.modifier())) {
                continue;
            }
            evt.addModifier(attribute, forSlot(slotUuid, item.modifier()));
            applied++;
        }
        return applied;
    }

    /**
     * 是否是本模组注册的属性
     *
     * @param attribute 属性
     * @return true = 注册名以 battlecorrection: 开头
     */
    private static boolean isModAttribute(@Nonnull Attribute attribute) {
        @Nullable ResourceLocation key = ForgeRegistries.ATTRIBUTES.getKey(attribute);
        return key != null && Reference.MOD_ID.equals(key.getNamespace());
    }

    /**
     * 属性表里是否已有同属性、同运算、同数值的一条
     *
     * @param modifiers 属性表
     * @param attribute 属性
     * @param modifier  修饰符
     * @return true = 已有
     */
    private static boolean isDuplicate(@Nonnull Multimap<Attribute, AttributeModifier> modifiers,
                                       @Nonnull Attribute attribute, @Nonnull AttributeModifier modifier) {
        for (AttributeModifier existing : modifiers.get(attribute)) {
            if (existing.getOperation() == modifier.getOperation()
                    && Double.compare(existing.getAmount(), modifier.getAmount()) == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 原版路径：标记物品拿在手上、穿在身上不给属性
     * <p>
     * ItemStack#getAttributeModifiers(装备槽) 每次被调用都会触发这个事件，原版拿它的结果给实体加属性、
     * 画提示框里的"在主手时："。这里对标记物品调 clearModifiers()，两件事就都没了。
     * <p>
     * 用 LOWEST 优先级：其他模组在这个事件里加的属性（例如词条模组）会在我们之前跑完，
     * 一起被清掉，保证标记物品在原版路径上真的一条属性都不给。
     * <p>
     * 放行条件：本模组自己为饰品栏收集时（{@link CurioMark#isCollectingItemModifiers()}）——
     * 那次读到的才是放进饰品栏后要生效的属性。
     * 总开关关了也放行（{@link CurioMark#isCurioOnly(ItemStack)} 返回 false），物品恢复原版表现。
     *
     * @param evt Forge 物品属性事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemAttributeModifiers(@Nonnull ItemAttributeModifierEvent evt) {
        ItemStack stack = evt.getItemStack();
        // isCurioOnly 第一步就是读 NBT 判断有没有标记，绝大多数物品在这里就返回了
        if (!CurioMark.isCurioOnly(stack) || CurioMark.isCollectingItemModifiers()) {
            return;
        }
        evt.clearModifiers();
    }

    /**
     * 把修饰符改成"属于这个格子"的版本
     * <p>
     * 把格子的 UUID 混进修饰符 UUID：同一格子每次算出来都一样（摘下时才能精确移除），
     * 不同格子互不相同（两件一样的饰品戴在两个格子里会分别生效，不会互相覆盖）。
     * 对物品自身属性尤其重要：所有胸甲共用同一个护甲修饰符 UUID，不混的话，饰品栏里的胸甲会顶掉
     * 身上真正穿着的胸甲的护甲，摘下饰品时还会把它一起删掉。
     *
     * @param slotUuid 当前格子的 UUID
     * @param original 原修饰符
     * @return 新修饰符（数值、运算方式、名字不变）
     */
    @Nonnull
    private static AttributeModifier forSlot(@Nonnull UUID slotUuid, @Nonnull AttributeModifier original) {
        return new AttributeModifier(
                mixUuid(slotUuid, original.getId()),
                original.getName(),
                original.getAmount(),
                original.getOperation()
        );
    }

    /**
     * 把两个 UUID 按位异或，得到稳定且互不冲突的新 UUID
     *
     * @param slotUuid     饰品栏格子的 UUID
     * @param modifierUuid 修饰符原本的 UUID
     * @return 混合后的 UUID
     */
    @Nonnull
    private static UUID mixUuid(@Nonnull UUID slotUuid, @Nonnull UUID modifierUuid) {
        return new UUID(slotUuid.getMostSignificantBits() ^ modifierUuid.getMostSignificantBits(),
                slotUuid.getLeastSignificantBits() ^ modifierUuid.getLeastSignificantBits());
    }

    /**
     * 右键穿戴的目标格子
     *
     * @param stacks  格子所在的物品栏
     * @param context 格子信息
     * @param swap    true = 格子里已有物品，需要交换
     */
    private record EquipTarget(IDynamicStackHandler stacks, SlotContext context, boolean swap) {
    }

    /**
     * 右键穿戴：手持带标记的物品右键，优先放进饰品栏
     * <p>
     * 这个事件在物品本身的"使用"之前触发，放进饰品栏后直接取消后续使用，
     * 所以标记过的盔甲也会先进饰品栏，不会穿到盔甲栏；找不到能放的饰品栏时才走原本的效果。
     * 例外：标记过的防具找不到饰品栏时直接取消，什么都不做——它已经被剥夺了穿盔甲栏的资格
     * （MobMixin 让原版把它当成主手物品），再走原本的穿戴逻辑只会把它和主手物品互换，看着像 bug。
     * <p>
     * 找格子的规则和 Curios 自带的右键穿戴一致：先找空的可放格子；都满了就和第一个能取下的格子交换
     * （交换只在手上只有 1 个时进行，避免把一叠物品整叠塞进去）。
     * <p>
     * 只有服务端真正移动物品，客户端只负责取消原本的使用（防止客户端先预测成穿盔甲），
     * 移动结果由服务端同步回来，两边不会出现物品对不上的情况。
     *
     * @param evt 右键使用物品事件
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickItem(@Nonnull PlayerInteractEvent.RightClickItem evt) {
        ItemStack held = evt.getItemStack();
        if (held.isEmpty() || !CurioMark.isMarked(held) || !ConfigAttribute.CURIO_MARK_ENABLED.get()) {
            return;
        }

        Player player = evt.getEntity();
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        if (handler == null) {
            return;
        }

        EquipTarget target = findEquipTarget(handler, player, held);
        if (target == null) {
            if (CurioMark.isArmorType(held)) {
                // 标记过的防具：不能进饰品栏也不能穿盔甲栏，右键直接无事发生
                evt.setCancellationResult(InteractionResult.FAIL);
                evt.setCanceled(true);
            }
            // 其他物品没有能放的饰品栏：不拦截，走物品原本的效果（比如食物照常吃）
            return;
        }

        boolean clientSide = player.level().isClientSide();
        if (!clientSide) {
            equip(evt, player, held, target);
        }
        evt.setCancellationResult(InteractionResult.sidedSuccess(clientSide));
        evt.setCanceled(true);
    }

    /**
     * 查找右键穿戴的目标格子（只查找，不修改任何东西）
     *
     * @param handler 玩家的饰品栏
     * @param player  玩家
     * @param held    手上的物品
     * @return 目标格子；没有能放的格子时返回 null
     */
    @Nullable
    private static EquipTarget findEquipTarget(@Nonnull ICuriosItemHandler handler, @Nonnull Player player,
                                               @Nonnull ItemStack held) {
        EquipTarget swapTarget = null;

        for (Map.Entry<String, ICurioStacksHandler> entry : handler.getCurios().entrySet()) {
            ICurioStacksHandler stacksHandler = entry.getValue();
            IDynamicStackHandler stacks = stacksHandler.getStacks();
            NonNullList<Boolean> renders = stacksHandler.getRenders();

            for (int i = 0; i < stacks.getSlots(); i++) {
                // isItemValid 会触发 CurioEquipEvent，由 onCurioEquip 按标记放行
                if (!stacks.isItemValid(i, held)) {
                    continue;
                }

                boolean visible = renders.size() > i && renders.get(i);
                SlotContext context = new SlotContext(entry.getKey(), player, i, false, visible);
                ItemStack present = stacks.getStackInSlot(i);

                if (present.isEmpty()) {
                    return new EquipTarget(stacks, context, false);
                }
                if (swapTarget == null && held.getCount() == 1 && canTakeOff(stacks, present, context)) {
                    swapTarget = new EquipTarget(stacks, context, true);
                }
            }
        }
        return swapTarget;
    }

    /**
     * 判断格子里原有的饰品能不能取下来（交换前检查）
     *
     * @param stacks  格子所在的物品栏
     * @param present 格子里原有的物品
     * @param context 格子信息
     * @return true = 可以取下
     */
    private static boolean canTakeOff(@Nonnull IDynamicStackHandler stacks, @Nonnull ItemStack present,
                                      @Nonnull SlotContext context) {
        CurioUnequipEvent unequipEvent = new CurioUnequipEvent(present, context);
        MinecraftForge.EVENT_BUS.post(unequipEvent);
        if (unequipEvent.getResult() == Event.Result.DENY) {
            return false;
        }
        // 模拟取出一次：带绑定诅咒之类不能取下的饰品会取不出来
        return stacks.extractItem(context.index(), present.getMaxStackSize(), true).getCount() == present.getCount();
    }

    /**
     * 执行穿戴（仅服务端）
     *
     * @param evt    右键事件（用于得知是哪只手）
     * @param player 玩家
     * @param held   手上的物品
     * @param target 目标格子
     */
    private static void equip(@Nonnull PlayerInteractEvent.RightClickItem evt, @Nonnull Player player,
                              @Nonnull ItemStack held, @Nonnull EquipTarget target) {
        int index = target.context().index();
        ItemStack toEquip = held.copy();
        toEquip.setCount(1);

        if (target.swap()) {
            // 交换：原来的饰品回到手上
            ItemStack previous = target.stacks().getStackInSlot(index).copy();
            target.stacks().setStackInSlot(index, toEquip);
            player.setItemInHand(evt.getHand(), previous);
        } else {
            target.stacks().setStackInSlot(index, toEquip);
            // 创造模式和 Curios 一样不消耗手上的物品
            if (!player.isCreative()) {
                held.shrink(1);
            }
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 1.0F, 1.0F);

        if (LogUtil.isDetailed()) {
            LogUtil.debug(String.format("右键穿戴饰品 - 玩家: %s, 物品: %s, 饰品栏: %s#%d, 交换: %s",
                    player.getName().getString(), toEquip.getHoverName().getString(),
                    target.context().identifier(), index, target.swap()));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 玩家饰品栏数量（编辑器测试功能）的恢复
    // ═══════════════════════════════════════════════════════════════

    /**
     * 登录后按编辑器记录恢复饰品栏数量
     * <p>
     * 优先级最低：等 Curios 自己的登录处理（重建饰品栏、发同步包）跑完再动，改完再发一次同步。
     *
     * @param evt 事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerLoggedIn(@Nonnull PlayerEvent.PlayerLoggedInEvent evt) {
        if (evt.getEntity() instanceof ServerPlayer player) {
            CurioSlotManager.applySaved(player);
        }
    }

    /**
     * 重生后同样恢复（PlayerPersisted 会跟着复制过来）
     *
     * @param evt 事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerRespawn(@Nonnull PlayerEvent.PlayerRespawnEvent evt) {
        if (evt.getEntity() instanceof ServerPlayer player) {
            CurioSlotManager.applySaved(player);
        }
    }
}
