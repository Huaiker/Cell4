package com.cell4.client;

import com.cell4.common.item.IInfinityCell;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterSelectItemModelPropertyEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 26.1 item-model integration: a custom string property that returns the
 * cell4texture value of the stack. The generated resource pack contains
 * minecraft:select item definitions that map those values to textures.
 */
public final class Cell4Textures {

    private Cell4Textures() {}

    public static final SelectItemModelProperty.Type<TextureProperty, String> TYPE =
            SelectItemModelProperty.Type.create(TextureProperty.MAP_CODEC, Codec.STRING);

    public static void register(RegisterSelectItemModelPropertyEvent event) {
        event.register(Identifier.fromNamespaceAndPath("cell4", "cell_texture"), TYPE);
    }

    public record TextureProperty() implements SelectItemModelProperty<String> {
        public static final TextureProperty INSTANCE = new TextureProperty();
        public static final MapCodec<TextureProperty> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override
        public @Nullable String get(ItemStack stack, @Nullable ClientLevel level,
                                    @Nullable LivingEntity owner, int seed,
                                    ItemDisplayContext displayContext) {
            if (stack.getItem() instanceof IInfinityCell cell) {
                String name = cell.getTextureName(stack);
                return name == null || name.isEmpty() ? "" : name;
            }
            return "";
        }

        @Override
        public Codec<String> valueCodec() {
            return Codec.STRING;
        }

        @Override
        public SelectItemModelProperty.Type<? extends SelectItemModelProperty<String>, String> type() {
            return TYPE;
        }
    }
}
