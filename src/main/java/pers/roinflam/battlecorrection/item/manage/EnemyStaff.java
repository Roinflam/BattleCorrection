package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

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
 * 旧版本把 EntityLiving 对象直接存在物品上（物品全局只有一个实例），
 * 玩家选完第一只就下线、或者怪被卸载时，这个实体（连带它所在的整个世界）会一直留在内存里。
 */
@Mod.EventBusSubscriber
public class EnemyStaff extends ItemStaff {

    /**
     * 玩家UUID -> 第一次选中的目标（只在服务端线程访问）
     */
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();

    /**
     * 第一次选择的记录
     */
    private static final class Selection {
        /**
         * 第一个目标的实体UUID
         */
        private final UUID targetId;
        /**
         * 第一个目标所在的维度
         */
        private final int dimension;

        private Selection(@Nonnull UUID targetId, int dimension) {
            this.targetId = targetId;
            this.dimension = dimension;
        }
    }

    public EnemyStaff(@Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        super(name, creativeTabs);
    }

    /**
     * 右键生物：第一次记录目标，第二次让两个目标互相攻击
     */
    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer playerIn, EntityLivingBase target, @Nonnull EnumHand hand) {
        if (hand != EnumHand.MAIN_HAND || !(target instanceof EntityLiving) || !target.isEntityAlive()) {
            return false;
        }
        if (playerIn.world.isRemote) {
            return true;
        }
        if (!(playerIn.world instanceof WorldServer)) {
            return false;
        }

        WorldServer worldServer = (WorldServer) playerIn.world;
        EntityLiving targetMob = (EntityLiving) target;
        UUID playerId = playerIn.getUniqueID();
        @Nullable Selection selection = SELECTIONS.get(playerId);

        if (selection == null) {
            // 第一次点击 - 记录目标
            SELECTIONS.put(playerId, new Selection(targetMob.getUniqueID(), worldServer.provider.getDimension()));
            sendMessage(playerIn, "message.battlecorrection.select_first");
        } else if (selection.targetId.equals(targetMob.getUniqueID())) {
            // 点到了同一只：保持选择，提示去点另一只（避免让它把自己设为目标）
            sendMessage(playerIn, "message.battlecorrection.select_first");
        } else {
            // 第二次点击 - 取出第一个目标并清除选择
            SELECTIONS.remove(playerId);

            // 只在同一维度里查找第一个目标；已死亡、已卸载或在别的维度都算失效
            @Nullable Entity first = worldServer.provider.getDimension() == selection.dimension
                    ? worldServer.getEntityFromUuid(selection.targetId)
                    : null;

            if (first instanceof EntityLiving && first.isEntityAlive()) {
                EntityLiving firstMob = (EntityLiving) first;
                targetMob.setAttackTarget(firstMob);
                firstMob.setAttackTarget(targetMob);
                sendMessage(playerIn, "message.battlecorrection.select_second");
            } else {
                sendMessage(playerIn, "message.battlecorrection.select_cancel");
            }
        }

        // 设置冷却
        playerIn.getCooldownTracker().setCooldown(this, 20);
        return true;
    }

    /**
     * 给玩家发送一条红色的提示消息
     *
     * @param player 玩家
     * @param key    翻译键
     */
    private static void sendMessage(@Nonnull EntityPlayer player, @Nonnull String key) {
        TextComponentTranslation message = new TextComponentTranslation(key);
        message.getStyle().setColor(TextFormatting.RED);
        player.sendMessage(message);
    }

    /**
     * 玩家登出事件 - 清理未完成的选择
     *
     * @param evt 玩家登出事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(@Nonnull PlayerEvent.PlayerLoggedOutEvent evt) {
        SELECTIONS.remove(evt.player.getUniqueID());
    }
}
