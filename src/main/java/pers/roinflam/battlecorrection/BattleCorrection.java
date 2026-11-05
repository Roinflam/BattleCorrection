package pers.roinflam.battlecorrection;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import pers.roinflam.battlecorrection.compat.CuriosIntegration;
import pers.roinflam.battlecorrection.compat.DevCurioSlotsCondition;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.config.ConfigBattle;
import pers.roinflam.battlecorrection.init.ModAttributes;
import pers.roinflam.battlecorrection.init.ModCreativeTabs;
import pers.roinflam.battlecorrection.init.ModItems;
import pers.roinflam.battlecorrection.init.ModMobEffects;
import pers.roinflam.battlecorrection.network.ModNetwork;
import pers.roinflam.battlecorrection.utils.Reference;

/**
 * 战斗修正模组主类
 * <p>
 * 1.21.1（NeoForge）：模组总线和 {@link ModContainer} 由构造器参数传进来，不再从全局上下文取；
 * 网络包在 {@code RegisterPayloadHandlersEvent} 里登记（{@link ModNetwork#register}）。
 * </p>
 */
@Mod(Reference.MOD_ID)
public class BattleCorrection {

    public static final Logger LOGGER = LogUtils.getLogger();

    public BattleCorrection(IEventBus modEventBus, ModContainer container) {
        LOGGER.info("=======================================");
        LOGGER.info("  战斗修正模组 (Battle Correction)");
        LOGGER.info("  作者: Roinflam");
        LOGGER.info("=======================================");

        // 注册延迟注册器
        ModItems.ITEMS.register(modEventBus);
        ModAttributes.ATTRIBUTES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModMobEffects.MOB_EFFECTS.register(modEventBus);
        // 数据包条件：开发环境给玩家默认饰品栏（配置 devCurioSlots 控制）
        DevCurioSlotsCondition.register(modEventBus);

        // 注册生命周期事件
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(ConfigAttribute::onConfigLoading);

        // 注册网络包（两端都要，顺序两端一致）
        modEventBus.addListener(ModNetwork::register);

        // 注册配置
        container.registerConfig(ModConfig.Type.COMMON, ConfigBattle.SPEC, "battlecorrection-battle.toml");
        container.registerConfig(ModConfig.Type.COMMON, ConfigAttribute.SPEC, "battlecorrection-attribute.toml");

        // 配置界面入口在客户端入口类 BattleCorrectionClient 里登记

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
    @EventBusSubscriber(modid = Reference.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static class ModEvents {

        @SubscribeEvent
        public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
            // 为所有生物实体添加自定义属性
            for (EntityType<? extends LivingEntity> entityType : event.getTypes()) {
                // 伤害增强属性
                if (!event.has(entityType, ModAttributes.MAGIC_DAMAGE)) {
                    event.add(entityType, ModAttributes.MAGIC_DAMAGE);
                }
                if (!event.has(entityType, ModAttributes.ARROW_DAMAGE)) {
                    event.add(entityType, ModAttributes.ARROW_DAMAGE);
                }
                if (!event.has(entityType, ModAttributes.PROJECTILE_DAMAGE)) {
                    event.add(entityType, ModAttributes.PROJECTILE_DAMAGE);
                }

                // 防御与减伤属性
                if (!event.has(entityType, ModAttributes.IMMUNE_DAMAGE)) {
                    event.add(entityType, ModAttributes.IMMUNE_DAMAGE);
                }
                if (!event.has(entityType, ModAttributes.IGNORE_DAMAGE)) {
                    event.add(entityType, ModAttributes.IGNORE_DAMAGE);
                }
                if (!event.has(entityType, ModAttributes.REDUCED_FALL_DAMAGE)) {
                    event.add(entityType, ModAttributes.REDUCED_FALL_DAMAGE);
                }

                // 生命恢复属性
                if (!event.has(entityType, ModAttributes.RESTORE_HEAL)) {
                    event.add(entityType, ModAttributes.RESTORE_HEAL);
                }
                if (!event.has(entityType, ModAttributes.BLOODTHIRSTY)) {
                    event.add(entityType, ModAttributes.BLOODTHIRSTY);
                }
                if (!event.has(entityType, ModAttributes.ALMIGHTY_BLOODTHIRSTY)) {
                    event.add(entityType, ModAttributes.ALMIGHTY_BLOODTHIRSTY);
                }

                // 速度与机动性属性
                if (!event.has(entityType, ModAttributes.BOW_SPEED)) {
                    event.add(entityType, ModAttributes.BOW_SPEED);
                }
                if (!event.has(entityType, ModAttributes.PREPARATION_SPEED)) {
                    event.add(entityType, ModAttributes.PREPARATION_SPEED);
                }
                if (!event.has(entityType, ModAttributes.JUMP_LIFT)) {
                    event.add(entityType, ModAttributes.JUMP_LIFT);
                }

                // 暴击系统属性
                if (!event.has(entityType, ModAttributes.VANILLA_CRITICAL_HIT_DAMAGE)) {
                    event.add(entityType, ModAttributes.VANILLA_CRITICAL_HIT_DAMAGE);
                }
                if (!event.has(entityType, ModAttributes.CUSTOM_CRITICAL_CHANCE)) {
                    event.add(entityType, ModAttributes.CUSTOM_CRITICAL_CHANCE);
                }
                if (!event.has(entityType, ModAttributes.CUSTOM_CRITICAL_DAMAGE)) {
                    event.add(entityType, ModAttributes.CUSTOM_CRITICAL_DAMAGE);
                }
            }

            LOGGER.info("已为所有实体类型注册 15 个自定义属性");
        }
    }
}
