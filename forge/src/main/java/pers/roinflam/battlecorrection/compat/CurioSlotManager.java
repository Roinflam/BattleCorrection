package pers.roinflam.battlecorrection.compat;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import pers.roinflam.battlecorrection.utils.LogUtil;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.inventory.CurioStacksHandler;
import top.theillusivec4.curios.common.network.NetworkHandler;
import top.theillusivec4.curios.common.network.server.sync.SPacketSyncCurios;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * 玩家饰品栏数量管理（服务端，测试用）
 * <p>
 * Curios 5 里玩家有哪几种饰品栏是数据包（curios/entities/*.json）决定的，
 * {@code /curios set} 只能改玩家已经有的那种的数量，没有的那种直接跳过。
 * 这里绕过去：玩家没有的类型就现建一个 {@link CurioStacksHandler} 塞进他的饰品栏表，再发一次完整同步包；
 * 已经有的就用 Curios 自己的 grow/shrink（它会转成永久性的槽位修饰符，能存盘）。
 * <p>
 * 现建出来的类型 Curios 存盘时不认（重进时它只按数据包分配的类型重建，多出来的会被丢掉，里面的物品退回背包），
 * 所以把编辑器设过的数量记在玩家的持久化 NBT 里，登录和重生后再按记录加回去。
 * <p>
 * 引用了 Curios 的实现类（CurioStacksHandler、NetworkHandler、SPacketSyncCurios），版本对应 5.14.1；
 * 只能在 {@link CuriosIntegration#isCuriosLoaded()} 为 true 时调用。
 */
public final class CurioSlotManager {

    /**
     * 玩家持久化 NBT 里记数量的键：{饰品栏ID: 数量}
     */
    public static final String TAG_SLOT_COUNTS = "BattleCorrectionCurioSlotCounts";

