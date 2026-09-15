# 发型、尾巴与眼型编号

2026-08-15 完成角色缩写到数字编号的迁移；2026-09-07 加入 AJ 独立前发，并将丸子头后发移至 08。

## 1. 用途

数字编号用于 Java、骨骼名、动画绑定和玩家存档，便于加入原创款式。界面显示“发型08”等通用名称，旧角色缩写 `TS/RD/RR/PP/AJ/FS` 保留为兼容映射。

## 2. 命名规则

- **按部位编号**：每个部位从 `"01"` 开始，唯一键为 `(part, id)`。暂缓款式保留空号，后续套装沿用各自编号。
- **骨骼名**：前缀为 `"Style" + id`，如 `Style01FrontMane`、`Style03Tail02`；其中 `03` 是款式，`Tail02` 表示尾巴第 2 节。
- **RD/AJ 独立前发**：RD 保留 `Style02FrontMane`；AJ 新增 `Style05FrontMane`，保持原共用造型，仅轻微回收四处发束末端，沿用鬃毛贴图。FS 前发顺延为 `06`，使前六款的前发、后发、尾巴编号重新对应。
- **原创款式**：DeepSidePart=`Style07FrontMane*`、Undercut=`Style08FrontMane`、Bun=`Style08BackMane`、ShortSpikyTail=`Style07Tail*`。后发 `07` 和尾巴 `08` 暂缓，其他部件可独立混搭。
- **显示实际 id**：后发列表为 `01–06、08`，第七项丸子头显示“发型08”。列表位置与款式编号分别处理。

### 旧 6 个风格的完整对照表

| 旧缩写 | 前鬃毛 id | 后鬃毛 id | 尾巴 id | 眼睛 id |
|---|---|---|---|---|
| TS | 01 | 01 | 01 | 01 |
| RD | 02 | 02 | 02 | — |
| RR | 03 | 03 | 03 | 02 |
| PP | 04 | 04 | 04 | — |
| AJ | 05（独立轻变体） | 05 | 05 | — |
| FS | 06 | 06 | 06 | 03 |

## 3. 注册表

款式定义集中在 `src/client/java/top/elysianherd/magicaland/client/config/style/`：

- `PonyStylePart.java`：枚举 `FRONT_MANE/BACK_MANE/TAIL/EYE`，携带骨骼名后缀 + 展示层通用名 lang key
- `PonyStyleDefinition.java`：单个部位款式的不可变数据（part、部位内 id）
- `PonyStyleRegistry.java`：按部位保存候选集和旧缩写映射；核心方法 `stylesFor(part)`、`byId(part, id)`、`legacyCodeToId(part, legacyCode)`、`isValidStyleId(part, id)`、`displayOrdinal(part, id)`、`DEFAULT_ID`

新套装从 `09` 起编号，眼型下一个编号为 `04`；后发 `07` 和尾巴 `08` 保留待补。

## 4. 迁移涉及的文件

