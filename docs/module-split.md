# 双仓开发

## 职责与安装

Magical Land 提供小马模型、自定义、动画和外观同步；Gameplay 扩展三族能力与成就系统。两者分别维护仓库和版本。

| 仓库 | 模组 ID | 内容 |
| --- | --- | --- |
| [Magical-Land](https://github.com/Elysian-Herd-Studio/Magical-Land) | `magicaland` | 模型、捏脸、动画、预设、挑染、可爱标志、眼神、耳动、手持物视觉、光效音效、外观同步、公共 API |
| [Magical-Land-Gameplay](https://github.com/Elysian-Herd-Studio/Magical-Land-Gameplay) | `magicaland_gameplay` | 三族能力、能力轮盘、成就系统及后续玩法 |

Gameplay 通过[公共 API](appearance-api.md)调用主模组的显示功能。配置、网络缓存和渲染内部实现由主模组管理；能力权限、库存、伤害和交互由 Gameplay 服务端判定。角和翅膀的显示状态不代表能力权限。

- 客户端安装 Magical Land 及依赖：使用本地造型与编辑器。
- 客户端和服务器都安装 Magical Land 及依赖：同步玩家造型、动画与注视。
- 使用 Gameplay：客户端和服务器都安装兼容的主模组、Gameplay 及依赖。
- 玩家安装普通 JAR；`api` 产物仅供开发编译。由旧一体包升级时先移除旧包。

## 版本与源码

两个仓库各自维护 `src/main`、`src/client` 和 Gradle 配置。美术与 Blockbench 源文件位于主仓库 `Resources/`。

Gameplay 的 `appearance_version` 指定编译和开发运行所需的主模组版本，`appearance_compatibility` 指定安装兼容范围。API 破坏性变更需同步调整依赖与测试，具体接口版本见 [API 文档](appearance-api.md#依赖与兼容性)。

角色预设与可爱标志沿用已有格式。Gameplay 的协议和存档兼容规则见[开发说明](https://github.com/Elysian-Herd-Studio/Magical-Land-Gameplay/blob/1.20.1-Fabric/docs/repository-boundary.md)。

## 独立构建与联合开发

两仓使用 Gradle 9.4.1 wrapper、Loom 1.16.3，Java 输出仍为 17。请使用能运行这些开发工具的现代 JDK。以下是两个仓库的构建与联合调试步骤。

Magical Land 仓库：

```powershell
.\gradlew.bat build
.\gradlew.bat publishMavenJavaPublicationToLocalDevelopmentRepository
```

默认发布到 `build/repo`，坐标为 `top.elysianherd:magicaland:<mod_version>`。其中 `mod_version` 取自主仓库 `gradle.properties`。同时提供普通安装 JAR、`api`、`sources` 和 `api-sources` 产物；运行时使用完整主模组。

Gameplay 仓库：

```powershell
.\gradlew.bat build -PappearanceMavenRepo=C:/absolute/path/to/appearance/build/repo
.\gradlew.bat runClient -PappearanceMavenRepo=C:/absolute/path/to/appearance/build/repo
```

首次构建 Gameplay 前，先发布其 `appearance_version` 指定的主模组及 API。也可在主仓库执行 `publishToMavenLocal`，通过 Maven Local 提供依赖。

两个仓库可分开或在同一个编辑器工作区打开。各自 `runClient`/`runServer` 使用独立 `run/client`/`run/server`；Gameplay 通过发布物加载主模组，主仓库的运行配置单独启动 Magical Land。避免跨仓共享 `sourceSets`、复制美术资源或共用正在运行的存档。

## 验证与后续

独立 Java 回归测试需单独执行，Gradle 构建成功不代表它们已通过。协作测试与个人维护工具的存放规则见[文档维护](development/documentation.md)。

历史过程见[迁移记录](repository-migration.md)与[双仓构建记录](reports/2026-09-10-split-build.md)。

[独立同步服务](appearance-sync-future.md)仍是备选方案。现有同步问题与后续工作见[项目待办](../TODO.md)。