    /**
     * 单种饰品栏数量上限
     */
    public static final int MAX_SLOTS = 64;

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_.\\-]{1,32}");

    private CurioSlotManager() {
    }

    /**
     * 饰品栏 ID 是否合法（小写字母、数字、下划线、点、横线，1-32 位）
     *
     * @param slotId ID
     * @return true = 合法
     */
    public static boolean isValidId(@Nullable String slotId) {
        return slotId != null && ID_PATTERN.matcher(slotId).matches();
    }

    /**
     * 把玩家某种饰品栏的数量设成指定值，并记到持久化数据
     *
     * @param player 玩家
     * @param slotId 饰品栏 ID
     * @param amount 目标数量（0 = 没有）
     * @return 给玩家看的反馈
     */
    @Nonnull
    public static Component setSlotCount(@Nonnull ServerPlayer player, @Nonnull String slotId, int amount) {
        int target = Mth.clamp(amount, 0, MAX_SLOTS);
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        if (handler == null) {
            return Component.translatable("message.battlecorrection.slots.no_inventory").withStyle(ChatFormatting.RED);
        }

        int before = currentCount(handler, slotId);
        apply(player, handler, slotId, target);
        remember(player, slotId, target);
        sync(player, handler);

        LogUtil.info(String.format("%s 把自己的饰品栏 %s 数量从 %d 改为 %d", player.getName().getString(), slotId, before, target));
        return Component.translatable("message.battlecorrection.slots.set", CurioMark.slotName(slotId), target)
                .withStyle(ChatFormatting.GREEN);
    }

    /**
     * 登录/重生后按持久化记录把数量加回去
     *
     * @param player 玩家
     */
    public static void applySaved(@Nonnull ServerPlayer player) {
        CompoundTag counts = savedCounts(player);
        if (counts == null || counts.isEmpty()) {
            return;
        }
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        if (handler == null) {
            return;
        }
        int applied = 0;
        for (String slotId : counts.getAllKeys()) {
            if (!isValidId(slotId)) {
                continue;
            }
            int target = Mth.clamp(counts.getInt(slotId), 0, MAX_SLOTS);
            if (currentCount(handler, slotId) != target) {
                apply(player, handler, slotId, target);
                applied++;
            }
        }
        if (applied > 0) {
            sync(player, handler);
            if (LogUtil.isDetailed()) {
                LogUtil.debug(String.format("按编辑器记录为 %s 恢复了 %d 种饰品栏数量", player.getName().getString(), applied));
            }
        }
    }

    /**
     * 玩家当前某种饰品栏的数量
     *
     * @param handler 饰品栏
     * @param slotId  ID
     * @return 数量；没有这种类型返回 0
     */
    private static int currentCount(@Nonnull ICuriosItemHandler handler, @Nonnull String slotId) {
        return handler.getStacksHandler(slotId).map(ICurioStacksHandler::getSlots).orElse(0);
    }

    /**
     * 真正改数量：已有的类型走 grow/shrink，没有的现建
     *
     * @param player  玩家
     * @param handler 饰品栏
     * @param slotId  ID
     * @param target  目标数量
     */
    private static void apply(@Nonnull ServerPlayer player, @Nonnull ICuriosItemHandler handler,
                              @Nonnull String slotId, int target) {
        Optional<ICurioStacksHandler> existing = handler.getStacksHandler(slotId);
        if (existing.isPresent()) {
            int current = existing.get().getSlots();
            if (target > current) {
                handler.growSlotType(slotId, target - current);
            } else if (target < current) {
                // 缩减到 0 而不是把类型整个摘掉：Curios 自己会把多出来的物品退回背包、撤掉属性
                handler.shrinkSlotType(slotId, current - target);
            }
            return;
        }
        if (target <= 0) {
            return;
        }

        // 玩家没有这种类型：按注册的定义建，没注册（自定义 ID）就用默认参数
        ISlotType type = CuriosApi.getSlot(slotId, false).orElse(null);
        CurioStacksHandler created = type != null
                ? new CurioStacksHandler(handler, slotId, target, type.isVisible(), type.hasCosmetic(),
                type.canToggleRendering(), type.getDropRule())
                : new CurioStacksHandler(handler, slotId, target, true, false, true, ICurio.DropRule.DEFAULT);
        SortedMap<String, ICurioStacksHandler> curios = new TreeMap<>(handler.getCurios());
        curios.put(slotId, created);
        handler.setCurios(curios);
        if (LogUtil.isDetailed()) {
            LogUtil.debug(String.format("为 %s 新建饰品栏类型 %s ×%d（%s）", player.getName().getString(), slotId, target,
                    type != null ? "已注册" : "自定义"));
        }
    }

    /**
     * 把整份饰品栏同步给玩家自己和看得见他的人
     *
     * @param player  玩家
     * @param handler 饰品栏
     */
    private static void sync(@Nonnull ServerPlayer player, @Nonnull ICuriosItemHandler handler) {
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new SPacketSyncCurios(player.getId(), handler.getCurios()));
    }

    /**
     * 记到玩家持久化 NBT（PlayerPersisted，死亡重生也会保留）
     *
     * @param player 玩家
     * @param slotId ID
     * @param amount 数量；0 = 删掉记录
     */
    private static void remember(@Nonnull ServerPlayer player, @Nonnull String slotId, int amount) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag counts = persisted.getCompound(TAG_SLOT_COUNTS);
        if (amount <= 0) {
            counts.remove(slotId);
        } else {
            counts.putInt(slotId, amount);
        }
        if (counts.isEmpty()) {
            persisted.remove(TAG_SLOT_COUNTS);
        } else {
            persisted.put(TAG_SLOT_COUNTS, counts);
        }
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /**
     * 读持久化记录
     *
     * @param player 玩家
     * @return 记录；没有返回 null
     */
    @Nullable
    private static CompoundTag savedCounts(@Nonnull ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG)) {
            return null;
        }
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        return persisted.contains(TAG_SLOT_COUNTS) ? persisted.getCompound(TAG_SLOT_COUNTS) : null;
    }
}
