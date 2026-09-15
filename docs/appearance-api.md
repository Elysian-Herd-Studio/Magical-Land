# Magical Land API v1.6

Magical Land 0.3.6 通过 `top.elysianherd.magicaland.api` 和 `top.elysianherd.magicaland.api.client` 提供公共接口，当前配套 Gameplay 0.3.4。扩展通过这些接口对接主模组；配置、渲染、动画状态和同步缓存属于内部实现。

`ApiVersion` 位于主源码集，只依赖 Java 标准库，可在独立服务端安全查询。`.api.client` 下的接口仅供客户端使用：查询、注册和 `playTransformation` 必须在客户端线程执行，绘制接口必须在渲染线程执行。本 API 不授予玩法能力，也不提供可作为服务端判定依据的权威外观数据。

## 依赖与兼容性

API 版本与模组版本独立。`ApiVersion.requireCompatible(1, 0)` 要求已安装 API 的主版本为 1、次版本至少为 0，不满足时抛出明确异常；`isCompatible` 提供不抛异常的兼容性检查。API 次版本更新保持已有签名和语义，不兼容变更必须提升主版本。扩展的模组元数据也必须声明兼容的 Magical Land 版本；运行时检查无法解决 API 本身未安装的问题。

独立持物视觉上下文与第三人称悬浮入口需要 `ApiVersion.requireCompatible(1, 1)`；魔法活动注册需要 `ApiVersion.requireCompatible(1, 2)`；随实体运动的光焰入口需要 `ApiVersion.requireCompatible(1, 3)`；角翅覆盖与变身光尘需要 `ApiVersion.requireCompatible(1, 4)`；独立飞行表现需要 `ApiVersion.requireCompatible(1, 5)`；完整飞行姿态需要 `ApiVersion.requireCompatible(1, 6)`。已有方法签名继续兼容。

发布坐标为 `top.elysianherd:magicaland:0.3.6`。编译时依赖带 `api` 分类标识（classifier）的产物，运行时依赖完整主模组。扩展应与主模组使用相同的 Minecraft 1.20.1、Fabric 和映射版本。通过 Loom 引用重映射后的 API 产物，Loom 会将其中的 Minecraft 类型签名转换为扩展开发环境所用的命名空间。

```groovy
modCompileOnly "top.elysianherd:magicaland:0.3.6:api"
modRuntimeOnly "top.elysianherd:magicaland:0.3.6"
```

`api` JAR 仅用于编译。不要将它放入 `mods` 文件夹、通过 `include` 嵌套打包、合并打包（shade），或把其中的类复制进扩展。运行时由完整 Magical Land JAR 提供公共 API、实现及服务端同步功能。

API 产物包含 `top/elysianherd/magicaland/api/**` 下的全部类文件，包括嵌套枚举类。v1.6 具体包含 `ApiVersion`、`Registration`、`AppearanceSnapshot`、`Appearances`、`AppearanceOverrides`、`AppearanceOverrides$Visibility`、`AppearanceVisuals`、`ItemVisualContext`、`AnatomyOverride`、`FlightPose` 和 `FlightPose$Mode`。它不包含 `client/api` 内部桥接实现、`ModelConfig`、渲染内部类、网络类、Mixin 或资源。公共方法签名只使用 Java、Minecraft、游戏自带的 JOML 或 API 自身的类型。`api-sources` 分类产物包含相应的公共源码；完整源码产物供主模组开发使用。

## 只读外观查询

`Appearances.find(UUID)` 返回 `Optional<AppearanceSnapshot>`。查询本地玩家时读取已应用的外观；即使编辑器中存在未提交草稿，也仍然读取已应用版本。查询远程玩家时读取当前客户端最后获知的外观。没有可用模型时返回 `Optional.empty()`，不接受 null UUID。

每次返回的结果都是不可变快照，包含以下字段：

| 字段 | 含义 |
| --- | --- |
| `modelReplacementEnabled` | 本地设置已启用模型替换；查询远程玩家时，还要求相关远程同步可用。此字段不检查实体是否可见、是否存活，也不代表玩法权限。 |
| `hasHorn` / `hasWings` | 当前显示的角和翅膀；有有效覆盖时采用覆盖，否则采用已应用外观的选择。仍不代表服务端种族或能力权限。 |
| `magicColor` | RGB 魔法颜色，不包含透明度通道。 |

