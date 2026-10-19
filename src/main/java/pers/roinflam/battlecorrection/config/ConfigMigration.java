package pers.roinflam.battlecorrection.config;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 配置文件版本迁移
 * <p>
 * 几个配置项改过含义（和 1.20.1 版统一），旧文件里的数值要换算成新含义，
 * 否则老服更新后行为会突变：
 * <ul>
 *   <li>{@code vanillaCriticalHitDamage}：旧公式直接把原版暴击倍率覆盖成 (装备-1)+配置，
 *       原意是「在 100% 之上加多少」；新公式在原版 1.5 倍之上加，所以 新值 = 旧值 - 0.5（不低于 0）</li>
 *   <li>{@code bowSpeed} / {@code preparationSpeed}：旧公式 装备 + 配置，新公式 (装备-1) + 配置，
 *       所以 新值 = 旧值 + 1，实际速度不变</li>
 *   <li>{@code hungerDamageDecay} / {@code hungerDamageDecayLimit}：1.2.0 之前的默认值是 0.05 / 0.05，
 *       一直留在老配置文件里，表现为饱食度不满时每一击少 5%（「伤害超过 1 时丢 0.5」）。
 *       恰好是这组旧默认值时改回 0 / 0；其他值是服主自己调的，保留</li>
 *   <li>{@code hurtTimeEntity} / {@code hurtTimePlayer}：1.2.0 之前的默认值是 1.0，
 *       配合旧公式正好等于原版无敌时间；新公式下原版是 0，恰好是 1.0 时改成 0，其他值保留</li>
 * </ul>
 * <p>
 * 必须在 Forge 的 {@code ConfigManager} 读取配置文件之前执行（模组主类构造器里调用）：
 * 它读完文件后，加载阶段内的 sync 会以文件为准，再改字段写不回去。
 * 迁移前把原文件备份成 {@code battlecorrection.cfg.v<旧版本>.bak}。
 */
public final class ConfigMigration {

    /**
     * 当前配置格式版本
     * 0 = 没有 configVersion 键的旧文件（1.2.0 及以前）
     */
    public static final int CURRENT_VERSION = 1;

    private static final String CATEGORY_BATTLE = "battle";
    private static final String CATEGORY_ATTRIBUTE = "attribute";
    private static final String KEY_VERSION = "configVersion";

    /**
     * 浮点比较容差（配置文件里的数值是 float 转出来的十进制串）
     */
    private static final double EPSILON = 1.0E-6D;

    private ConfigMigration() {
    }

    /**
     * 检查并迁移配置文件
     *
     * @param configDir 配置目录（{@code Loader.instance().getConfigDir()}）
     */
    public static void migrateBeforeLoad(@Nonnull File configDir) {
        File file = new File(configDir, Reference.MOD_ID + ".cfg");
        if (!file.isFile()) {
            // 全新安装：Forge 会按默认值写出文件，默认值里的 configVersion 就是当前版本
            return;
        }

        try {
            Configuration cfg = new Configuration(file);
            int fromVersion = readVersion(cfg);
            if (fromVersion >= CURRENT_VERSION) {
                return;
            }

            File backup = new File(configDir, Reference.MOD_ID + ".cfg.v" + fromVersion + ".bak");
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);

            List<String> changes = new ArrayList<>();
            if (fromVersion < 1) {
                migrateToV1(cfg, changes);
            }

            cfg.get(CATEGORY_BATTLE, KEY_VERSION, CURRENT_VERSION).set(CURRENT_VERSION);
            cfg.save();

            LogUtil.info(String.format("配置文件已从版本 %d 迁移到 %d，原文件备份为 %s",
                    fromVersion, CURRENT_VERSION, backup.getName()));
            for (String change : changes) {
                LogUtil.info("  " + change);
            }
        } catch (IOException | RuntimeException e) {
            // 迁移失败不阻止启动，按文件原值加载
            LogUtil.error("配置文件迁移失败，将按原有数值加载: " + file, e);
        }
    }

    /**
     * 读文件里的格式版本，没有该键视为 0
     */
    private static int readVersion(@Nonnull Configuration cfg) {
        Property property = find(cfg, CATEGORY_BATTLE, KEY_VERSION);
        if (property == null) {
            return 0;
        }
        return property.getInt(0);
    }

    /**
     * 版本 0 → 1
     */
    private static void migrateToV1(@Nonnull Configuration cfg, @Nonnull List<String> changes) {
        // 原版暴击：新值 = 旧值 - 0.5，不低于 0
        Property crit = find(cfg, CATEGORY_ATTRIBUTE, "vanillaCriticalHitDamage");
        if (crit != null) {
            double old = crit.getDouble(0.5D);
            set(crit, Math.max(0.0D, old - 0.5D), old, changes);
        }

        // 拉弓 / 使用速度：新值 = 旧值 + 1
        for (String key : new String[]{"bowSpeed", "preparationSpeed"}) {
            Property speed = find(cfg, CATEGORY_ATTRIBUTE, key);
            if (speed != null) {
                double old = speed.getDouble(0.0D);
                set(speed, old + 1.0D, old, changes);
            }
        }

        // 饥饿衰减：只有恰好是旧默认 0.05 / 0.05 才改回 0
        Property decay = find(cfg, CATEGORY_BATTLE, "hungerDamageDecay");
        Property limit = find(cfg, CATEGORY_BATTLE, "hungerDamageDecayLimit");
        if (decay != null && limit != null
                && near(decay.getDouble(0.0D), 0.05D) && near(limit.getDouble(0.0D), 0.05D)) {
            set(decay, 0.0D, 0.05D, changes);
            set(limit, 0.0D, 0.05D, changes);
        }

        // 无敌时间：只有恰好是旧默认 1.0 才改成 0
        for (String key : new String[]{"hurtTimeEntity", "hurtTimePlayer"}) {
            Property hurtTime = find(cfg, CATEGORY_BATTLE, key);
            if (hurtTime != null && near(hurtTime.getDouble(0.0D), 1.0D)) {
                set(hurtTime, 0.0D, 1.0D, changes);
            }
        }
    }

    @Nullable
    private static Property find(@Nonnull Configuration cfg, @Nonnull String category, @Nonnull String key) {
        if (!cfg.hasCategory(category)) {
            return null;
        }
        ConfigCategory cat = cfg.getCategory(category);
        return cat.containsKey(key) ? cat.get(key) : null;
    }

    private static void set(@Nonnull Property property, double value, double old, @Nonnull List<String> changes) {
        if (near(value, old)) {
            return;
        }
        property.set(value);
        changes.add(String.format("%s: %s -> %s", property.getName(), trim(old), trim(value)));
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < EPSILON;
    }

    private static String trim(double value) {
        // 0.30000001192092896 这种 float 转出来的尾巴不往日志里写
        return String.valueOf((float) value);
    }
}
