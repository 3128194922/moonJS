$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$javaRoot = Join-Path $projectRoot 'src/main/java'
$resourceRoot = Join-Path $projectRoot 'src/main/resources'

# 扫描项目文本与文件名，但排除文档、计划目录、构建产物和本合同脚本自身。
$forbidden = rg -n 'com\.example|examplemod|ExampleMod' $projectRoot `
    -g '!build/**' -g '!run/**' -g '!docs/**' -g '!.superpowers/**' -g '!scripts/**'
if ($LASTEXITCODE -eq 0) { throw "模板标识仍存在:`n$forbidden" }
if ($LASTEXITCODE -gt 1) { throw 'rg 扫描失败' }

$props = Get-Content -Raw (Join-Path $projectRoot 'gradle.properties')
if ($props -notmatch '(?m)^mod_id=moonjs$') { throw 'mod_id 未设置为 moonjs' }
if ($props -notmatch '(?m)^mod_group_id=com\.moonjs$') { throw 'mod_group_id 未设置为 com.moonjs' }

$requiredSources = @(
    'com/moonjs/MoonJSMod.java'
    'com/moonjs/MoonJSConstants.java'
    'com/moonjs/server/MoonState.java'
    'com/moonjs/server/MoonStateSavedData.java'
    'com/moonjs/server/MoonPhaseDefinition.java'
    'com/moonjs/server/MoonPhaseRegistry.java'
    'com/moonjs/server/MoonEventOptions.java'
    'com/moonjs/server/MoonEventService.java'
    'com/moonjs/server/MoonServerEvents.java'
    'com/moonjs/network/MoonNetwork.java'
    'com/moonjs/network/MoonStatePacket.java'
    'com/moonjs/kubejs/MoonJSBindings.java'
    'com/moonjs/kubejs/MoonJSKubeJSPlugin.java'
    'com/moonjs/kubejs/MoonKubeJSOptions.java'
    'com/moonjs/client/MoonClientState.java'
    'com/moonjs/client/MoonDimensionEffects.java'
    'com/moonjs/client/MoonSkyRenderer.java'
    'com/moonjs/client/MoonClientEvents.java'
)
foreach ($relativePath in $requiredSources) {
    if (-not (Test-Path -LiteralPath (Join-Path $javaRoot $relativePath) -PathType Leaf)) {
        throw "缺少必需 Java 源文件: $relativePath"
    }
}
if ((Get-ChildItem -LiteralPath $javaRoot -Directory -Recurse | Where-Object { $_.FullName -match '(?i)(^|[\\.])example([\\.]|$)' }).Count -gt 0) {
    throw 'Java 源码包路径包含 example'
}

$requiredResources = @('META-INF/mods.toml', 'kubejs.plugins.txt', 'pack.mcmeta')
foreach ($relativePath in $requiredResources) {
    if (-not (Test-Path -LiteralPath (Join-Path $resourceRoot $relativePath) -PathType Leaf)) {
        throw "缺少必需资源文件: $relativePath"
    }
}

$pluginFile = Join-Path $resourceRoot 'kubejs.plugins.txt'
$pluginEntries = @(Get-Content $pluginFile | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' -and -not $_.StartsWith('#') })
if ($pluginEntries.Count -ne 1) { throw "kubejs.plugins.txt 必须且只能列出一个插件，当前为 $($pluginEntries.Count) 个" }
if ($pluginEntries[0] -ne 'com.moonjs.kubejs.MoonJSKubeJSPlugin') { throw 'KubeJS 插件类条目错误' }

$build = Get-Content -Raw (Join-Path $projectRoot 'build.gradle')
$dependencyPaths = @(
    '../其他项目/KubeJS-2001/common/bin/main'
    '../其他项目/KubeJS-2001/.gradle/loom-cache/remapped_mods/loom_mappings_1_20_1_layered_hash_40359_v2/dev/architectury/architectury/9.1.12/architectury-9.1.12.jar'
    '../其他项目/KubeJS-2001/.gradle/loom-cache/remapped_mods/loom_mappings_1_20_1_layered_hash_40359_v2_forge_1_20_1_47_1_81_forge/dev/architectury/architectury-forge/9.1.12/architectury-forge-9.1.12.jar'
    '../其他项目/KubeJS-2001/.gradle/loom-cache/remapped_mods/loom_mappings_1_20_1_layered_hash_40359_v2/dev/latvian/mods/rhino/2001.2.2-build.17/rhino-2001.2.2-build.17.jar'
    '../其他项目/KubeJS-2001/.gradle/loom-cache/remapped_mods/loom_mappings_1_20_1_layered_hash_40359_v2_forge_1_20_1_47_1_81_forge/dev/latvian/mods/rhino-forge/2001.2.2-build.17/rhino-forge-2001.2.2-build.17.jar'
)
foreach ($relativePath in $dependencyPaths) {
    if (-not $build.Contains("compileOnly files('$relativePath')")) { throw "缺少编译期本地依赖声明: $relativePath" }
    if (-not (Test-Path -LiteralPath (Join-Path $projectRoot $relativePath))) { throw "本地依赖路径不存在: $relativePath" }
}

$mods = Get-Content -Raw (Join-Path $resourceRoot 'META-INF/mods.toml')
if ($mods -match '(?i)example|change\.me') { throw 'mods.toml 仍包含 MDK 示例或占位文本' }
foreach ($modId in @('forge', 'minecraft', 'kubejs')) {
    if (-not $mods.Contains("modId=`"$modId`"")) { throw "mods.toml 缺少依赖: $modId" }
}
if (-not $mods.Contains('modId="${mod_id}"')) { throw 'mods.toml 缺少 moonjs 模组标识模板属性' }
if ($mods -notmatch '(?s)\[\[dependencies\.\$\{mod_id\}\]\]\s*modId="kubejs"\s*mandatory=false\s*versionRange="\[2001\.6\.5,\)"\s*ordering="AFTER"\s*side="BOTH"') {
    throw 'KubeJS 可选依赖元数据不完整或约束不符'
}

Write-Output 'MoonJS naming/resource/dependency contract: PASS'
