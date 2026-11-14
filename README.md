<div align="center">

# Cell⁴

**Infinite ME Storage Cells for Applied Energistics 2**

</div>

---

Cell⁴ is an AE2 addon designed for modpack development. It adds **Infinity Cells** that can be bound to specific items, tags, or mod IDs and provide infinite extraction from the ME network. Inserted matching items are silently discarded, consuming no actual storage.

On 26.1.2, item entries support full data components, `custom_data`, and complete item compounds (including components registered by other mods).

## Cell Types

- **Infinity Item Cell** — bind specific items/fluids (supports full components)
- **Infinity Tag Cell** — bind all items matching tags and/or from specified mods
- **Infinity Mod ID Cell** — bind all items from a mod
- **Portable Infinity Item / Tag / Mod ID Cell** — portable variants using AE2's portable terminal UI, with configurable energy

## Cell Configurator

The **Cell Configurator** is an in-game tool for editing cell data. Right-click while holding it to open the GUI, then place a cell into the slot.

### List UI (UI1)

- Editable fields change based on the cell type placed in the slot.
- Inactive fields cannot be focused.
- Hold an item and click the Item / Blacklist section to add an entry; the item stays on the cursor.
- Drag items from JEI / REI onto the section to add them.
- Supports custom names, texture names, and directly editing portable energy fields.

### Workbench UI (UI2)

- Available for Infinity Item Cells only.
- 63 configuration slots per page plus the cell slot.
- Auto-saves after binding, replacing, or removing an entry.
- Clicking any empty slot appends in order; clicking an occupied slot replaces it.
- Free page navigation, including blank pages.
- Live preview with item icons and a vanilla tooltip panel.

### Commands

- `/cell4 gui` — open the Cell Configurator
- `/cell4 texfile` — open the custom texture folder

## Binding Fields

| Cell Type | Editable Fields |
|-----------|-----------------|
| Item Cell | `cell4item`, `cell4blacklist` |
| Tag Cell | `cell4tag`, `cell4modid`, `cell4blacklist` |
| Mod ID Cell | `cell4modid`, `cell4blacklist` |
| Portable variants | Same as the corresponding base cell, plus energy fields |

| Key | Target | Example |
|-----|--------|---------|
| `cell4item` | Specific items/fluids or full item entries | `"minecraft:diamond"` |
| `cell4tag` | Tag | `"minecraft:logs"` |
| `cell4modid` | Mod ID (Tag Cell also supports this) | `"mekanism"` |
| `cell4blacklist` | Exclude items/tags/mods (optional) | `"#minecraft:logs"` |
| `cell4texture` | Custom texture name | `"example"` |

## Item Entry Syntax (26.1.2)

Supported forms for `cell4item`:

- Plain ID: `minecraft:diamond`
- Components: `minecraft:diamond_sword[minecraft:custom_name={text:'Test'},minecraft:enchantments={'minecraft:sharpness':5}]`
- Custom data shorthand: `minecraft:stone{custom_data:{CustomTag:'test',CustomNumber:42}}`
- Full compound: `{id:'minecraft:golden_apple',count:1,components:{'minecraft:custom_name':{text:'Full'}}}`

26.1.2 component format notes:
- Enchantments use a bare map: `minecraft:enchantments={'minecraft:sharpness':5}` (the old `{levels:{...}}` form is normalized automatically).
- Attribute modifiers use a bare list: `minecraft:attribute_modifiers=[{type:'minecraft:attack_damage',id:'test:atk',amount:10,operation:'add_value',slot:'mainhand'}]`.
- Dyed color is an int: `minecraft:dyed_color=16711680`.
- Prefer `{text:'...'}` for text components; `'{"text":"..."}'` is treated as plain text.

## Command Examples

### Single Value

```
/give @p cell4:infinity_item_cell[custom_data={cell4item:"minecraft:diamond"}]
/give @p cell4:infinity_tag_cell[custom_data={cell4tag:"minecraft:logs"}]
/give @p cell4:infinity_modid_cell[custom_data={cell4modid:"mekanism"}]
```

