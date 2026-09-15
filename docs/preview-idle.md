# 界面眼神与捏脸主预览待机

生存背包、创造模式的玩家背包页和捏马主预览中，眼仁会跟随鼠标。背包同时保留原版头部跟随；捏马预览只移动眼仁，相机仍由拖动与自动聚焦控制。眼仁沿用各眼型的活动范围、鼻梁侧限制和眼眶裁剪。

`InventoryScreenMixin` 只包住这两种背包的角色绘制调用，`PonyCustom` 只包住主角色预览。`PonyGuiGaze` 根据实际模型、相机和投影矩阵把完整界面的鼠标坐标转换为眼前目标，兼容界面缩放、头部旋转和捏脸取景切换。眼神独立平滑，转到背面时回正；正侧面前后分界做短距离淡出，远处鼠标不会因此让正面的眼神回中。

界面眼神独立于世界注视及其开关，闭眼等表情会暂停跟随。绘制结束或发生异常时恢复临时眼位和鼠标上下文。

主预览使用独立的 `PonyPreviewAnimatable` 播放待机、眨眼、尾巴与 idle 表情；抽耳复用 `PonyIdleEars` 的左右轻幅变体。静态缩略图和世界玩家不受预览动画影响。

`PonyPreviewClock` 每个可见角色绘制帧采样一次 `System.nanoTime()`，转换为每秒 20 tick。游戏暂停或没有世界时仍能前进；重复采样不加速，时钟倒退不回放，切后台的长间隔最多推进 0.25 秒。换页、调色和窗口重新布局继续使用原实例；离开捏脸时同时释放实例与 renderer，重新进入从零开始。

GeckoLib 4.8.3 的 `GeoObjectRenderer` 不自动把 `getTick` 填入动画状态。捏脸专用的模型覆写 `handleAnimations`，显式传入 `DataTickets.TICK`，并把首次 manager 时间设为 0，避免默认初始化额外引入游戏 partial tick。`shouldPlayAnimsWhileGamePaused()` 仅在此预览实例返回 true。

预览相机使用静态 `PreviewGeometryBounds`，避免取景范围随待机呼吸变化。

主预览另从模型资源私有烘焙完整骨骼树，不直接动画化 GeckoLib 的世界共享缓存，也不复用取景／缩略图的静止树。共享缓存对象身份仅用于识别资源重载；重载后重新私有烘焙并把动画处理器绑定到新私有树，避免世界暂停时被预览覆盖姿势。

独立回归入口：`top.elysianherd.magicaland.client.model.PonyPreviewClockTest`，参数为仓库根目录。需要当前客户端类、GeckoLib 4.8.3 依赖和 `src/main/resources` 在 classpath；未包含游戏启动或整包构建步骤。

骨骼隔离回归：`top.elysianherd.magicaland.client.model.PonyPreviewModelIsolationTest`，同样传仓库根目录，离线验证真实骨骼树、动画处理器绑定和重载后的对象隔离。

鼠标投影回归入口：`top.elysianherd.magicaland.client.render.PonyGuiGazeTest`，无参数，覆盖上下左右、远端鼠标、GUI 缩放、旋转取景、镜像矩阵、正侧面连续性、暂停平滑、嵌套绘制清理及世界状态隔离。
