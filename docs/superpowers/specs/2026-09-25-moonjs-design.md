# moonJS 月相与月亮事件设计

## 目标

为 Minecraft 1.20.1 Forge 附属模组 `moonjs` 提供可选的 KubeJS 绑定，使 KubeJS 能够注册自定义月相、切换原版月相、启动和停止月亮事件，并同步客户端渲染单张月相贴图与全世界染色效果。

## 已确认的边界

- 目标项目：`E:\Server_mod\moonJS`。
- 参考项目：`E:\Server_mod\其他项目\KubeJS-2001`，只读，不修改。
- Minecraft：1.20.1。
- Forge：47.4.6。
- KubeJS：按参考项目的 2001.6.5 API 实现，作为可选运行时依赖。
- 状态模型：服务器按维度维护、持久化，并同步客户端。
- 自定义贴图：每个自定义月相使用一张独立图片，不使用 4×2 图集。
- 包路径不得包含 `example`。
- 不引入参考项目之外的第三方运行时依赖。

## 项目命名与依赖

项目使用以下标识：

- Mod ID：`moonjs`
- Java 包：`com.moonjs`
- 显示名称：`MoonJS`

模板中的 `com.example.examplemod`、示例入口、示例配置和示例注册内容全部替换为 moonJS 实现。

KubeJS 与 Rhino 只作为编译期依赖使用，不打包进 moonJS。`mods.toml` 中声明 KubeJS 为非必需依赖，并要求在 KubeJS 之后加载。moonJS 在未安装 KubeJS 时仍必须能够加载 Forge 主模组和客户端渲染部分。

KubeJS 插件类只通过 `src/main/resources/kubejs.plugins.txt` 暴露给 KubeJS，主模组入口不得直接引用 KubeJS 类型，避免可选依赖缺失时触发类加载失败。

## 服务端状态模型

每个 `ServerLevel` 使用自己的 `SavedData` 保存月亮状态。数据名称固定为 moonJS 使用的命名空间名称，并通过该维度的 `DataStorage` 获取，不能把所有维度状态错误地存进单一世界对象。

状态包含：

- `phaseId`：当前注册月相 ID，可为空。
- `vanillaPhase`：原版月相索引，可为空，范围为 0 到 7。
- `texture`：自定义单张贴图的 `ResourceLocation` 字符串，可为空。
- `eventId`：当前月亮事件 ID，可为空。
- `eventEndGameTime`：事件结束时刻；`-1` 表示无限持续。
- `color`：RGB 颜色值。
- `intensity`：染色强度，范围为 0.0 到 1.0。
- `revision`：状态版本号，用于客户端更新和诊断。

状态变化后立即标记 `SavedData` 为 dirty，并向该维度当前玩家同步。服务端 Tick 检查有限时事件：当当前游戏刻达到结束刻时，清除事件状态、恢复默认月相渲染策略并广播新状态。

所有时间语义使用服务器游戏刻，不使用现实时间。服务器重启后，结束时刻仍与该维度的游戏时间一致。

## 月相注册与 KubeJS API

KubeJS 插件通过 `KubeJSPlugin.registerBindings(BindingsEvent)` 注册全局 `MoonJS` 对象，并在 `kubejs.plugins.txt` 中列出插件类。

### 注册自定义月相

启动脚本示例：

```js
MoonJS.registerPhase('blood', {
    texture: 'moonjs:textures/moon/blood.png',
    vanillaPhase: 0
})
```

注册项包含：

- `id`：非空、小写、稳定的月相 ID。
- `texture`：单张贴图资源路径，必须能解析为 `ResourceLocation`。
- `vanillaPhase`：用于月亮亮度和原版月相语义的基础索引，默认 0，范围 0 到 7。

注册失败必须提供明确的 KubeJS 异常，包括重复 ID、非法资源路径、非法月相索引和缺少贴图路径。

### 控制月亮状态

服务端脚本使用以下接口：

```js
MoonJS.start(level, 'blood', {
    eventId: 'blood_moon',
    durationTicks: 6000,
    color: '#b51f2f',
    intensity: 0.45
})

MoonJS.setVanillaPhase(level, 4)
MoonJS.stop(level)
MoonJS.clearVanillaPhase(level)
const state = MoonJS.getState(level)
```

接口语义：

- `start(level, phaseId, options)`：启动或替换当前维度的月亮事件。
- `stop(level)`：停止当前事件并清除事件专属贴图与染色。
- `setVanillaPhase(level, phase)`：设置当前维度的原版月相视觉覆盖值。
- `clearVanillaPhase(level)`：移除视觉覆盖，恢复按世界时间计算的原版月相。
- `getState(level)`：返回只读状态对象，用于脚本判断当前状态。

