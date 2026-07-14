package com.cell4.common.storage;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.cell4.common.integration.MekanismIntegration;
import com.cell4.common.item.IInfinityCell;
import com.cell4.common.item.InfinityModIdCell;
import com.cell4.common.util.Cell4Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;

import java.util.List;
import java.util.Set;

/**
 * StorageCell implementation for the Infinity ModID Cell.
 * Provides infinite extraction of any item/fluid from specified mods (namespaces).
 * Insertion of matching keys is accepted but items are silently discarded (phantom storage).
 * Supports blacklist via cell4blacklist NBT key.
 */
public class InfinityModIdStorage extends AbstractInfinityStorage {

    private final List<String> modIds;
    private final Set<String> modIdSet;

    // 2.2: Cached available stacks
    private KeyCounter cachedAvailableStacks;
    private boolean cacheValid = false;

    public InfinityModIdStorage(ItemStack cellItem) {
        super(cellItem);
        this.modIds = InfinityModIdCell.getModIds(cellItem);
        this.modIdSet = Set.copyOf(modIds);
    }

    @Override
    protected boolean matchesFilter(AEKey what) {
        return Cell4Util.belongsToMod(what, modIdSet);
    }

    @Override
    protected boolean hasConfiguration() {
        return !modIds.isEmpty();
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (modIds.isEmpty()) return;

        // 2.2: Lazy-load and cache available stacks
        if (!cacheValid) {
            cachedAvailableStacks = new KeyCounter();

            for (var item : BuiltInRegistries.ITEM) {
                if (Cell4Util.belongsToMod(AEItemKey.of(item), modIdSet)) {
                    var key = AEItemKey.of(item);
                    if (key != null && !blacklist.isBlacklisted(key)) {
                        cachedAvailableStacks.add(key, IInfinityCell.getAsIntMax(key));
                    }
                }
            }

            for (var fluid : BuiltInRegistries.FLUID) {
                if (fluid == Fluids.EMPTY) continue;
                ResourceLocation rl = BuiltInRegistries.FLUID.getKey(fluid);
                if (rl != null && rl.getPath().startsWith("flowing_")) continue;
                var key = AEFluidKey.of(fluid);
                if (key != null && Cell4Util.belongsToMod(key, modIdSet) && !blacklist.isBlacklisted(key)) {
                    cachedAvailableStacks.add(key, IInfinityCell.getAsIntMax(key));
                }
            }

            // Mekanism chemicals (only if Mekanism + AppMek installed)
            for (AEKey chemicalKey : MekanismIntegration.getAllChemicalKeys()) {
                if (Cell4Util.belongsToMod(chemicalKey, modIdSet) && !blacklist.isBlacklisted(chemicalKey)) {
                    cachedAvailableStacks.add(chemicalKey, IInfinityCell.getAsIntMax(chemicalKey));
                }
            }
            cacheValid = true;
        }
        // Use set() instead of add() — see InfinityItemStorage for the full rationale.
        for (var entry : cachedAvailableStacks) {
            AEKey key = entry.getKey();
            long existing = out.get(key);
            if (existing < 0) {
                out.set(key, Long.MAX_VALUE);
            } else if (existing < Long.MAX_VALUE) {
                out.set(key, Long.MAX_VALUE);
            }
        }
    }

    @Override
    public Component getDescription() {
        return Component.translatable("item.cell4.infinity_modid_cell");
    }
}