### Multiple Values

```
/give @p cell4:infinity_item_cell[custom_data={cell4item:["minecraft:diamond","minecraft:oak_log"]}]
/give @p cell4:infinity_tag_cell[custom_data={cell4tag:["minecraft:logs","c:ingots/iron"]}]
/give @p cell4:infinity_modid_cell[custom_data={cell4modid:["mekanism","thermal"]}]
```

### Tag Cell with Mod ID Filter

The Infinity Tag Cell supports both `cell4tag` and `cell4modid`:

```
/give @p cell4:infinity_tag_cell[custom_data={cell4tag:"minecraft:logs",cell4modid:"mekanism"}]
/give @p cell4:infinity_tag_cell[custom_data={cell4tag:["minecraft:logs","c:ingots/iron"],cell4modid:["mekanism","thermal"]}]
```

### Item Entries with Components

```
/give @p cell4:infinity_item_cell[custom_data={cell4item:["minecraft:diamond_sword[minecraft:custom_name={text:'Test Sword'},minecraft:enchantments={'minecraft:sharpness':5},minecraft:attribute_modifiers=[{type:'minecraft:attack_damage',id:'test:atk',amount:10,operation:'add_value',slot:'mainhand'}]]","minecraft:stone{custom_data:{CustomTag:'test'}}","{id:'minecraft:golden_apple',count:1,components:{'minecraft:custom_name':{text:'Full'}}}"]}]
```

### Portable Cell with Energy

```
/give @p cell4:portable_infinity_item_cell[custom_data={cell4item:"minecraft:diamond",cell4power:5000.0d,cell4power_max:100000L,cell4power_cost:20L,cell4power_charge:8.0d}]
```

- `cell4power` — current energy
- `cell4power_max` — capacity (`0` = no power and no energy bar)
- `cell4power_cost` — energy per extracted item (`0` = energy bar shown, no power consumed)
- `cell4power_charge` — charge per tick

### Blacklist

Blacklist entries support three types:
- **Item** — plain identifier (e.g. `minecraft:birch_log`)
- **Tag** — prefixed with `#` (e.g. `#minecraft:birch_logs`)
- **Mod ID** — prefixed with `@` (e.g. `@mekanism`)

```
/give @p cell4:infinity_tag_cell[custom_data={cell4tag:"minecraft:logs",cell4blacklist:"minecraft:birch_log"}]
/give @p cell4:infinity_modid_cell[custom_data={cell4modid:"mekanism",cell4blacklist:["mekanism:steel_ingot","#c:ingots/steel","@thermal"]}]
```

Blacklisted items are shown in the cell tooltip with a ✖ marker.

## Custom Textures

Set a texture name with `cell4texture` (the `cell4textrue` typo is also accepted):

```
/give @p cell4:infinity_item_cell[custom_data={cell4item:"minecraft:diamond",cell4texture:"example"}]
```

Place `<name>.png` and optional `<name>.png.mcmeta` in `config/Cell4/.pack/assets/cell4/textures/item/`. Open the folder with the Configurator's folder button or `/cell4 texfile`. Press F3+T to reload after adding or editing textures.

## Mod Compatibility

- JEI / REI: the item panel moves aside when the preview appears.
- Drag items from JEI / REI onto the Item / Blacklist section or a UI2 slot.
- Components are preserved when dragging.
- EMI has no 26.1 version yet, so EMI support is not available on this build.

## Building

```bash
./gradlew build
```

The output JAR will be in `build/libs/`.

## Dependencies

| Dependency | Version |
|-----------|---------|
| Minecraft | 26.1.2 |
| NeoForge | 26.1.2.55-beta+ |
| Applied Energistics 2 | 26.1.8-alpha+ |

## Attribution

This mod references the design of [ExtendedAE](https://github.com/GlodBlock/ExtendedAE)'s `InfinityCell` for the infinite extraction concept and `getAsIntMax()` display logic. The Tag Cell and ModID Cell are original extensions of this concept.

## License

[MIT](LICENSE)
