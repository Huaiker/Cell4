<div align="center">

# Cell⁴

**应用能源2 (AE2) 无限ME存储元件**

*一个元件, 无限资源, 随心筛选.*

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.1.0+-orange.svg)](https://files.minecraftforge.net/)
[![AE2](https://img.shields.io/badge/AE2-15.x-blue.svg)](https://github.com/Applied-Energistics/Applied-Energistics-2)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

---

## Cell⁴ 是什么?

Cell⁴是一个轻量级AE2附属模组, 为你的ME网络添加**无限元件** — 拥有无限提取能力的存储元件. 每个元件通过NBT绑定到指定的物品/标签/模组ID, 让你从单个元件中提取无限资源.

> **无限提取** — 随取随用, 永不耗尽.
> **幽灵存入** — 匹配的物品可以被存入元件但会静默消失, 保持ME网络整洁.
> **纯NBT绑定** — 无GUI, 无铁砧, 无额外指令, 仅需NBT数据.

## 元件类型

| 元件 | NBT键 | 描述 |
|------|-------|------|
| **无限物品元件** | `cell4item` | 绑定指定物品或流体 |
| **无限标签元件** | `cell4tag` + `cell4modid` | 绑定匹配标签和/或指定模组的所有物品 |
| **无限模组元件** | `cell4modid` | 绑定某模组的所有物品 |

所有元件类型均支持**物品, 流体, 气体**及任何AE2键类型 — 全部在一个元件中, 无需额外变体.

## 用法

所有元件通过**NBT数据**配置, 使用`/give`获取. 支持单值和列表两种格式.

### 单值

```
/give @p cell4:infinity_item_cell{cell4item:"minecraft:diamond"}
/give @p cell4:infinity_tag_cell{cell4tag:"minecraft:logs"}
/give @p cell4:infinity_modid_cell{cell4modid:"mekanism"}
```

### 多值

```
/give @p cell4:infinity_item_cell{cell4item:["minecraft:diamond","minecraft:oak_log","minecraft:water"]}
/give @p cell4:infinity_tag_cell{cell4tag:["minecraft:logs","forge:ingots/iron","forge:ores/diamond"]}
/give @p cell4:infinity_modid_cell{cell4modid:["mekanism","thermal","create"]}
```

### 标签元件搭配模组过滤

无限标签元件同时支持标签和模组ID过滤, 允许在单个元件中组合基于标签和基于模组的筛选:

```
/give @p cell4:infinity_tag_cell{cell4tag:"minecraft:logs",cell4modid:"mekanism"}
/give @p cell4:infinity_tag_cell{cell4tag:["minecraft:logs","forge:ingots/iron"],cell4modid:["mekanism","thermal"]}
```

### 黑名单

使用`cell4blacklist`排除特定物品, 被加入黑名单的物品不会被提取或接收. 黑名单支持三种类型:
- **物品** — 直接使用标识符 (如 `minecraft:birch_log`)
- **标签** — 以`#`为前缀 (如 `#minecraft:birch_logs`)
- **模组ID** — 以`@`为前缀 (如 `@mekanism`)

```
/give @p cell4:infinity_tag_cell{cell4tag:"minecraft:logs",cell4blacklist:"minecraft:birch_log"}
/give @p cell4:infinity_modid_cell{cell4modid:"mekanism",cell4blacklist:["mekanism:steel_ingot","#forge:ingots/steel","@thermal"]}
```

第二个示例从Mekanism元件中排除了:
- 特定物品 `mekanism:steel_ingot`
- 匹配标签 `forge:ingots/steel` 的所有物品
- 来自模组 `thermal` 的所有物品

黑名单物品会在元件tooltip中以✖标记显示.

## 构建

```bash
./gradlew build
```

输出JAR位于`build/libs/`.

## 依赖

| 依赖 | 版本 |
|-----|------|
| Minecraft | 1.20.1 |
| Forge | 47.1.0+ |
| Applied Energistics 2 | 15.0.0 - 15.x |

## 致谢

本模组参考了[ExtendedAE](https://github.com/GlodBlock/ExtendedAE)的`InfinityCell`设计, 包括无限提取概念和`getAsIntMax()`显示逻辑. 标签元件和模组元件为原创扩展.

## 许可证

[MIT](LICENSE)