`durationTicks` 为 `-1` 时无限持续；非负值表示从当前服务器游戏刻起算的持续刻数。`color` 支持 `#RRGGBB` 和 `#AARRGGBB`，内部以 RGB 和强度分别保存；`intensity` 必须在 0.0 到 1.0 之间。

KubeJS 绑定只接受 `ServerLevel` 或 KubeJS 的 `ServerLevelKJS` 包装对象。客户端脚本不能调用改变服务端状态的接口。

## 服务端到客户端同步

使用 Forge `SimpleChannel` 注册单向服务端到客户端的月亮状态包。数据包至少包含：

- 维度 ResourceKey。
- 状态版本号。
- 月相 ID。
- 原版月相索引或空标记。
- 单张贴图 ResourceLocation 或空标记。
- 事件 ID与结束刻。
- RGB 颜色和强度。

同步时机：

1. 玩家登录。
2. 玩家重生。
3. 玩家切换维度。
4. 当前维度状态发生变化。

客户端使用维度 ResourceKey 建立状态缓存。退出世界和客户端断开连接时清空缓存。服务端不接受客户端反向修改状态的数据包。

## 客户端月亮渲染

在 Forge 客户端模组总线上注册适用于原版 Overworld 天空效果的 moonJS `DimensionSpecialEffects`。自定义效果继承原版 Overworld 效果的天空参数，并在当前客户端状态有月相覆盖时接管天空绘制：

- 太阳、天空背景、星空和云层保持 1.20.1 原版行为。
- 月亮绘制改为使用客户端状态中的单张贴图。
- 没有自定义贴图时使用原版月相图集，并使用同步的 0 到 7 索引。
- 没有月相覆盖时返回原版路径，不改变普通世界显示。

天空绘制实现必须保持原版的深度、混合、纹理绑定和矩阵恢复顺序，避免影响后续方块、实体和 GUI 渲染。贴图路径只作为客户端资源定位，不允许服务端发送任意文件路径或网络地址。

客户端自定义维度若使用原版 Overworld effects location，则复用该渲染效果；其他维度仍可使用服务端状态和血月颜色同步，但若其天空效果不支持月亮绘制，则不强行显示月亮。

## 全世界颜色渲染

当客户端状态的染色强度大于零时，同时使用三种 Forge 客户端扩展：

1. `DimensionSpecialEffects` 的光照贴图颜色调整：让方块和实体光照受血月色调影响。
2. `ViewportEvent.ComputeFogColor`：让远景雾颜色与血月一致。
3. `RenderGuiOverlayEvent` 的世界末端染色层：使用低透明度 RGB 矩形覆盖最终世界画面，确保天空、未受光照物体和远景也被染色。

染色层不得覆盖 HUD 文本和聊天内容。颜色强度由服务端状态控制，客户端不自行推断或修改。

## 错误处理

- KubeJS 缺失：跳过 KubeJS 插件，不影响 Forge 主模组。
- 状态中引用了未注册月相：记录警告并退回原版月相，不让客户端崩溃。
- 客户端找不到贴图：使用 Minecraft 缺失纹理并记录资源路径。
- 非法参数：在 KubeJS 调用处立即抛出带字段名的异常。
- 客户端收到未知维度或过旧版本状态：丢弃该包，不覆盖当前缓存。
- 状态切换期间不得在客户端线程访问服务端对象。

## 验证计划

### 静态与构建验证

- 检查所有 Java 包路径不含 `example`。
- 检查 `mods.toml`、`kubejs.plugins.txt` 和 `pack.mcmeta` 的资源替换结果。
- 使用项目本地 Gradle 缓存执行 `compileJava` 和 `build`。
- 检查最终 JAR 包含 moonJS 主类、KubeJS 插件类和插件资源，不包含 KubeJS/Rhino 二进制。
- 执行 `git diff --check`；当前项目没有 Git 仓库时改用文件编码、路径和关键资源合同检查。

### 行为验证

- 纯 Java 状态测试：默认状态、月相覆盖、启动事件、无限事件、定时过期、颜色边界和 SavedData 序列化。
- KubeJS API 合同测试：注册、重复注册、非法参数、启动、停止和查询。
- 数据包结构测试：维度、版本号、纹理路径和颜色字段能够编码解码。

### 运行时验证

- 在安装 KubeJS 的 Forge 1.20.1 客户端中执行注册和启动脚本。
- 验证登录、重生、切维度和重载后的同步。
- 验证原版月相覆盖、单张自定义月相贴图和血月全世界染色。
- 如果本次环境无法启动游戏，只报告构建与静态验证结果，不把它们等同于游戏内验证。

## 当前限制

- 首个版本不提供 GUI、命令和数据包 JSON 注册系统。
- 首个版本不修改世界真实 `dayTime`，月相改变只影响视觉状态和 KubeJS 查询结果。
- 首个版本不保证所有自定义维度都显示自定义月亮；颜色同步可覆盖这些维度，但月亮绘制依赖其天空效果注册方式。
