<div align="center">

# Cell⁴

**Infinite ME Storage Cells for Applied Energistics 2**

</div>

---

Cell⁴ is an AE2 addon designed for modpack development. It adds three types of **Infinity Cells** that can be bound to specific items via NBT, allowing infinite extraction from the ME network. Inserted matching items are silently discarded, consuming no actual storage.

## Infinity Cell Types

- **Infinity Item Cell** — bind specific items/fluids
- **Infinity Tag Cell** — bind all items matching tags and/or from specified mods
- **Infinity ModID Cell** — bind all items from a mod

## Cell Configurator

The **Cell Configurator** is an in-game tool for editing cell data visually. Right-click while holding it to open the configuration GUI, then place a cell into the slot.

![Cell Configurator Usage](exampleConfiguratorUsage1)
![Cell Configurator Usage](exampleConfiguratorUsage2)
![Cell Configurator Usage](exampleConfiguratorUsage3)

- Editable fields change based on the cell type placed in the slot.
- Inactive fields are shown in gray and cannot be edited.
- Separate multiple values with commas (e.g. `minecraft:diamond,minecraft:oak_log`).
- In the blacklist field, prefix tags with `#` (e.g. `#forge:ingots/steel`) and mod IDs with `@` (e.g. `@mekanism`).
- Press **Tab** to cycle between editable fields.
- Click **Save** to apply changes.

| Cell Type | Editable Fields |
|-----------|----------------|
| Item Cell | `cell4item`, `cell4blacklist` |
| Tag Cell | `cell4tag`, `cell4modid`, `cell4blacklist` |
| ModID Cell | `cell4modid`, `cell4blacklist` |

## NBT Keys

| Key | Target | Example |
|-----|--------|---------|
| `cell4item` | Specific items/fluids | `"minecraft:diamond"` |
| `cell4tag` | Tag | `"minecraft:logs"` |
| `cell4modid` | Mod ID (Tag Cell also supports this) | `"mekanism"` |
| `cell4blacklist` | Exclude items/tags/mods (optional) | See below |

## Command Examples

### Single Value

```
/give @p cell4:infinity_item_cell{cell4item:"minecraft:diamond"}
/give @p cell4:infinity_tag_cell{cell4tag:"minecraft:logs"}
/give @p cell4:infinity_modid_cell{cell4modid:"mekanism"}
```

### Multiple Values

```
/give @p cell4:infinity_item_cell{cell4item:["minecraft:diamond","minecraft:oak_log"]}
/give @p cell4:infinity_tag_cell{cell4tag:["minecraft:logs","forge:ingots/iron"]}
/give @p cell4:infinity_modid_cell{cell4modid:["mekanism","thermal"]}
```

### Tag Cell with Mod ID Filter

The Infinity Tag Cell supports both `cell4tag` and `cell4modid` simultaneously:

```
/give @p cell4:infinity_tag_cell{cell4tag:"minecraft:logs",cell4modid:"mekanism"}
/give @p cell4:infinity_tag_cell{cell4tag:["minecraft:logs","forge:ingots/iron"],cell4modid:["mekanism","thermal"]}
```

### Blacklist

Blacklist entries support three types:
- **Item** — plain identifier (e.g. `minecraft:birch_log`)
- **Tag** — prefixed with `#` (e.g. `#minecraft:birch_logs`)
- **Mod ID** — prefixed with `@` (e.g. `@mekanism`)

```
/give @p cell4:infinity_tag_cell{cell4tag:"minecraft:logs",cell4blacklist:"minecraft:birch_log"}
/give @p cell4:infinity_modid_cell{cell4modid:"mekanism",cell4blacklist:["mekanism:steel_ingot","#forge:ingots/steel","@thermal"]}
```

Blacklisted items are shown in the cell tooltip with a ✖ marker.

## Building

```bash
./gradlew build
```

The output JAR will be in `build/libs/`.

## Dependencies

| Dependency | Version |
|-----------|---------|
| Minecraft | 1.20.1 |
| Forge | 47.1.0+ |
| Applied Energistics 2 | 15.0.0 - 15.x |

## Attribution

This mod references the design of [ExtendedAE](https://github.com/GlodBlock/ExtendedAE)'s `InfinityCell` for the infinite extraction concept and `getAsIntMax()` display logic. The Tag Cell and ModID Cell are original extensions of this concept.

## License

[MIT](LICENSE)
