package com.cell4.mixin;

import appeng.util.ReadableNumberConverter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 防御性 Mixin，防止负数到达 AE2 数字格式化器时导致客户端崩溃。
 *
 * <p>AE2 的 {@link ReadableNumberConverter#format(long, int)} 显式拒绝负数输入，
 * 抛出 {@link IllegalArgumentException}。这是无限元件物品的网络 counter 溢出为负值时，
 * {@code ae2wtlib$restockOverlay} 崩溃的直接原因。</p>
 *
 * <p>有了 {@link KeyCounterMixin}，网络 counter 永远不会变负（被 clamp 到
 * {@link Long#MAX_VALUE}）。但是，其他 mod 或未来的 AE2 更改可能仍然在边界情况下
 * 产生负数。此 Mixin 在格式化边界捕获任何负数输入，并将其渲染为 "0" 而不是
 * 崩溃客户端。</p>
 */
@Mixin(value = ReadableNumberConverter.class, remap = false)
public class ReadableNumberConverterMixin {

    @Inject(
            method = "format(JI)Ljava/lang/String;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void cell4$clampNegativeLong(long number, int digits, CallbackInfoReturnable<String> cir) {
        if (number < 0) {
            cir.setReturnValue("0");
        }
    }

    @Inject(
            method = "format(DI)Ljava/lang/String;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void cell4$clampNegativeDouble(double number, int digits, CallbackInfoReturnable<String> cir) {
        if (number < 0) {
            cir.setReturnValue("0");
        }
    }
}
