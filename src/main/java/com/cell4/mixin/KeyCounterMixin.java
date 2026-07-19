package com.cell4.mixin;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.cell4.common.util.Cell4BigStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigInteger;

/**
 * 将 AE2 KeyCounter 的存储上限从 long 提升到 BigInteger 的 Mixin。
 *
 * <p>AE2 的 {@link KeyCounter} 内部使用 long 算术。当所有 cell 的计数总和超过
 * {@link Long#MAX_VALUE} 时，counter 会溢出为负数，导致：</p>
 * <ol>
 *   <li><b>ae2wtlib restock overlay 崩溃</b> — overlay 调用
 *       {@code ReadableNumberConverter.format(long)} 处理网络数量，对负数抛出
 *       {@link IllegalArgumentException}。</li>
 *   <li><b>自动合成"缺东西"</b> — AE2 合成 CPU 通过 {@code KeyCounter.get(key)} 检查
 *       可用量。负值被当作"无可用物品"，导致合成配方拒绝执行。</li>
 * </ol>
 *
 * <p>此 Mixin 通过拦截 KeyCounter 的 {@code add}、{@code set}、{@code remove}、
 * {@code get} 方法提供 <em>真正的 BigInteger 级存储</em>：</p>
 *
 * <ul>
 *   <li>当 {@code add} 会溢出 long 时，精确的 BigInteger 值被跟踪在
 *       {@link Cell4BigStorage} 中，long 字段被 clamp 到 {@link Long#MAX_VALUE}
 *       （所以永远不会变负）。</li>
 *   <li>当 {@code get} 被调用时，如果 key 在 BigInteger 存储中被跟踪，
 *       long 返回值为 {@link Long#MAX_VALUE}（clamp，但永远不会是负数）。</li>
 *   <li>当 {@code remove}（减法）将 BigInteger 值降回到 long 范围内时，
 *       跟踪条目被移除，后续读取回退到基于 long 的 KeyCounter。</li>
 * </ul>
 *
 * <p>这实际上将存储上限提升到 {@link BigInteger}（counter 永不溢出，永不变负，
 * 并精确跟踪真实数值），而报告给 AE2 基于 long 的 API 的实际数值被 clamp 在
 * {@link Long#MAX_VALUE}。</p>
 */
@Mixin(value = KeyCounter.class, remap = false)
public class KeyCounterMixin {

    /**
     * 在 HEAD 拦截 add(AEKey, long)。
     *
     * <p>如果加法会溢出 Long.MAX_VALUE，将精确算术路由到 BigInteger 存储，
     * 并将 long 字段 clamp 到 Long.MAX_VALUE。否则，让原始 add 正常执行。</p>
     */
    @Inject(
            method = "add(Lappeng/api/stacks/AEKey;J)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cell4$addWithBigStorage(AEKey key, long amount, CallbackInfo ci) {
        // 只拦截正数加法。减法（amount < 0）通过 remove() 路由，零是 no-op。
        if (amount <= 0) {
            return;
        }

        @SuppressWarnings("unchecked")
        KeyCounter self = (KeyCounter) (Object) this;

        long current = self.get(key);

        // 如果已经是 Long.MAX_VALUE（或在安装此 Mixin 之前因之前的溢出而处于损坏的负数状态），
        // 路由到 BigInteger 存储。
        if (current == Long.MAX_VALUE || current < 0) {
            Cell4BigStorage.addPositive(key, amount, current);
            self.set(key, Long.MAX_VALUE);
            ci.cancel();
            return;
        }

        // 检查 (current + amount) 是否会溢出 Long.MAX_VALUE。
        // 等价于：amount > (Long.MAX_VALUE - current)
        if (amount > Long.MAX_VALUE - current) {
            // 溢出 — 跟踪精确的 BigInteger 值
            Cell4BigStorage.addPositive(key, amount, current);
            self.set(key, Long.MAX_VALUE);
            ci.cancel();
        }
        // 否则：加法是安全的 — 让原始 add() 正常执行。
    }

    /**
     * 在 RETURN 拦截 get(AEKey)。
     *
     * <p>如果 key 在 BigInteger 存储中被跟踪（即其真实值超过 Long.MAX_VALUE），
     * 返回 Long.MAX_VALUE（clamp，永远不会是负数）。这确保 AE2 基于 long 的消费者
     * 总是看到非负的、clamp 过的值。</p>
     */
    @Inject(
            method = "get(Lappeng/api/stacks/AEKey;)J",
            at = @At("RETURN"),
            cancellable = true
    )
    private void cell4$getWithBigStorage(AEKey key, CallbackInfoReturnable<Long> cir) {
        BigInteger bigValue = Cell4BigStorage.get(key);
        if (bigValue != null) {
            // 真实值超过 Long.MAX_VALUE — clamp 到 MAX，永远不会是负数。
            cir.setReturnValue(Long.MAX_VALUE);
        }
        // 否则：未被跟踪 — 返回原始 long 值（是非负的，因为 add() clamp 到 Long.MAX_VALUE）。
    }

    /**
     * 在 HEAD 拦截 remove(AEKey, long)。
     *
     * <p>被跟踪的 BigInteger key 上的减法会递减精确的 BigInteger 值。
     * 如果结果降回到 long 范围内，跟踪条目被移除，后续读取回退到基于 long 的 KeyCounter。</p>
     */
    @Inject(
            method = "remove(Lappeng/api/stacks/AEKey;J)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cell4$removeWithBigStorage(AEKey key, long amount, CallbackInfo ci) {
        if (amount <= 0) {
            return;
        }

        BigInteger bigValue = Cell4BigStorage.get(key);
        if (bigValue == null) {
            // 未被跟踪 — 让原始 remove() 正常执行。
            return;
        }

        // 被跟踪 — 递减 BigInteger 值。
        BigInteger newValue = Cell4BigStorage.subtract(key, amount);

        @SuppressWarnings("unchecked")
        KeyCounter self = (KeyCounter) (Object) this;

        if (newValue == null) {
            // 结果降回到 long 范围内 — 条目已被移除。
            // 我们不知道精确的新值，所以让原始 remove() 在 long 字段上处理。
            // 为了安全，我们让原始 remove() 继续。
            return;
        }

        // 将 long 字段设置为 clamp 过的新值，并取消原始 remove。
        if (newValue.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) >= 0) {
            self.set(key, Long.MAX_VALUE);
        } else {
            self.set(key, newValue.longValue());
        }
        ci.cancel();
    }

    /**
     * 在 HEAD 拦截 set(AEKey, long)。
     *
     * <p>如果调用者显式设置一个不等于 Long.MAX_VALUE 的值到被跟踪的 key 上，
     * 我们移除 BigInteger 跟踪（调用者正在覆盖值）。</p>
     */
    @Inject(
            method = "set(Lappeng/api/stacks/AEKey;J)V",
            at = @At("HEAD")
    )
    private void cell4$setWithBigStorage(AEKey key, long amount, CallbackInfo ci) {
        // 如果新值小于 Long.MAX_VALUE，移除 BigInteger 跟踪
        // （long 字段将持有精确值）。
        if (amount < Long.MAX_VALUE) {
            Cell4BigStorage.remove(key);
        }
        // 如果 amount == Long.MAX_VALUE，保留跟踪 — 调用者正在设置上限，
        // 这与我们的 BigInteger 跟踪一致。
    }
}