`Appearances.magicColor(UUID)` 返回相同的魔法颜色；没有已知外观时沿用现有默认值 `0xAA00FF`。查询结果不会暴露可变配置对象、预设名称或底层映射表。已经保存的快照不会随外观更新而变化；需要最新的同步或已应用数据时，应重新查询。

## 持物可见性、注视与魔法活动

`AppearanceOverrides.registerMainHandVisibility(ownerId, priority, provider)` 和 `registerGaze(ownerId, priority, provider)` 返回 `Registration` 注册句柄。`ownerId` 是带命名空间的小写字符串，例如 `magicaland_gameplay:remote_tool`；命名空间应标识发起注册的扩展。同一 owner 可以在任一覆盖类别下拥有多条注册。`provider` 回调接收正在渲染的玩家 UUID。

优先级整数越大，越先采用；优先级相同时，先注册且仍有效的条目优先。回调按需求值，不缓存返回结果：

* 可见性回调返回 `DEFAULT` 或 null 时，交给下一条注册决定；`HIDDEN` 隐藏正常主手持物的视觉显示；`VISIBLE` 阻止较低优先级的隐藏覆盖。没有回调作出决定时，执行原有持物逻辑。本 API 不修改背包、手部动画或副手状态。
* 注视目标覆盖原有头部和眼睛的目标。null、已移除实体或其他世界中的实体均交给下一条回调处理。没有有效目标时，执行原有注视逻辑。
* 回调抛出 `RuntimeException` 时，该条注册会被撤销并记录一次日志，然后继续尝试较低优先级条目。对应句柄将报告注册已失效。

`registration.close()` 只撤销该句柄对应的注册，重复调用不会产生额外影响。`unregisterOwner(ownerId)` 撤销该 owner 在全部覆盖类别中的注册，保留其他 owner 的注册。撤销后立即恢复到下一条适用回调或原有行为，并释放已撤销的回调引用。扩展可以通过 `ownerId()`、`priority()` 和 `isRegistered()` 检查自己的注册句柄。

每次断线都会清空所有覆盖，并使尚未关闭的句柄失效。需要跨会话使用的扩展必须在 `ClientPlayConnectionEvents.JOIN` 中重新注册，并在 `DISCONNECT` 或功能关闭时关闭自己的句柄。无论断线回调先后顺序如何，关闭已失效句柄都是安全的。回调应根据 UUID 获取当前状态，并在世界切换时释放扩展自己持有的世界和实体引用。能力结束时，可见性返回 `DEFAULT`、注视返回 null、魔法活动返回 false 即可恢复正常视觉表现，无需反复安装回调。

### 空手施法时的角部发光

`AppearanceOverrides.registerMagicActivity(ownerId, priority, Predicate<UUID> provider)` 返回独立的 `Registration`。回调返回 true 表示对应玩家正在施法；false 交给下一条注册，不会关闭普通持物或其他扩展请求的发光。优先级控制查询顺序，异常隔离与生命周期沿用上述规则。

世界中的角部发光由「主手非空、副手非空、扩展请求施法」任一条件触发，沿用已有渐亮和淡出。外观替换、角的显示、隐身、睡眠、骑乘等现有资格检查保持不变。注册不会改变物品、手部装备动画或 GUI 预览。`magicActive(UUID)` 仅查询扩展请求，不代表当前模型一定显示角部光效，也不包含普通持物状态。

```java
Registration magic = AppearanceOverrides.registerMagicActivity("my_addon:projection", 100,
        playerId -> hasActiveProjectionInCurrentWorld(playerId));
// 能力停用或断线时释放；跨会话重新注册。
magic.close();
```

Gameplay 在 JOIN 时注册远控实体注视与魔法活动两条回调，断线时关闭各自句柄。工具已实际转入独立携带槽，无需隐藏本体持物。空手投影也属于魔法活动；远控实体销毁、移出当前世界或不再有效后，回调返回 false。

### 独立飞行表现

`AppearanceOverrides.registerFlightActivity(ownerId, priority, Predicate<UUID> provider)` 让扩展启用已有飞行姿态、包身魔法、飞行声音和第一人称边缘光罩。回调返回 true 表示该玩家正在使用扩展的飞行能力；false 交给下一条注册，不会关闭原版飞行。角色仍需满足外观和状态条件，身体光效只用于有角、无翼的悬浮姿态，屏幕光罩只在第一人称显示。

