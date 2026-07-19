package com.cell4.mixin.ae2wtlib;

import de.mari_023.ae2wtlib.wct.CraftingTerminalHandler;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复 ae2wtlib 的 CraftingTerminalHandler.getAccessibleAmount 算术溢出。
 *
 * <p>ae2wtlib 的 restock overlay 通过 getAccessibleAmount 获取物品的可访问数量：</p>
 * <pre>{@code
 * return stack.getCount() + restockAbleItems.get(stack.getItem());
 * }</pre>
 *
 * <p>当无限元件报告 Long.MAX_VALUE 时，restockAbleItems 中的值也是 Long.MAX_VALUE。
 * stack.getCount() (int) + Long.MAX_VALUE (long) 会溢出为负数，导致：</p>
 * <ol>
 *   <li>restock overlay 显示 "0"（被 ReadableNumberConverterMixin 转成 "0"）</li>
 *   <li>或 amount <= 1 检查拦截，不显示数字</li>
 * </ol>
 *
 * <p>此 Mixin 在 RETURN 拦截 getAccessibleAmount，检测到负数（溢出）时
 * 返回 Long.MAX_VALUE，表示"无限"。</p>
 */
@Mixin(value = CraftingTerminalHandler.class, remap = false)
public class CraftingTerminalHandlerMixin {

    @Inject(
            method = "getAccessibleAmount",
            at = @At("RETURN"),
            cancellable = true
    )
    private void fixOverflow(ItemStack stack, CallbackInfoReturnable<Long> cir) {
        long amount = cir.getReturnValue();
        if (amount < 0) {
            // stack.getCount() + restockAmount 溢出为负数
            // 返回 Long.MAX_VALUE 表示"无限"
            cir.setReturnValue(Long.MAX_VALUE);
        }
    }
}
