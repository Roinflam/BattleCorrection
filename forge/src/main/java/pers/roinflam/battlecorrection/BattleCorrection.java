// 文件：BattleCorrection.java
// 路径：src/main/java/pers/roinflam/battlecorrection/BattleCorrection.java
package pers.roinflam.battlecorrection;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import pers.roinflam.battlecorrection.compat.CuriosIntegration;
import pers.roinflam.battlecorrection.config.ClothConfigScreen;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.config.ConfigBattle;
import pers.roinflam.battlecorrection.init.ModAttributes;
import pers.roinflam.battlecorrection.init.ModCreativeTabs;
import pers.roinflam.battlecorrection.init.ModItems;
import pers.roinflam.battlecorrection.init.ModMobEffects;
import pers.roinflam.battlecorrection.utils.Reference;

/**
 * BattleCorrection 战斗修正模组 - 主类 (1.20.1)
 */
@Mod(Reference.MOD_ID)
public class BattleCorrection {

    public static final Logger LOGGER = LogUtils.getLogger();

    public BattleCorrection() {
        LOGGER.info("═══════════════════════════════════════");
        LOGGER.info("  战斗修正模组 (Battle Correction)");
        LOGGER.info("  作者: Roinflam");
        LOGGER.info("═══════════════════════════════════════");

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册延迟注册器
        ModItems.ITEMS.register(modEventBus);
        ModAttributes.ATTRIBUTES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModMobEffects.MOB_EFFECTS.register(modEventBus);

        // 注册生命周期事件
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        // 注册配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ConfigBattle.SPEC, "battlecorrection-battle.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ConfigAttribute.SPEC, "battlecorrection-attribute.toml");

        // 注册配置屏幕
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, screen) -> ClothConfigScreen.createConfigScreen(screen)
                )
        );

        // 注册Forge事件总线
        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("战斗修正模组 - 注册完成");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // 初始化Curios集成（在工作队列中执行，确保线程安全）
        event.enqueueWork(CuriosIntegration::init);

        LOGGER.info("战斗修正模组 - 通用设置完成");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("战斗修正模组 - 客户端设置完成");
    }

    /**
     * Mod事件总线订阅者
     */
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {

        @SubscribeEvent
        public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
            // 为所有生物实体添加自定义属性
            for (EntityType<? extends LivingEntity> entityType : event.getTypes()) {
                // 伤害增强属性
                if (!event.has(entityType, ModAttributes.MAGIC_DAMAGE.get())) {
                    event.add(entityType, ModAttributes.MAGIC_DAMAGE.get());
                }
                if (!event.has(entityType, ModAttributes.ARROW_DAMAGE.get())) {
                    event.add(entityType, ModAttributes.ARROW_DAMAGE.get());
                }
                if (!event.has(entityType, ModAttributes.PROJECTILE_DAMAGE.get())) {
                    event.add(entityType, ModAttributes.PROJECTILE_DAMAGE.get());
                }

                // 防御与减伤属性
                if (!event.has(entityType, ModAttributes.IMMUNE_DAMAGE.get())) {
                    event.add(entityType, ModAttributes.IMMUNE_DAMAGE.get());
                }
                if (!event.has(entityType, ModAttributes.IGNORE_DAMAGE.get())) {
                    event.add(entityType, ModAttributes.IGNORE_DAMAGE.get());
                }
                if (!event.has(entityType, ModAttributes.REDUCED_FALL_DAMAGE.get())) {
                    event.add(entityType, ModAttributes.REDUCED_FALL_DAMAGE.get());
                }

                // 生命恢复属性
                if (!event.has(entityType, ModAttributes.RESTORE_HEAL.get())) {
                    event.add(entityType, ModAttributes.RESTORE_HEAL.get());
                }
                if (!event.has(entityType, ModAttributes.BLOODTHIRSTY.get())) {
                    event.add(entityType, ModAttributes.BLOODTHIRSTY.get());
                }
                if (!event.has(entityType, ModAttributes.ALMIGHTY_BLOODTHIRSTY.get())) {
                    event.add(entityType, ModAttributes.ALMIGHTY_BLOODTHIRSTY.get());
                }

                // 速度与机动性属性
                if (!event.has(entityType, ModAttributes.BOW_SPEED.get())) {
                    event.add(entityType, ModAttributes.BOW_SPEED.get());
                }
                if (!event.has(entityType, ModAttributes.PREPARATION_SPEED.get())) {
                    event.add(entityType, ModAttributes.PREPARATION_SPEED.get());
                }
                if (!event.has(entityType, ModAttributes.JUMP_LIFT.get())) {
                    event.add(entityType, ModAttributes.JUMP_LIFT.get());
                }

                // 暴击系统属性
                if (!event.has(entityType, ModAttributes.VANILLA_CRITICAL_HIT_DAMAGE.get())) {
                    event.add(entityType, ModAttributes.VANILLA_CRITICAL_HIT_DAMAGE.get());
                }
                if (!event.has(entityType, ModAttributes.CUSTOM_CRITICAL_CHANCE.get())) {
                    event.add(entityType, ModAttributes.CUSTOM_CRITICAL_CHANCE.get());
                }
                if (!event.has(entityType, ModAttributes.CUSTOM_CRITICAL_DAMAGE.get())) {
                    event.add(entityType, ModAttributes.CUSTOM_CRITICAL_DAMAGE.get());
                }
            }

            LOGGER.info("已为所有实体类型注册 15 个自定义属性");
        }
    }
}