| 文件 | 改动内容 |
|---|---|
| `ModelConfig.java` | 删除 `MANE_STYLES`/`EYE_STYLES` 两个 `Set`；4 个风格字段默认值从 `"TS"` 改为 `PonyStyleRegistry.DEFAULT_ID`；`sanitize()` 里的校验逻辑改用按部位查注册表，并内联了 legacy 缩写→新 id 的兼容翻译（见第 5 节） |
| `ModelManager.java` | `createModel()` 里 3 处 `"TS"` 字面量改用 `PonyStyleRegistry.DEFAULT_ID` |
| `PonyCustomPageHelper.java` | `createStyleButton` 从"字符串数组 + 拼接文字再反解析"改造成"按部位查注册表 + `CustomButton` 的 label/value 双段构造器"，按钮上显示"通用名+实际 id"（如"发型08"），不再显示角色名；删除了不再使用的 `getNextStyle` |
| `ManePage.java` / `FacePage.java` | 删除硬编码的 `FRONT_MANE_STYLES`/`BACK_MANE_STYLES`/`TAIL_STYLES`/`EYE_STYLES` 数组，改为在调用 `createStyleButton` 时传 `PonyStylePart` 常量，候选集完全由注册表按部位提供 |
| `PonyRenderer.java` | 所有发型/尾巴选择统一使用 `"Style" + 部位内id + part.boneSuffix`；眼睛使用 TS=`01`、RR=`02`、FS=`03`；删除了 Bun 强制隐藏等硬编码特例 |
| `src/main/resources/assets/magicaland/lang/en_us.json` / `zh_cn.json` | 各新增 3 条 key：`text.magicaland.style.generic.mane`（发型/Hairstyle）、`.tail`（尾巴/Tail）、`.eye`（眼睛/Eyes） |
| `src/main/resources/assets/magicaland/geo/mare_geo.json` / `Resources/Models/Mare.geo.json` | AJ 新增 `Style05FrontMane`；FS、DeepSidePart、Undercut 的前发分别使用 `Style06FrontMane*`、`Style07FrontMane*`、`Style08FrontMane`；原 07 丸子头后发改为 08，新 07 草案撤出，尾巴不变 |
| `Resources/BlockbenchProjects/Mare.bbmodel` | 同步迁移所有 group 和动画 animator 名称，保留 UUID、元素、贴图及关键帧，避免重新打开或导出时恢复旧命名 |
| `src/main/resources/assets/magicaland/animations/mare_animation.json` **以及** `Resources/Animations/mare_animation.json` | 动画绑定同步使用部位内编号，移除旧 Bun 在 13 段动作中的 scale=0 隐藏轨道；Bun 改为 `Style08BackMane`，不改其他动作轨道 |

## 5. 存档兼容策略

`ModelConfig.sanitize()` 中的 `sanitizeStyle(value, part)` 先用 `legacyCodeToId` 翻译旧缩写，再用 `isValidStyleId` 校验数字编号。两者都不匹配时回退默认值。

旧缩写按部位映射：AJ 前发/后发/尾巴为 `05`；FS 前发/后发/尾巴为 `06`，眼型为 `03`。

**2026-09-07 开发期调整**：旧数字配置没有版本号，需要手动重选。原丸子头后发由 `07` 改选 `08`；留在 `07` 的配置会回退 `01`。原前发依次改为 `05→06`、`06→07`、`07→08`；`02` 仍是 RD，AJ 改选新 `05`。数字含义有重叠，不能自动判断，也不能在每次 `sanitize()` 时递增。正式发布前需冻结编号或加入明确的配置版本。

旧缩写兼容持续保留。本地加载、周期规范化和远端同步都经过 `ModelConfig.sanitize()`；两字母缩写与两位数字无歧义，重复读取安全，保存时写入数字编号。`maneColorLinkVersion` 只用于调色联动，不是款式版本。

早期全局数字编号也没有版本字段。使用过该开发版本的存档，需手动重选受影响的前发和眼型。

## 6. 历史检查与待办

2026-08-15 重构时未运行 Fabric Loom 编译：当时环境缺少 Gradle wrapper 脚本、JAR 和全局 Gradle。已完成 Java 引用与方法签名检查、几何/动画/语言 JSON 语法校验、骨骼完整名称替换及数量核对；睡觉和蹲下的旧眼型骨骼引用也已替换。

当时仅等价重命名 `AJHat`/`FSSmile` 为 `Style05Hat`/`Style03Smile`，未确认其行为或接入额外渲染。角色原稿 `Resources/Textures/{TS,RD,RR,PP,AJ,FS}.png` 作为 Blockbench 参考保留原名。

2026-09-07 的 AJ 插入与 07/08 调整记录未包含游戏构建或启动验证。

## 7. 07 / 08 后发调整与尾巴实验

07 分层短后发草案因块面过厚而搁置，已从 Blockbench 工程、两份几何和注册表撤出，编号保留。08 沿用原丸子头的形状、UV、pivot 与 UUID，调整编号并解除隐藏轨道。

08 尾巴草案因过于球形、缺少盘团发束质感而搁置，实验保存在本地 `Resources/PolishLab_20260907/15_hair_sets_07_08/02_trial_tail08/`。两项制作计划见 `TODO.md`。

前发/后发/尾巴的自动色阶、手动色阶锁及部位颜色联动见 [鬃毛与尾巴调色](mane-palette.md)。