`flightActive(UUID)` 只查询扩展请求，包括下方非空的完整飞行姿态。接口不修改速度、重力、碰撞、摔落伤害或原版飞行权限，也不会发出网络消息。扩展负责同步有效施法状态，在停止、换维度或断线后清理。独立飞行与角部施法是两个注册类别，需要点亮角时同时注册魔法活动。

### 完整飞行姿态（API 1.6）

`AppearanceOverrides.registerFlightPose(ownerId, priority, Function<UUID, FlightPose> provider)` 接收扩展提供的世界朝向与拍翼状态。返回 null 时尝试下一条注册；`flightPose(UUID)` 返回最终姿态或 null。非空姿态同时令 `flightActive(UUID)` 为 true，无需为同一次飞行另注册活动回调。句柄、优先级、异常隔离、owner 清理和断线重注册沿用上述规则。

`new FlightPose(x, y, z, w, mode, flapStrength, reboundProgress)` 保存不可变的四元数分量，构造时归一化；非有限数、近零四元数或 null 模式会被拒绝。`rotation()` 返回新的 JOML `Quaternionf`，修改它不会改变原姿态。拍翼强度截取到 0–2，0 表示展开滑翔；回弹进度截取到 0–1。

朝向将局部 **+Z 前方、+Y 上方** 转到 Minecraft 世界坐标。水平 yaw 0 对应单位四元数；原版角度换算为 `Ry(-yaw) * Rx(pitch)`，角度使用弧度，正 pitch 向下；局部横滚可再右乘 `Rz(roll)`。提供完整世界姿态，不要预乘模型自身的 180° 校准，也不要重复叠加 bodyYaw。主模组用此姿态转动视觉根节点，并把头部注视转回身体局部坐标。

模式包括 `NORMAL` 普通拍翼、`GLIDE` 滑翔、`BOOST` 滑翔加力、`BRAKE` 制动、`LANDING` 落地缓冲、`REBOUND` 团身回弹。制动与落地会加强拍翼；滑翔加力仍可通过强度控制拍翼。回弹由进度驱动团身、视觉翻转三圈和最后展翼，传入的四元数只含基础朝向，不应再包含这三圈。

```java
ApiVersion.requireCompatible(1, 6);
Map<UUID, FlightPose> poses = new HashMap<>(); // 扩展自己的当前世界状态
Registration flight = AppearanceOverrides.registerFlightPose("my_addon:flight", 100, poses::get);
// 在客户端更新姿态；yaw/pitch/roll 已换成弧度。
Quaternionf q = new Quaternionf().rotationY(-yaw).rotateX(pitch).rotateZ(roll);
poses.put(playerId, new FlightPose(q.x, q.y, q.z, q.w, FlightPose.Mode.GLIDE, 0, 0));
// 能力结束移除玩家；断线或功能停用时清空并释放句柄。
poses.remove(playerId);
poses.clear();
flight.close();
```

完整根节点姿态与程序拍翼只叠加于世界中的有效有翼小马；背包、捏脸预览和缩略图不叠加这一层旋转。绘制后恢复骨骼及变更标记，不改模型、UV、原动画资源或保存的外观。此接口不控制第一人称相机；相机、输入、飞行物理和多人姿态同步由扩展负责。没有扩展注册时沿用主模组的原有飞行表现。

### 角与翅膀的临时显示覆盖

`AppearanceOverrides.registerAnatomy(ownerId, priority, Function<UUID, AnatomyOverride> provider)` 返回独立注册句柄。回调返回 `new AnatomyOverride(hasHorn, hasWings)`，同时决定该玩家是否显示角和翅膀；两个 false 也是有效决定。返回 null 时交给下一条注册，最终回落到原预设。优先级、异常隔离、owner 清理和断线规则与其他覆盖一致。

覆盖作用于世界、背包和当前玩家的捏脸主预览，并用于飞行动画、角光、持物魔法及对应声音的显示判断。标题界面没有当前玩家时、款式示例缩略图及没有扩展注册时，仍沿用原显示规则。现有模型只有一套角和翅膀，直接复用其几何与配色；不改变发型、眼型或其他外观。

主模组只生成临时显示副本，原预设、编辑草稿、已应用配置和远端外观缓存均不改写。种族选择、允许外观混搭等设置由 Gameplay 管理；允许混搭时，Gameplay 的回调可返回 null。此接口不存储种族，不新增同步协议，也不改变飞行权限或服务端判定。回调应读取当前已同步状态，不要在回调中递归调用 `Appearances.find`。

