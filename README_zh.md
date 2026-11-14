<div align="center">

# Cell⁴

**应用能源2 (AE2) 无限ME存储元件**

</div>

---

Cell⁴ 是一个为整合包开发设计的 AE2 附属模组。它添加了**无限元件**，可绑定指定物品、标签或模组 ID，从 ME 网络中无限提取。存入的匹配物品会被静默丢弃，不消耗实际存储空间。

元件也支持完整的原版物品数据：在 1.21.1 中，可以绑定带数据组件、`custom_data` 或完整物品 compound 的条目（包括其他模组注册的自定义组件）。

## 元件类型

- **无限物品元件** — 绑定指定物品/流体（支持完整 NBT/组件）
- **无限标签元件** — 绑定匹配标签和/或指定模组的所有物品
- **无限模组元件** — 绑定某模组的所有物品
- **便携式无限物品 / 标签 / 模组元件** — 复用 AE2 原版便携终端界面，可配置能量

## 元件配置器

**元件配置器**是一个通过 GUI 编辑元件数据的工具。手持元件配置器右键即可打开配置界面，然后将元件放入槽位。

### 列表界面（UI1）

- 可编辑的字段根据放入的元件类型自动变化。
- 不可编辑的字段无法获得焦点。
- 拾起物品后点击 Item / 黑名单栏即可添加条目，物品会留在光标上。
- 也可以从 JEI / REI / EMI 拖动物品到对应栏添加。
- 支持自定义名称、材质名称，以及直接编辑便携式元件的能量字段。

### 元件工作台（UI2）

- 仅无限物品元件可用。
- 每页 63 个配置槽，另有右上角元件槽。
- 绑定、替换、移除后自动保存。
- 点击任意空白格按顺序追加；点击已有物品格替换。
- 支持随时翻页，可停留在空白页。
- 实时预览：物品图标与 Tooltip。

### 指令

- `/cell4 gui` — 打开元件配置器
- `/cell4 texfile` — 打开自定义材质文件夹

## 绑定字段

| 元件类型 | 可编辑字段 |
|---------|-----------|
| 物品元件 | `cell4item`, `cell4blacklist` |
| 标签元件 | `cell4tag`, `cell4modid`, `cell4blacklist` |
| 模组元件 | `cell4modid`, `cell4blacklist` |
| 便携式变体 | 对应基础元件的字段 + 能量字段 |

| 键 | 目标 | 示例 |
|----|------|------|
| `cell4item` | 指定物品/流体或完整物品条目 | `"minecraft:diamond"` |
| `cell4tag` | 标签 | `"minecraft:logs"` |
| `cell4modid` | 模组ID（标签元件也支持） | `"mekanism"` |
| `cell4blacklist` | 排除物品/标签/模组（可选） | `"#minecraft:logs"` |
| `cell4texture` | 自定义材质名 | `"example"` |

## 物品条目语法（1.21.1）

`cell4item` 支持以下写法：

- 纯 ID：`minecraft:diamond`
- 组件：`minecraft:diamond_sword[minecraft:enchantments={levels:{'minecraft:sharpness':5}},minecraft:custom_name={text:'Test'}]`
- `custom_data` 简写：`minecraft:stone{custom_data:{CustomTag:'test',CustomNumber:42}}`
- 完整 compound：`{id:'minecraft:golden_apple',count:1,components:{'minecraft:custom_name':{text:'Full'}}}`

其他模组注册的自定义组件也可使用（只要已注册且带有可序列化的 codec）。文本组件支持 `{text:'...'}`，解析器会自动转换为 1.21.1 需要的形式。

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

标签元件同时支持 `cell4tag` 和 `cell4modid`：

```
/give @p cell4:infinity_tag_cell[custom_data={cell4tag:"minecraft:logs",cell4modid:"mekanism"}]
/give @p cell4:infinity_tag_cell[custom_data={cell4tag:["minecraft:logs","c:ingots/iron"],cell4modid:["mekanism","thermal"]}]
```

### 带组件的物品条目

```
/give @p cell4:infinity_item_cell[custom_data={cell4item:["minecraft:diamond_sword[minecraft:custom_name={text:'Test Sword'},minecraft:enchantments={levels:{'minecraft:sharpness':5}}]","minecraft:stone{custom_data:{CustomTag:'test'}}","{id:'minecraft:golden_apple',count:1,components:{'minecraft:custom_name':{text:'Full'}}}"]}]
```

### 带能量的便携式元件

```
/give @p cell4:portable_infinity_item_cell[custom_data={cell4item:"minecraft:diamond",cell4power:5000.0d,cell4power_max:100000L,cell4power_cost:20L,cell4power_charge:8.0d}]
```

- `cell4power` — 当前能量
- `cell4power_max` — 容量（`0` = 不耗电且不显示能量条）
- `cell4power_cost` — 每件消耗（`0` = 显示能量条但不耗电）
- `cell4power_charge` — 每 tick 充能

### 黑名单

黑名单条目支持三种类型：
- **物品** — 普通标识符（如 `minecraft:birch_log`）
- **标签** — 加 `#` 前缀（如 `#minecraft:birch_logs`）
- **模组ID** — 加 `@` 前缀（如 `@mekanism`）

```
/give @p cell4:infinity_tag_cell[custom_data={cell4tag:"minecraft:logs",cell4blacklist:"minecraft:birch_log"}]
/give @p cell4:infinity_modid_cell[custom_data={cell4modid:"mekanism",cell4blacklist:["mekanism:steel_ingot","#c:ingots/steel","@thermal"]}]
```

黑名单物品会在元件 tooltip 中以 ✖ 标记显示。

## 自定义材质

使用 `cell4texture` 指定材质名（兼容拼写 `cell4textrue`）：

```
/give @p cell4:infinity_item_cell[custom_data={cell4item:"minecraft:diamond",cell4texture:"example"}]
```

把 `<名称>.png` 和可选的 `<名称>.png.mcmeta` 放在 `config/Cell4/.pack/assets/cell4/textures/item/`。可通过配置器的文件夹按钮或 `/cell4 texfile` 打开该目录。添加或修改材质后按 F3+T 重载资源包。

## 模组兼容

- JEI / REI / EMI：预览出现时物品面板会自动让位。
- 可从 JEI / REI / EMI 拖动物品到 Item / 黑名单栏或 UI2 格子。
- 拖拽时保留物品的 NBT/组件。

## 构建

```bash
./gradlew build
```

输出 JAR 位于 `build/libs/`。

## 依赖

| 依赖 | 版本 |
|-----|------|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.169+ |
| Applied Energistics 2 | 19.2.17+ |

## 致谢

本模组参考了 [ExtendedAE](https://github.com/GlodBlock/ExtendedAE) 的 `InfinityCell` 设计，包括无限提取概念和 `getAsIntMax()` 显示逻辑。标签元件和模组元件为原创扩展。

## 许可证

[MIT](LICENSE)
