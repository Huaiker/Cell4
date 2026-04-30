<div align="center">

# Cell⁴

**Infinite ME Storage Cells for Applied Energistics 2**

*One cell. Infinite resources. Any filter you need.*

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.x-orange.svg)](https://neoforged.net/)
[![AE2](https://img.shields.io/badge/AE2-19.x-blue.svg)](https://github.com/Applied-Energistics/Applied-Energistics-2)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

---

## What is Cell⁴?

Cell⁴ is a lightweight AE2 addon that adds **Infinity Cells** to your ME network — storage cells with unlimited extraction capability. Each cell is bound to specific items, tags, or mod IDs via NBT data, allowing you to pull infinite resources from a single cell.

> **Infinite extraction** — pull as much as you want, it never runs out.
> **Ghost insertion** — matching items can be stored into the cell but are silently discarded, keeping your ME network clean.
> **Pure NBT binding** — no GUI, no anvil, no extra commands. Just NBT data.

## Cell Types

| Cell | NBT Key | Description |
|------|---------|-------------|
| **Infinity Item Cell** | `cell4item` | Bind specific items or fluids |
| **Infinity Tag Cell** | `cell4tag` | Bind all items matching a tag |
| **Infinity ModID Cell** | `cell4modid` | Bind all items from a mod |

All cell types support **items, fluids, gases** and any other AE2 key type — all in a single cell, no separate variants needed.

## Usage

All cells are configured via **custom data** using `/give`. Both single-value and list formats are supported.

### Single Value

```
/give @p cell4:infinity_item_cell[custom_data={cell4item:"minecraft:diamond"}]
/give @p cell4:infinity_tag_cell[custom_data={cell4tag:"minecraft:logs"}]
/give @p cell4:infinity_modid_cell[custom_data={cell4modid:"mekanism"}]
```

### Multiple Values

```
/give @p cell4:infinity_item_cell[custom_data={cell4item:["minecraft:diamond","minecraft:oak_log","minecraft:water"]}]
/give @p cell4:infinity_tag_cell[custom_data={cell4tag:["minecraft:logs","c:ingots/iron","c:ores/diamond"]}]
/give @p cell4:infinity_modid_cell[custom_data={cell4modid:["mekanism","thermal","create"]}]
```

### Blacklist

Use `cell4blacklist` to exclude specific items from any cell type. Blacklisted items will not be extracted or accepted.

```
/give @p cell4:infinity_tag_cell[custom_data={cell4tag:"minecraft:logs",cell4blacklist:"minecraft:birch_log"}]
/give @p cell4:infinity_modid_cell[custom_data={cell4modid:"mekanism",cell4blacklist:["mekanism:steel_ingot","mekanism:osmium_ingot"]}]
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
| Minecraft | 1.21.1 |
| NeoForge | 21.1.0+ |
| Applied Energistics 2 | 19.0.0 - 19.x |

## Attribution

This mod references the design of [ExtendedAE](https://github.com/GlodBlock/ExtendedAE)'s `InfinityCell` for the infinite extraction concept and `getAsIntMax()` display logic. The Tag Cell and ModID Cell are original extensions of this concept.

## License

[MIT](LICENSE)