## 视觉入口

`AppearanceVisuals.playTransformation(UUID)` 在客户端线程调用，为当前世界中已加载的指定玩家播放现有变身光尘。它使用已应用／远端已知外观的颜色，不读取捏脸草稿，不补发网络消息。未知玩家、缺少外观、模型替换关闭或不在当前世界时忽略；隐身、死亡、旁观、可见距离、粒子设置、每玩家冷却与全局数量限制沿用已有光尘规则。UUID 不可为 null。扩展应只在实际改族事件上调用，不在初次同步、进入范围或重连时重播。

`AppearanceVisuals.renderOrb(matrices, magicColor, ticks, seed)` 保留旧调用签名，使用静态光焰样式。

`AppearanceVisuals.renderFlame(matrices, source, magicColor, tickDelta)` 在调用方的世界渲染锚点绘制柔光内核和流动焰尾，尾部响应 source 的实际移动。矩阵使用当前相机的世界坐标系，调用方先平移到实体插值位置。入口接受非空 matrices/source 及有限的 0–1 tickDelta，忽略已移除或不在当前世界的实体；无效参数在绘制前拒绝。入口自行 push/pop，异常时也恢复调用方栈。它不改变实体、碰撞、交互范围或光团遮挡采样范围。

`renderGlowingItem(stack, mode, matrices, buffers, world, light, seed, magicColor)` 调用现有物品渲染及发光捕获逻辑。需要传入当前渲染上下文；底层预览渲染器支持无世界的场景时，`world` 可以为 null。调用方负责管理自己的变换栈。此入口不会重新定位物品或选择其他动画。

`renderFirstPerson(owner, camera, stack, buffers, renderCallback)` 临时向现有第一人称动画与持物渲染代码提供指定的 owner、camera 和 stack。调用顺序保持如下：

1. 打开临时持物视图。
2. 开始现有第一人称发光渲染阶段。
3. 同步执行回调。
4. 结束该阶段，完成原有缓冲刷新与光晕清理。
5. 恢复此前的持物视图。

即使回调或缓冲刷新抛出异常，也会恢复此前的视图。null 参数会在状态改变之前被拒绝。嵌套调用这个入口也会在修改外层渲染阶段之前被拒绝。不要在回调中开始或结束另一轮手部渲染，不要保留回调供稍后执行，也不要在手部渲染上下文之外使用这个入口。内部 `FirstPersonItemView` 仍属于实现细节，公共方法签名不暴露其作用域实现类型。

旧签名继续提供原 stack 引用，沿用装备已完成的表现与 owner 的使用状态。普通玩家的第一、第三人称渲染不需要创建上下文。

### 独立持物视觉状态

`new ItemVisualContext(source, stack, equipProgress, swingProgress, usingItem, sprinting)` 保存一次绘制需要的状态，供投影等独立视觉对象使用。构造与 `stack()` 读取均复制物品，外部修改输入或返回的 stack 不会改变此上下文。数值与布尔状态固定；`source()` 保留实际实体引用，用于读取其当前插值位置、朝向和 age，并非实体世界状态的深拷贝。

| 参数 | 语义 |
| --- | --- |
| `source` | 独立持物/相机实体，UUID 标识动画缓存，位置、朝向与 age 驱动悬浮惯性。不要用玩家本体代替真实投影实体。 |
| `equipProgress` | 0 表示收起，1 表示完全显示。对接原版 lowered equip 参数时传 `1 - vanillaEquip`。必须有限且位于 0–1。 |
| `swingProgress` | 当前挥动物品动作进度，必须有限且位于 0–1；大于 0 时收束悬浮并停止产生新尾迹。挥动姿态本身由调用方绘制。 |
| `usingItem` | 当前视觉对象自己的使用/执行动作状态；为 true 时收束悬浮并停止产生新尾迹，不读取本体的使用状态。 |
| `sprinting` | 当前视觉对象自己的冲刺状态，与实际位移速度共同决定现有尾迹表现。 |

