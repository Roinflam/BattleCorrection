package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 敌对权杖
 * 右键点击两个生物使它们互相攻击
 * <p>
 * 第一次选择只记录目标的 UUID 和所在维度，不持有实体对象：
 * 旧版本把 Mob 对象直接存在物品上（物品全局只有一个实例），
 * 玩家选完第一只就下线、或者怪被卸载时，这个实体（连带它所在的整个世界）会一直留在内存里。
 */
public class EnemyStaff extends ItemStaff {

    /**
     * 玩家UUID -> 第一次选中的目标（只在服务端线程访问）
     */
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();

    /**
     * 第一次选择的记录
     *
     * @param targetId  第一个目标的实体UUID
     * @param dimension 第一个目标所在的维度
     */
    private record Selection(UUID targetId, ResourceKey<Level> dimension) {
    }

    /**
     * 构造敌对权杖
     *
     * @param properties 物品属性
     */
    public EnemyStaff(@Nonnull Properties properties) {
        super(properties);
    }

    /**
     * 右键生物：第一次记录目标，第二次让两个目标互相攻击
     *
     * @param stack  手中的权杖
     * @param player 使用者
     * @param target 被右键的生物
     * @param hand   使用的手
     * @return 服务端主手成功处理时返回 SUCCESS，否则 PASS
     */
    @Override
    @Nonnull
    public InteractionResult interactLivingEntity(@Nonnull ItemStack stack, @Nonnull Player player,
                                                  @Nonnull LivingEntity target, @Nonnull InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || player.level().isClientSide()) {
            return InteractionResult.PASS;
        }
        if (!(target instanceof Mob targetMob) || !targetMob.isAlive()) {
            return InteractionResult.PASS;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        UUID playerId = player.getUUID();
        @Nullable Selection selection = SELECTIONS.get(playerId);

        if (selection == null) {
            // 第一次点击 - 记录目标
            SELECTIONS.put(playerId, new Selection(targetMob.getUUID(), serverLevel.dimension()));
            sendMessage(player, "message.battlecorrection.select_first");
        } else if (selection.targetId().equals(targetMob.getUUID())) {
            // 点到了同一只：保持选择，提示去点另一只（避免让它把自己设为目标）
            sendMessage(player, "message.battlecorrection.select_first");
        } else {
            // 第二次点击 - 取出第一个目标并清除选择
            SELECTIONS.remove(playerId);

            // 只在同一维度里查找第一个目标；已死亡、已卸载或在别的维度都算失效
            @Nullable Entity first = serverLevel.dimension().equals(selection.dimension())
                    ? serverLevel.getEntity(selection.targetId())
                    : null;

            if (first instanceof Mob firstMob && firstMob.isAlive()) {
                targetMob.setTarget(firstMob);
                firstMob.setTarget(targetMob);
                sendMessage(player, "message.battlecorrection.select_second");
            } else {
                sendMessage(player, "message.battlecorrection.select_cancel");
            }
        }

        // 设置冷却
        player.getCooldowns().addCooldown(this, 20);
        return InteractionResult.SUCCESS;
    }

    /**
     * 给玩家发送一条红色的提示消息
     *
     * @param player 玩家
     * @param key    翻译键
     */
    private static void sendMessage(@Nonnull Player player, @Nonnull String key) {
        player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.RED));
    }

    /**
     * 玩家下线时清理其未完成的选择
     */
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID)
    public static final class SelectionCleaner {

        private SelectionCleaner() {
        }

        /**
         * 玩家登出事件 - 清理选择记录
         *
         * @param evt 玩家登出事件
         */
        @SubscribeEvent
        public static void onPlayerLoggedOut(@Nonnull PlayerEvent.PlayerLoggedOutEvent evt) {
            SELECTIONS.remove(evt.getEntity().getUUID());
        }
    }
}
