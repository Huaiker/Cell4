<div align="center">

# Cell⁴

**应用能源2 (AE2) 无限ME存储元件**

</div>

---

Cell⁴是一个为整合包开发设计的AE2附属模组. 它添加了三种**无限元件**, 可通过NBT绑定到指定物品, 允许从ME网络中无限提取. 存入的匹配物品会被静默丢弃, 不消耗实际存储空间.

## 无限元件类型

- **无限物品元件** — 绑定指定物品/流体
- **无限标签元件** — 绑定匹配标签和/或指定模组的所有物品
- **无限模组元件** — 绑定某模组的所有物品

## 元件配置器

**元件配置器**是一个通过GUI编辑元件数据的工具. 手持元件配置器右键即可打开配置界面, 然后将元件放入槽位.

![元件配置器使用演示](AAA)

- 可编辑的字段根据放入的元件类型自动变化.
- 不可编辑的字段显示为灰色, 无法获得焦点.
- 多个值之间用逗号隔开 (如 `minecraft:diamond,minecraft:oak_log`).
- 在黑名单字段中, 标签需加 `#` 前缀 (如 `#c:ingots/steel`), 模组ID需加 `@` 前缀 (如 `@mekanism`).
- 按**Tab**键在可编辑字段间循环切换.
- 点击**保存**按钮应用更改.

| 元件类型 | 可编辑字段 |
|---------|-----------|
| 物品元件 | `cell4item`, `cell4blacklist` |
| 标签元件 | `cell4tag`, `cell4modid`, `cell4blacklist` |
| 模组元件 | `cell4modid`, `cell4blacklist` |

## NBT键

| 键 | 目标 | 示例 |
|----|------|------|
| `cell4item` | 指定物品/流体 | `"minecraft:diamond"` |
| `cell4tag` | 标签 | `"minecraft:logs"` |
| `cell4modid` | 模组ID (标签元件也支持) | `"mekanism"` |
| `cell4blacklist` | 排除物品/标签/模组 (可选) | 见下文 |

## 指令示例

### 单值

```
/give @p cell4:infinity_item_cell[custom_data={cell4item:"minecraft:diamond"}]
/give @p cell4:infinity_tag_cell[custom_data={cell4tag:"minecraft:logs"}]
/give @p cell4:infinity_modid_cell[custom_data={cell4modid:"mekanism"}]
```

### 多值

```
/give @p cell4:infinity_item_cell[custom_data={cell4item:["minecraft:diamond","minecraft:oak_log"]}]
/give @p cell4:infinity_tag_cell[custom_data={cell4tag:["minecraft:logs","c:ingots/iron"]}]
/give @p cell4:infinity_modid_cell[custom_data={cell4modid:["mekanism","thermal"]}]
```

### 标签元件 + 模组ID过滤

标签元件同时支持 `cell4tag` 和 `cell4modid`:

```
/give @p cell4:infinity_tag_cell[custom_data={cell4tag:"minecraft:logs",cell4modid:"mekanism"}]
/give @p cell4:infinity_tag_cell[custom_data={cell4tag:["minecraft:logs","c:ingots/iron"],cell4modid:["mekanism","thermal"]}]
```

### 黑名单

黑名单条目支持三种类型:
- **物品** — 普通标识符 (如 `minecraft:birch_log`)
- **标签** — 加 `#` 前缀 (如 `#minecraft:birch_logs`)
- **模组ID** — 加 `@` 前缀 (如 `@mekanism`)

```
/give @p cell4:infinity_tag_cell[custom_data={cell4tag:"minecraft:logs",cell4blacklist:"minecraft:birch_log"}]
/give @p cell4:infinity_modid_cell[custom_data={cell4modid:"mekanism",cell4blacklist:["mekanism:steel_ingot","#c:ingots/steel","@thermal"]}]
```

黑名单物品会在元件tooltip中以✖标记显示.

## 构建

```bash
./gradlew build
```

输出JAR位于`build/libs/`.

## 依赖

| 依赖 | 版本 |
|-----|------|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.169+ |
| Applied Energistics 2 | 19.0.0 - 19.x |

## 致谢

本模组参考了[ExtendedAE](https://github.com/GlodBlock/ExtendedAE)的`InfinityCell`设计, 包括无限提取概念和`getAsIntMax()`显示逻辑. 标签元件和模组元件为原创扩展.

## 许可证

[MIT](LICENSE)