`renderFirstPerson(owner, context, buffers, renderCallback)` 使用 `context.source()` 作为视觉相机，使用上下文物品、装备进度与动作状态，不读取玩家本体的装备动画缓存。它沿用上述阶段顺序、嵌套拒绝和异常恢复规则。回调应同步调用原版第一人称物品绘制，并传入同一帧的物品、挥动与装备参数。此上下文保留调用方的原版装备升降，仅叠加缩放与受限悬浮惯性，不再添加本体装备的侧边飞入路径或该路径的弹簧驱动力。悬浮位置比普通持物略靠前、略向中央；沿用现有光效样式。旧签名与普通外观的持物位置不变。

```java
var visual = new ItemVisualContext(tool, stack, 1 - vanillaEquip, swing, actionActive, sprinting);
AppearanceVisuals.renderFirstPerson(owner, visual, buffers, () -> renderVanillaHand());
```

`renderLevitatingItem(owner, context, mode, matrices, buffers, world, light, seed, magicColor, tickDelta)` 在世界渲染阶段绘制第三人称独立悬浮持物。调用方先设置实体插值位置、yaw/pitch 和挥动/工具动作姿态，再调用此入口。矩阵必须处于当前游戏相机的世界渲染坐标系；入口从当前物品锚点复用现有世界悬浮惯性、物品光效与尾迹，不重新设置物品动作。`owner` 提供物品模型所需的玩家信息；`source` 提供独立位置、朝向、动画时钟与状态。

`mode` 只接受 `THIRD_PERSON_LEFT_HAND` 或 `THIRD_PERSON_RIGHT_HAND`。`world` 必须同时是 owner 和 source 所在世界，`tickDelta` 必须有限且位于 0–1；这些约束与所有非空参数在修改矩阵前检查。入口自行 push/pop，绘制抛异常也会恢复调用方矩阵。物品不可见时不绘制几何；正常世界渲染阶段之外不累积悬浮尾迹。

独立视觉使用专用缓存，按 source UUID、持物侧与第一/第三人称区分，即使 UUID 与 owner 相同也不共享普通本体缓存。物品身份变化、间断渲染返回、世界/视角切换及断线沿用现有重置和清理规则。所有这些入口只改变显示，不移动实体、不改变物品使用状态，不控制碰撞、射线、命中或服务端判定。

## 验证方式与范围

API 回归源码与入口由作者在本地维护，需要复跑时先取得对应版本的测试。环境需要可用的 Node 和 JDK 17 或更高版本。这是一组针对源码契约的独立测试，不会构建整个项目。测试使用轻量 Minecraft/Fabric 测试替身编译实际 API 与内部桥接源码，检查以下内容：

- 外观快照：快照数据隔离、有效角翅覆盖、本地／远端与预览草稿互不改写。
- 回调与注册：同优先级顺序、逐级回退、owner 与句柄清理、断线会话、无效注视目标和回调异常。
- 视觉状态：刷新异常、新旧第一人称重载的嵌套调用保护与状态恢复、上下文物品复制，以及第三人称入口的参数约束和矩阵恢复。
- API 打包边界：仅使用公共 API JAR 与 Minecraft 测试替身编译外部调用示例，并检查公共字节码签名是否泄漏内部类型。
- 变身入口：当前世界玩家解析、已应用颜色，以及未知玩家、缺少外观、关闭替换与断线时不触发；实际粒子绘制沿用既有测试。

`tests/api/ModelDisplayCopyTest.java` 另以真实 `ModelConfig` 和项目依赖运行，检查全部字段保留、三组挑染数组独立复制及原配置不变。后续新增可变字段时，需同步补充复制规则。此项与上述使用配置替身的 API 测试分开执行。

API 测试还在轻量实体替身上执行实际 `MagicEquip` 与装备包络，覆盖空手远控点亮、回收淡出、正常持物保留、物品转入远控时持续发光、隐身和无角资格、断线清理。`tests/render/LevitationVisualIsolationTest.java` 使用真实 `ItemLevitation` 的两组缓存，验证本体与投影的独立状态、时钟、视角与清理。`LevitationMotionTest` 和 `MagicEquipMotionTest` 继续覆盖惯性与装备动画的纯计算规则。这些测试不提供游戏内画面验收。

如果同级目录存在 Gameplay 仓库，测试还会扫描其 Java 源码，检查是否越过 API 边界引用主模组内部实现。Gameplay 位于其他位置时，可将 `MAGICALAND_GAMEPLAY_REPO` 设置为该仓库路径以启用扫描。这些检查不能替代 Loom 构建、打包后 JAR 的启动检查或游戏内视觉验证。
