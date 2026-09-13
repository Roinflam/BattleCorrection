package pers.roinflam.battlecorrection.client.editor;

import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 读客户端已知的饰品栏信息
 * <p>
 * 这个类引用了 Curios 的类，只能在 {@code CuriosIntegration.isCuriosLoaded()} 为 true 时调用；
 * 没装 Curios 时只要不调它，JVM 就不会去加载它，不会崩。所以它单独成一个类，别把方法挪进页面里。
 * 饰品栏列表和玩家的饰品栏都是服务端同步到客户端的，和服务器一致。
 */
public final class CurioSlotsClient {

    private CurioSlotsClient() {
    }

    /**
     * 全部已注册的饰品栏 ID，按字母排序
     *
     * @return ID 列表
     */
    @Nonnull
    public static List<String> slotIds() {
        List<String> ids = new ArrayList<>(CuriosApi.getSlots(true).keySet());
        Collections.sort(ids);
        return ids;
    }

    /**
     * 数据包分配给玩家的饰品栏 ID
     *
     * @return ID 集合
     */
    @Nonnull
    public static Set<String> assignedSlotIds() {
        return new TreeSet<>(CuriosApi.getPlayerSlots(true).keySet());
    }

    /**
     * 玩家有没有饰品栏能力
     * <p>
     * Curios 只给"至少被分配了一种饰品栏"的实体装能力；一种都没有的玩家这里返回 false，什么都改不了。
     *
     * @param player 玩家（客户端实体）
     * @return true = 有
     */
    public static boolean hasInventory(@Nonnull Player player) {
        return CuriosApi.getCuriosInventory(player).isPresent();
    }

    /**
     * 某个玩家当前每种饰品栏的数量（按 ID 排序）
     *
     * @param player 玩家（客户端实体）
     * @return ID → 数量；没有饰品栏能力时为空表
     */
    @Nonnull
    public static Map<String, Integer> slotCounts(@Nonnull Player player) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        if (handler == null) {
            return counts;
        }
        List<String> ids = new ArrayList<>(handler.getCurios().keySet());
        Collections.sort(ids);
        for (String id : ids) {
            ICurioStacksHandler stacks = handler.getCurios().get(id);
            counts.put(id, stacks == null ? 0 : stacks.getSlots());
        }
        return counts;
    }
}
