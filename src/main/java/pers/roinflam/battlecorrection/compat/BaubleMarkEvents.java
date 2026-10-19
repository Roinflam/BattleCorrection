package pers.roinflam.battlecorrection.compat;

import com.google.common.collect.Multimap;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AbstractAttributeMap;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.items.IItemHandlerModifiable;
import pers.roinflam.battlecorrection.config.ConfigBattle;
import pers.roinflam.battlecorrection.config.ConfigBaubles;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.util.ItemUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 饰品栏标记的事件处理
 * <p>
 * 1. AttachCapabilitiesEvent：给物品挂上按标记现判的饰品能力，标记物品因此能放进 Baubles 的饰品栏
 * （拖放、Shift 快速放、别的模组的自动穿戴都走这条），见 {@link BaubleMarkCapability}。
 * 2. 玩家每 tick 末尾（服务端）：
 * a) 饰品栏属性同步到属性表（{@link BaubleWornModifiers}）：本模组的附加属性 + 饰品自身的原版格式属性，
 * 戴上时加、摘下时撤，只在格子里的物品变了的时候动；
 * b) 标了多种饰品栏的物品对 Baubles 报的是 TRINKET（哪都能放），放进没标的格子时挪到标了的空格子，
 * 没有空格子就退回背包。
 * 3. 只当饰品用：1.12.2 的 Forge 没有 ItemAttributeModifierEvent，拦不住原版把手上、身上物品的属性加到实体身上。
 * 原版每次装备变动先发 LivingEquipmentChangeEvent、再加属性，所以这里在事件里记下"哪个槽换上了标记物品"，
 * 等原版加完（玩家：同一 tick 的玩家 tick 末尾；其他生物：这一 tick 的服务端末尾）再按原样撤掉。
 * 玩家把标记过的物品穿进盔甲栏时，同时把它挪进能放的饰品栏，没有就退回背包——效果上等于穿不上。
 * 4. PlayerInteractEvent.RightClickItem：手持标记物品右键时优先放进饰品栏，先于原版的盔甲穿戴、吃喝等使用效果。
 * <p>
 * 这个类会用到 Baubles 的类，只能在装了 Baubles 时由 {@link BaublesIntegration#init()} 手动注册，
 * 不能加 @Mod.EventBusSubscriber。第 3 条虽然只用 Forge 事件，也跟着这个类一起注册：
 * 没装 Baubles 时标记物品哪都放不进，再撤掉属性就成废品了。
 */
public final class BaubleMarkEvents {

    /**
     * 等着撤属性的实体：值是装备槽的位掩码（第 n 位 = EntityEquipmentSlot 序号 n）
     * <p>
     * 只在服务端线程读写（装备变动事件只在服务端触发）。
     */
    private static final Map<EntityLivingBase, Integer> PENDING = new WeakHashMap<>();

    private static final EntityEquipmentSlot[] EQUIPMENT_SLOTS = EntityEquipmentSlot.values();

    private BaubleMarkEvents() {
    }

    // ═══════════════════════════════════════════════════════════════
    // 饰品能力
    // ═══════════════════════════════════════════════════════════════

    /**
     * 物品创建时挂上饰品能力
     * <p>
     * 用 LOWEST：排在别的模组之后，能看到它们有没有已经给这件物品挂了饰品能力。
     *
     * @param evt 物品能力事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttachItemCapabilities(@Nonnull AttachCapabilitiesEvent<ItemStack> evt) {
        BaubleMarkCapability.attach(evt);
    }

    // ═══════════════════════════════════════════════════════════════
    // 玩家 tick
    // ═══════════════════════════════════════════════════════════════

    /**
     * 玩家 tick 末尾：撤掉手上、身上标记物品的属性，纠正饰品栏，同步饰品属性
     * <p>
     * 玩家的装备变动是在玩家 tick 里处理的，tick 末尾时原版已经加完属性，
     * 这时撤掉，之后收到的攻击包算伤害时就已经是撤过的数值。
     *
     * @param evt 玩家 tick 事件
     */
    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.side != Side.SERVER) {
            return;
        }
        EntityPlayer player = evt.player;
        IItemHandlerModifiable baubles = BaublesAccess.getHandler(player);

        Integer pending = PENDING.remove(player);
        if (pending != null) {
            fixEquipment(player, pending, baubles);
        }
        if (baubles == null) {
            return;
        }
        fixBaubleSlots(player, baubles);
        BaubleWornModifiers.syncAttributeMap(player, baubles);
    }

    /**
     * 服务端 tick 末尾：处理其他生物（以及没赶上自己 tick 末尾的玩家）
     *
     * @param evt 服务端 tick 事件
     */
    @SubscribeEvent
    public static void onServerTick(@Nonnull TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        List<Map.Entry<EntityLivingBase, Integer>> entries = new ArrayList<>(PENDING.entrySet());
        PENDING.clear();
        for (Map.Entry<EntityLivingBase, Integer> entry : entries) {
            EntityLivingBase entity = entry.getKey();
            if (entity == null || entity.isDead) {
                continue;
            }
            IItemHandlerModifiable baubles = entity instanceof EntityPlayer
                    ? BaublesAccess.getHandler((EntityPlayer) entity) : null;
            fixEquipment(entity, entry.getValue(), baubles);
        }
    }

    /**
     * 玩家下线：清掉缓存
     *
     * @param evt 事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(@Nonnull PlayerEvent.PlayerLoggedOutEvent evt) {
        PENDING.remove(evt.player);
        BaubleWornModifiers.forget(evt.player);
    }

    // ═══════════════════════════════════════════════════════════════
    // 只当饰品用
    // ═══════════════════════════════════════════════════════════════

    /**
     * 装备变动：换上的是"只当饰品用"的物品就记下来，等原版加完属性再撤
     * <p>
     * 这个事件在原版加属性之前触发，这里撤不掉，只能先记着。绝大多数物品在 isBaubleOnly 第一步读 NBT 时就返回了。
     *
     * @param evt 装备变动事件
     */
    @SubscribeEvent
    public static void onEquipmentChange(@Nonnull LivingEquipmentChangeEvent evt) {
        ItemStack to = evt.getTo();
        if (to.isEmpty() || !BaubleMark.isBaubleOnly(to)) {
            return;
        }
        EntityLivingBase entity = evt.getEntityLiving();
        if (entity.world.isRemote) {
            return;
        }
        int bit = 1 << evt.getSlot().ordinal();
        Integer old = PENDING.get(entity);
        PENDING.put(entity, old == null ? bit : (old | bit));
    }

    /**
     * 处理记下来的装备槽：撤属性；玩家盔甲栏里的标记物品挪走
     *
     * @param entity  实体
     * @param mask    装备槽位掩码
     * @param baubles 玩家的饰品栏；不是玩家或拿不到时为 null
     */
    private static void fixEquipment(@Nonnull EntityLivingBase entity, int mask, @Nullable IItemHandlerModifiable baubles) {
        for (EntityEquipmentSlot slot : EQUIPMENT_SLOTS) {
            if ((mask & (1 << slot.ordinal())) == 0) {
                continue;
            }
            ItemStack stack = entity.getItemStackFromSlot(slot);
            if (stack.isEmpty() || !BaubleMark.isBaubleOnly(stack)) {
                continue;
            }
            int removed = stripOwnModifiers(entity.getAttributeMap(), stack.getAttributeModifiers(slot));

            if (slot.getSlotType() == EntityEquipmentSlot.Type.ARMOR && entity instanceof EntityPlayer) {
                moveOutOfArmorSlot((EntityPlayer) entity, slot, stack, baubles);
            } else if (removed > 0 && ConfigBattle.enableDetailedLogging) {
                LogUtil.debug(String.format("撤掉标记物品在装备槽的属性 - 实体: %s, 物品: %s, 槽位: %s, 条数: %d",
                        entity.getName(), stack.getDisplayName(), slot.getName(), removed));
            }
        }
    }

    /**
     * 按原样撤掉原版刚加上的那批属性
     * <p>
     * 只撤属性表上 UUID、运算方式、数值都对得上的：两件物品用同一个 UUID 的情况（同一模板做出来的物品）下，
     * 别把另一件的属性误撤了。先查再撤也避免了每次都把属性标脏、多发同步包。
     *
     * @param map       属性表
     * @param modifiers 物品在这个装备槽给的属性
     * @return 撤掉的条数
     */
    private static int stripOwnModifiers(@Nonnull AbstractAttributeMap map,
                                         @Nonnull Multimap<String, AttributeModifier> modifiers) {
        int removed = 0;
        for (Map.Entry<String, AttributeModifier> entry : modifiers.entries()) {
            IAttributeInstance instance = map.getAttributeInstanceByName(entry.getKey());
            if (instance == null) {
                continue;
            }
            AttributeModifier own = entry.getValue();
            AttributeModifier applied = instance.getModifier(own.getID());
            if (applied != null && applied.getOperation() == own.getOperation()
                    && Double.compare(applied.getAmount(), own.getAmount()) == 0) {
                instance.removeModifier(own.getID());
                removed++;
            }
        }
        return removed;
    }

    /**
     * 标记物品穿进了盔甲栏：拿下来，放进能放的饰品栏，没有就退回背包
     * <p>
     * 1.12.2 没法拦住"放进盔甲格"（拖放、Shift、发射器都直接看物品类型），只能事后挪走。
     * Baubles 界面里 Shift 点防具时也是先找盔甲栏，挪进饰品栏正好接上玩家的本意。
     *
     * @param player  玩家
     * @param slot    盔甲槽
     * @param stack   物品
     * @param baubles 玩家的饰品栏
     */
    private static void moveOutOfArmorSlot(@Nonnull EntityPlayer player, @Nonnull EntityEquipmentSlot slot,
                                           @Nonnull ItemStack stack, @Nullable IItemHandlerModifiable baubles) {
        ItemStack moving = stack.copy();
        player.setItemStackToSlot(slot, ItemStack.EMPTY);

        int target = baubles == null || moving.getCount() != 1 ? -1 : findEmptyBaubleSlot(player, baubles, moving, -1);
        if (target >= 0 && placeInto(baubles, target, moving)) {
            BaublesAccess.onEquipped(moving, player);
            playEquipSound(player);
        } else {
            target = -1;
            giveBack(player, moving);
            // 名字从原物品取：moving 放回背包后数量被清零，再取就成了空气
            player.sendStatusMessage(new TextComponentTranslation("message.battlecorrection.bauble.armor_blocked",
                    ItemUtil.chatName(stack)), true);
        }

        if (ConfigBattle.enableDetailedLogging) {
            LogUtil.debug(String.format("标记物品不能穿在盔甲栏 - 玩家: %s, 物品: %s, 槽位: %s, 去向: %s",
                    player.getName(), stack.getDisplayName(), slot.getName(),
                    target >= 0 ? "饰品栏#" + target : "背包"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 饰品栏纠正
    // ═══════════════════════════════════════════════════════════════

    /**
     * 把放错格子的标记物品挪走
     * <p>
     * 只管本模组按标记给了饰品能力的物品（原生饰品由它自己的类型决定，不动）：
     * a) 标了多种饰品栏的物品对 Baubles 报 TRINKET，Baubles 会让它进任意一格，没标的那几格要挪走；
     * b) 标记里只剩不认识的 ID（手写 NBT）、已经不算饰品的物品留在格子里，玩家去拿时 Baubles 会空指针，也挪走。
     * 总开关关着时不动：开关只管能不能戴上，已经戴着的不强制摘下。
     *
     * @param player  玩家
     * @param baubles 饰品栏
     */
    private static void fixBaubleSlots(@Nonnull EntityPlayer player, @Nonnull IItemHandlerModifiable baubles) {
        if (!ConfigBaubles.baubleMarkEnabled) {
            return;
        }
        for (int i = 0; i < baubles.getSlots(); i++) {
            ItemStack stack = baubles.getStackInSlot(i);
            if (stack.isEmpty() || !stack.hasTagCompound() || !stack.getTagCompound().hasKey(BaubleMark.TAG_SLOTS)) {
                continue;
            }
            String slotType = BaubleMark.slotTypeAt(i);
            boolean misplaced;
            if (BaublesAccess.isMarkedBauble(stack)) {
                misplaced = slotType != null && !BaubleMark.canEquip(stack, slotType);
            } else {
                misplaced = !BaublesAccess.isBauble(stack);
            }
            if (!misplaced) {
                continue;
            }

            ItemStack moving = stack.copy();
            baubles.setStackInSlot(i, ItemStack.EMPTY);
            int target = BaublesAccess.isMarkedBauble(moving) ? findEmptyBaubleSlot(player, baubles, moving, i) : -1;
            if (target < 0 || !placeInto(baubles, target, moving)) {
                target = -1;
                giveBack(player, moving);
            }

            if (ConfigBattle.enableDetailedLogging) {
                LogUtil.debug(String.format("饰品栏纠正 - 玩家: %s, 物品: %s, 原格子: %d（%s），去向: %s，标记为 %s",
                        player.getName(), stack.getDisplayName(), i, slotType,
                        target >= 0 ? "饰品栏#" + target : "背包", BaubleMark.getSlots(stack)));
            }
        }
    }

    /**
     * 找一个能放这件物品的空饰品格
     * <p>
     * 既要 Baubles 自己放行（饰品类型、canEquip），本模组的标记物品还要标记里有这一格的类型。
     *
     * @param player  玩家
     * @param baubles 饰品栏
     * @param stack   物品
     * @param except  跳过的格子；-1 = 不跳过
     * @return 格子序号；没有时返回 -1
     */
    private static int findEmptyBaubleSlot(@Nonnull EntityPlayer player, @Nonnull IItemHandlerModifiable baubles,
                                           @Nonnull ItemStack stack, int except) {
        boolean marked = BaublesAccess.isMarkedBauble(stack);
        for (int i = 0; i < baubles.getSlots(); i++) {
            if (i == except || !baubles.getStackInSlot(i).isEmpty()) {
                continue;
            }
            if (canGoInto(player, baubles, i, stack, marked)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 物品能不能放进某一格
     *
     * @param player  玩家
     * @param baubles 饰品栏
     * @param index   格子序号
     * @param stack   物品
     * @param marked  物品的饰品能力是不是本模组按标记给的
     * @return true = 能
     */
    private static boolean canGoInto(@Nonnull EntityPlayer player, @Nonnull IItemHandlerModifiable baubles, int index,
                                     @Nonnull ItemStack stack, boolean marked) {
        if (!BaublesAccess.isItemValidForSlot(baubles, index, stack, player)) {
            return false;
        }
        return !marked || BaubleMark.canEquip(stack, BaubleMark.slotTypeAt(index));
    }

    /**
     * 往饰品格里放物品，并确认真的放进去了
     * <p>
     * Baubles 的 setStackInSlot 遇到它认为不合法的物品会直接忽略，不报错也不返回结果；
     * 不确认的话，调用方接着把物品从别处删掉，物品就凭空没了。
     *
     * @param baubles 饰品栏
     * @param index   格子序号
     * @param stack   物品
     * @return true = 放进去了
     */
    private static boolean placeInto(@Nonnull IItemHandlerModifiable baubles, int index, @Nonnull ItemStack stack) {
        baubles.setStackInSlot(index, stack);
        return baubles.getStackInSlot(index) == stack;
    }

    /**
     * 物品退回背包，背包满了就丢在脚下
     * <p>
     * 创造模式下原版 addItemStackToInventory 放不下的部分会被直接抹掉（数量清零、照样返回 true），
     * 背包满着的管理员身上被挪出来的物品就凭空没了。所以创造模式只在有空格、一格放得下整组时才往背包放，
     * 否则直接丢在脚下。
     *
     * @param player 玩家
     * @param stack  物品
     */
    private static void giveBack(@Nonnull EntityPlayer player, @Nonnull ItemStack stack) {
        InventoryPlayer inventory = player.inventory;
        boolean safe = !player.capabilities.isCreativeMode
                || (inventory.getFirstEmptyStack() >= 0
                && stack.getCount() <= Math.min(stack.getMaxStackSize(), inventory.getInventoryStackLimit()));
        if (safe) {
            inventory.addItemStackToInventory(stack);
        }
        if (!stack.isEmpty()) {
            player.dropItem(stack, false);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 右键穿戴
    // ═══════════════════════════════════════════════════════════════

    /**
     * 右键穿戴：手持带标记的物品右键，优先放进饰品栏
     * <p>
     * 这个事件在物品本身的"使用"之前触发，放进饰品栏后直接取消后续使用，
     * 所以标记过的盔甲也会先进饰品栏，不会穿到盔甲栏；找不到能放的饰品栏时才走原本的效果。
     * 例外：标记过的防具找不到饰品栏时直接取消，什么都不做——它已经不能穿盔甲栏了，
     * 再走原本的穿戴逻辑只会穿上去又被挪下来。
     * <p>
     * 找格子的规则：先找空的可放格子；都满了就和第一个能取下的格子交换
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
        if (held.isEmpty() || !BaubleMark.isMarked(held) || !ConfigBaubles.baubleMarkEnabled) {
            return;
        }

        EntityPlayer player = evt.getEntityPlayer();
        IItemHandlerModifiable baubles = BaublesAccess.getHandler(player);
        if (baubles == null) {
            return;
        }

        int target = findEmptyBaubleSlot(player, baubles, held, -1);
        boolean swap = false;
        if (target < 0 && held.getCount() == 1) {
            target = findSwapSlot(player, baubles, held);
            swap = target >= 0;
        }
        if (target < 0) {
            if (BaubleMark.isArmorType(held)) {
                // 标记过的防具：不能进饰品栏也不能穿盔甲栏，右键直接无事发生
                evt.setCancellationResult(EnumActionResult.FAIL);
                evt.setCanceled(true);
            }
            // 其他物品没有能放的饰品栏：不拦截，走物品原本的效果（比如食物照常吃）
            return;
        }

        if (!player.world.isRemote) {
            equip(evt, player, baubles, held, target, swap);
        }
        evt.setCancellationResult(EnumActionResult.SUCCESS);
        evt.setCanceled(true);
    }

    /**
     * 都满了时找一个能交换的格子
     *
     * @param player  玩家
     * @param baubles 饰品栏
     * @param held    手上的物品
     * @return 格子序号；没有时返回 -1
     */
    private static int findSwapSlot(@Nonnull EntityPlayer player, @Nonnull IItemHandlerModifiable baubles,
                                    @Nonnull ItemStack held) {
        boolean marked = BaublesAccess.isMarkedBauble(held);
        for (int i = 0; i < baubles.getSlots(); i++) {
            ItemStack present = baubles.getStackInSlot(i);
            if (present.isEmpty() || !canGoInto(player, baubles, i, held, marked)) {
                continue;
            }
            if (BaublesAccess.canUnequip(present, player)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 执行穿戴（仅服务端）
     *
     * @param evt     右键事件（用于得知是哪只手）
     * @param player  玩家
     * @param baubles 饰品栏
     * @param held    手上的物品
     * @param index   目标格子
     * @param swap    true = 和格子里原有的交换
     */
    private static void equip(@Nonnull PlayerInteractEvent.RightClickItem evt, @Nonnull EntityPlayer player,
                              @Nonnull IItemHandlerModifiable baubles, @Nonnull ItemStack held, int index, boolean swap) {
        ItemStack toEquip = held.copy();
        toEquip.setCount(1);

        ItemStack previous = baubles.getStackInSlot(index);
        if (!placeInto(baubles, index, toEquip)) {
            return;
        }
        if (swap) {
            // 交换：原来的饰品回到手上
            BaublesAccess.onUnequipped(previous, player);
            player.setHeldItem(evt.getHand(), previous);
        } else if (!player.capabilities.isCreativeMode) {
            // 创造模式不消耗手上的物品
            held.shrink(1);
        }
        BaublesAccess.onEquipped(toEquip, player);
        playEquipSound(player);

        if (ConfigBattle.enableDetailedLogging) {
            LogUtil.debug(String.format("右键穿戴饰品 - 玩家: %s, 物品: %s, 饰品栏: %d（%s），交换: %s",
                    player.getName(), toEquip.getDisplayName(), index, BaubleMark.slotTypeAt(index), swap));
        }
    }

    /**
     * 播放穿戴音效
     *
     * @param player 玩家
     */
    private static void playEquipSound(@Nonnull EntityPlayer player) {
        player.world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.ITEM_ARMOR_EQUIP_GENERIC, SoundCategory.PLAYERS, 1.0F, 1.0F);
    }
}
