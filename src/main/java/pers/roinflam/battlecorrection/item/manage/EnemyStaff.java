package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnemyStaff extends ItemStaff {
    private final HashMap<UUID, EntityLiving> hashMap = new HashMap<>();

    public EnemyStaff(@Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        super(name, creativeTabs);
    }

    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer playerIn, EntityLivingBase target, @Nonnull EnumHand hand) {
        if (hand.equals(EnumHand.MAIN_HAND) && !playerIn.world.isRemote) {
            if (target instanceof EntityLiving) {
                EntityLiving targetEntityLiving = (EntityLiving) target;
                if (target.isEntityAlive()) {
                    UUID uuid = playerIn.getUniqueID();
                    if (hashMap.containsKey(uuid)) {
                        EntityLiving entityLiving = hashMap.get(uuid);
                        if (entityLiving.isEntityAlive()) {
                            targetEntityLiving.setAttackTarget(entityLiving);
                            entityLiving.setAttackTarget(targetEntityLiving);
                            TextComponentTranslation textComponentString = new TextComponentTranslation("message.battlecorrection.select_second");
                            textComponentString.getStyle().setColor(TextFormatting.RED);
                            playerIn.sendMessage(textComponentString);
                        } else {
                            TextComponentTranslation textComponentString = new TextComponentTranslation("message.battlecorrection.select_cancel");
                            textComponentString.getStyle().setColor(TextFormatting.RED);
                            playerIn.sendMessage(textComponentString);
                        }
                        hashMap.remove(uuid);
                    } else {
                        hashMap.put(uuid, targetEntityLiving);
                        TextComponentTranslation textComponentString = new TextComponentTranslation("message.battlecorrection.select_first");
                        textComponentString.getStyle().setColor(TextFormatting.RED);
                        playerIn.sendMessage(textComponentString);
                    }
                    playerIn.getCooldownTracker().setCooldown(this, 20);
                    return true;
                }
            }
        }
        return false;
    }
}
