// 文件：EnemyStaff.java
// 路径：src/main/java/pers/roinflam/battlecorrection/item/manage/EnemyStaff.java
package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.UUID;

/**
 * 敌对权杖
 * 右键点击两个生物使它们互相攻击
 */
public class EnemyStaff extends ItemStaff {

    /**
     * 存储玩家第一次选择的目标
     */
    private final HashMap<UUID, Mob> selectedTargets = new HashMap<>();

    public EnemyStaff(@Nonnull Properties properties) {
        super(properties);
    }

    @Override
    @Nonnull
    public InteractionResult interactLivingEntity(@Nonnull ItemStack stack, @Nonnull Player player,
                                                  @Nonnull LivingEntity target, @Nonnull InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !player.level().isClientSide()) {
            if (target instanceof Mob targetMob) {
                if (target.isAlive()) {
                    UUID uuid = player.getUUID();

                    if (selectedTargets.containsKey(uuid)) {
                        // 第二次点击 - 设置两个生物互相攻击
                        Mob firstTarget = selectedTargets.get(uuid);

                        if (firstTarget.isAlive()) {
                            // 设置互相攻击
                            targetMob.setTarget(firstTarget);
                            firstTarget.setTarget(targetMob);

                            // 发送消息
                            Component message = Component.translatable("message.battlecorrection.select_second")
                                    .withStyle(ChatFormatting.RED);
                            player.sendSystemMessage(message);
                        } else {
                            // 第一个目标已死亡
                            Component message = Component.translatable("message.battlecorrection.select_cancel")
                                    .withStyle(ChatFormatting.RED);
                            player.sendSystemMessage(message);
                        }

                        // 清除选择
                        selectedTargets.remove(uuid);
                    } else {
                        // 第一次点击 - 记录目标
                        selectedTargets.put(uuid, targetMob);

                        Component message = Component.translatable("message.battlecorrection.select_first")
                                .withStyle(ChatFormatting.RED);
                        player.sendSystemMessage(message);
                    }

                    // 设置冷却
                    player.getCooldowns().addCooldown(this, 20);
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return InteractionResult.PASS;
    }
}