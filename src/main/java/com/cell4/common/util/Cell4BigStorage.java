package com.cell4.common.util;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 全局 BigInteger 存储层，跟踪超过 Long.MAX_VALUE 的真实数值。
 *
 * <p>AE2 的 KeyCounter 内部使用 long 算术。当所有 cell 的计数总和超过
 * {@link Long#MAX_VALUE} 时，counter 会溢出为负数，导致：</p>
 * <ol>
 *   <li>ae2wtlib restock overlay 崩溃（ReadableNumberConverter 拒绝负数）</li>
 *   <li>自动合成失败（负数可用量被当作"无物品"）</li>
 * </ol>
 *
 * <p>此类提供一种方式来存储溢出 key 的 <em>真实</em> BigInteger 数值。
 * KeyCounter 本身被 clamp 到 {@link Long#MAX_VALUE}（所以永远不会变负），
 * 而精确的 BigInteger 值在这里被跟踪。</p>
 *
 * <p>map 用 {@link AEKey} 作为 key（它的 equals/hashCode 基于内容 —
 * item/fluid/chemical 身份）。条目跨 KeyCounter 实例持久存在，所以即使
 * AE2 为每次网络查询创建新的 KeyCounter，真实数值也能被保留。</p>
 *
 * <p>内存管理：条目在溢出发生时添加，但不会显式移除。实际上，溢出的不同 key
 * 数量很少（只有无限元件中存储的物品），所以内存使用受存档中无限元件配置数量限制。</p>
 */
public final class Cell4BigStorage {

    private static final ConcurrentMap<AEKey, BigInteger> BIG_AMOUNTS = new ConcurrentHashMap<>();

    private Cell4BigStorage() {}

    /**
     * 获取 key 的真实 BigInteger 数值，如果从未溢出过则返回 null。
     */
    public static BigInteger get(AEKey key) {
        return BIG_AMOUNTS.get(key);
    }

    /**
     * 向 key 的真实 BigInteger 数值添加正数。
     * 如果 key 之前未被跟踪，则从给定的 long 基线开始
     * （通常是当前 KeyCounter 值，可能是 Long.MAX_VALUE）。
     *
     * @param key       要添加到的 AEKey
     * @param amount    要添加的非负数量
     * @param baseline  KeyCounter 中的当前 long 值（用于在首次溢出时初始化 BigInteger）
     * @return 添加后的新 BigInteger 数值
     */
    public static BigInteger addPositive(AEKey key, long amount, long baseline) {
        return BIG_AMOUNTS.compute(key, (k, existing) -> {
            BigInteger base = existing;
            if (base == null) {
                base = BigInteger.valueOf(baseline);
            }
            return base.add(BigInteger.valueOf(amount));
        });
    }

    /**
     * 从 key 的真实 BigInteger 数值中减去正数。
     * 如果结果降回到 Long.MAX_VALUE 以下，条目会被移除
     * （这样后续读取会回退到基于 long 的 KeyCounter 值）。
     *
     * @param key       要减去的 AEKey
     * @param amount    要减去的非负数量
     * @return 减去后的新 BigInteger 数值，如果条目被移除则返回 null
     *         （因为结果可以放入 long）
     */
    public static BigInteger subtract(AEKey key, long amount) {
        BigInteger[] resultHolder = new BigInteger[1];
        BIG_AMOUNTS.compute(key, (k, existing) -> {
            if (existing == null) {
                return null;
            }
            BigInteger newValue = existing.subtract(BigInteger.valueOf(amount));
            if (newValue.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) < 0) {
                resultHolder[0] = newValue;
                return null;
            }
            resultHolder[0] = newValue;
            return newValue;
        });
        return resultHolder[0];
    }

    /**
     * 直接设置 key 的真实 BigInteger 数值（无限元件用此方法报告 Long.MAX_VALUE 作为"真实"数值）。
     */
    public static void set(AEKey key, BigInteger value) {
        if (value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            BIG_AMOUNTS.put(key, value);
        } else {
            BIG_AMOUNTS.remove(key);
        }
    }

    /**
     * 停止跟踪某个 key（例如无限元件从网络中移除时）。
     */
    public static void remove(AEKey key) {
        BIG_AMOUNTS.remove(key);
    }

    /**
     * 获取 key 的有效 long 值。如果真实 BigInteger 数值超过 Long.MAX_VALUE，
     * 返回 Long.MAX_VALUE。否则返回 BigInteger 作为 long。
     */
    public static long getEffectiveLong(AEKey key, long fallback) {
        BigInteger big = BIG_AMOUNTS.get(key);
        if (big == null) {
            return fallback;
        }
        if (big.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) >= 0) {
            return Long.MAX_VALUE;
        }
        return big.longValue();
    }

    /**
     * 检查 key 是否正在被跟踪（即其真实数值超过 Long.MAX_VALUE）。
     */
    public static boolean isTracked(AEKey key) {
        return BIG_AMOUNTS.containsKey(key);
    }

    /**
     * 清除所有跟踪条目（用于测试，或世界卸载时清理）。
     */
    public static void clear() {
        BIG_AMOUNTS.clear();
    }
}
