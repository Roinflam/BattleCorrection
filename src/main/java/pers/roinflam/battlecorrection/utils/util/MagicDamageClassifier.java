package pers.roinflam.battlecorrection.utils.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import pers.roinflam.battlecorrection.config.ConfigBattle;
import pers.roinflam.battlecorrection.utils.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 魔法伤害分类器（不依赖任何第三方模组）
 *
 * <p>判断这次 {@link DamageSource} 算不算"魔法伤害"。
 * {@code AttributeMagicDamage} 用它决定要不要加魔法伤害；
 * {@code DamageEventListener} 用它决定要不要叠玩家魔法攻击/承受倍率。
 * 魔法是独立于近战/箭矢/弹射物基础层的叠加层：基础层按投递方式照常结算，魔法只在其上追加，两者互不排斥。</p>
 *
 * <p><b>判定顺序</b>（命中一条即返回）：</p>
 * <ol>
 *   <li><b>召唤物/宠物挥砍排除</b>：直接实体是活物、但归属实体是另一个实体。
 *       这本质上是一次近战命中，不按魔法算。</li>
 *   <li><b>黑名单</b>（配置 {@code magicDamageTypeBlacklist}）：伤害类型在黑名单里 → 一律不算魔法，
 *       优先级高于下面所有规则。</li>
 *   <li><b>原版规则</b>：{@link DamageSource#isMagicDamage()}，或伤害类型名含 "magic"。</li>
 *   <li><b>第三方规则</b>（总开关 {@code enableThirdPartyMagicRecognition}）：
 *       白名单精确命中，或伤害类型名以 {@code magicDamageTypePrefixes} 里的某个前缀开头。</li>
 * </ol>
 *
 * <p>1.12.2 的伤害类型只是一个字符串（{@link DamageSource#getDamageType()}），没有命名空间也没有 tag，
 * 1.20.1 的「命名空间」规则在这里换成「名字前缀」，{@code forge:is_magic} tag 规则没有对应物。</p>
 *
 * <p><b>性能</b>：配置里的列表解析成 {@link Set} 缓存在 {@link Snapshot} 里，
 * 每次伤害事件只做几次 HashSet 查询；配置界面保存后调用 {@link #invalidateCache()} 重建。</p>
 */
public final class MagicDamageClassifier {

    /**
     * 配置解析后的只读快照
     */
    private static final class Snapshot {
        /** 第三方魔法识别总开关 */
        private final boolean enabled;
        /** 整组算魔法的伤害类型前缀（已小写） */
        private final String[] prefixes;
        /** 精确算魔法的伤害类型（已小写） */
        private final Set<String> whitelist;
        /** 精确不算魔法的伤害类型（已小写） */
        private final Set<String> blacklist;

        private Snapshot(boolean enabled, String[] prefixes, Set<String> whitelist, Set<String> blacklist) {
            this.enabled = enabled;
            this.prefixes = prefixes;
            this.whitelist = whitelist;
            this.blacklist = blacklist;
        }
    }

    /** 当前生效的快照；配置变更后置为 null，下一次判定时懒重建 */
    @Nullable
    private static volatile Snapshot cachedSnapshot = null;

    private MagicDamageClassifier() {
    }

    // 对外判定入口

    /**
     * 判断一次伤害是否算魔法伤害（原版规则 + 第三方规则）
     *
     * @param source 伤害源，可为 null（返回 false）
     * @return 是否按魔法伤害处理
     */
    public static boolean isMagic(@Nullable DamageSource source) {
        if (source == null) {
            return false;
        }

        // 1. 召唤物/宠物替主人挥砍：直接实体是活物但归属实体是别人，按近战不按魔法
        if (isMinionMelee(source)) {
            return false;
        }

        Snapshot current = snapshot();
        String type = source.getDamageType().toLowerCase(Locale.ROOT);

        // 2. 黑名单一票否决（仅在第三方识别开启时生效）
        if (current.enabled && current.blacklist.contains(type)) {
            return false;
        }

        // 3. 原版规则
        if (source.isMagicDamage() || type.contains("magic")) {
            return true;
        }

        // 4. 第三方规则
        if (!current.enabled) {
            return false;
        }
        if (current.whitelist.contains(type)) {
            return true;
        }
        for (String prefix : current.prefixes) {
            if (type.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为召唤物/宠物替主人造成的近战命中
     * <p>
     * 条件：直接实体是活物，且归属实体存在、不是同一个实体。
     * 施法者自己的触碰/射线法术（直接实体 == 归属实体 == 施法者）不满足该条件，不会被误排。
     *
     * @param source 伤害源
     * @return 是否为召唤物近战
     */
    private static boolean isMinionMelee(@Nonnull DamageSource source) {
        Entity direct = source.getImmediateSource();
        Entity owner = source.getTrueSource();
        return direct instanceof EntityLivingBase && owner != null && owner != direct;
    }

    // 配置缓存

    /**
     * 使缓存失效；配置界面保存后调用
     */
    public static void invalidateCache() {
        cachedSnapshot = null;
    }

    private static Snapshot snapshot() {
        Snapshot current = cachedSnapshot;
        if (current != null) {
            return current;
        }
        Snapshot built = new Snapshot(ConfigBattle.enableThirdPartyMagicRecognition,
                parse(ConfigBattle.magicDamageTypePrefixes).toArray(new String[0]),
                parse(ConfigBattle.magicDamageTypeWhitelist),
                parse(ConfigBattle.magicDamageTypeBlacklist));
        if (LogUtil.isDetailed()) {
            LogUtil.debug("[MagicDamageClassifier] 配置快照重建: enabled=" + built.enabled
                    + ", prefixes=" + built.prefixes.length + ", whitelist=" + built.whitelist.size()
                    + ", blacklist=" + built.blacklist.size());
        }
        cachedSnapshot = built;
        return built;
    }

    /**
     * 解析配置列表：去空白、转小写、丢弃空串
     */
    private static Set<String> parse(@Nullable String[] raw) {
        if (raw == null || raw.length == 0) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            String trimmed = entry.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
