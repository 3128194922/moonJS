MoonJS
======

用途
----
MoonJS 为 Minecraft 1.20.1 Forge 提供可选的 KubeJS 月相注册、服务器月亮事件、客户端月亮贴图和血月染色功能。状态按维度由服务器管理并同步给客户端。

兼容性
------
- Minecraft 1.20.1
- Minecraft Forge 47.x（开发验证版本：47.4.6）
- KubeJS 2001.6.5 或更高版本（可选；未安装时基础 Forge 模组仍可加载）

注册自定义月相
--------------
仅在 startup_scripts 中注册。每个月相使用一张图片，不使用图集：

```js
MoonJS.registerPhase('blood', {
  texture: 'moonjs:textures/moon/blood.png',
  vanillaPhase: 0
})
```

贴图由资源包或其他 mod 提供。上例资源位置为 `assets/moonjs/textures/moon/blood.png`；MoonJS 不附带该图片。`vanillaPhase` 可选，范围为 0 到 7，用于原版月相亮度语义。

服务端脚本 API
--------------
下列接口在 server_scripts 中使用，`level` 是当前服务器维度：

```js
MoonJS.start(level, 'blood', {
  eventId: 'blood_moon',
  durationTicks: 6000,
  color: '#B51F2F',
  intensity: 0.45
})

MoonJS.setVanillaPhase(level, 4)
const state = MoonJS.getState(level)
MoonJS.clearVanillaPhase(level)
MoonJS.stop(level)
```

- `start(level, phaseId, options)` 启动或替换当前维度事件。`eventId`、`color`、`intensity` 可选；颜色接受 `#RRGGBB` 或 `#AARRGGBB`，强度范围为 0.0 到 1.0。
- `stop(level)` 结束事件并清除事件贴图和染色。
- `setVanillaPhase(level, phase)` 设置原版月相视觉覆盖，数值为 0 到 7。
- `clearVanillaPhase(level)` 清除该覆盖并恢复按世界时间计算的原版月相。
- `getState(level)` 返回当前维度状态对象，包括月相、事件、结束游戏刻、颜色、强度和修订号。

`durationTicks` 使用服务器游戏刻：`-1` 表示无限持续；正数表示从启动时的服务器游戏刻起持续相应刻数；`0` 表示到当前游戏刻即到期，并在服务器刻结束阶段清除。未提供时默认为 `-1`。这些功能只改变月相视觉状态，不修改世界真实 `dayTime`。

血月染色边界
------------
服务器同步的染色由客户端光照颜色、雾色和 VIGNETTE 覆盖层阶段的窗口填充呈现；它不取消原版覆盖层，后续 GUI 覆盖层的实际叠加顺序仍需在游戏内确认。不同维度是否能绘制月亮取决于其天空效果，染色状态仍可同步。

验证状态
--------
静态合同和 Gradle 合约任务用于检查命名、资源、依赖、状态选项和客户端缓存等代码约束。它们不等同于游戏内验证。真实 KubeJS 脚本执行、贴图加载、联机同步、画面效果及 Forge 客户端/专用服务器运行仍需在游戏环境验证；请以随附 `task-6-report.md` 中本次执行结果为准。
