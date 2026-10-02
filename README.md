# GWO / GD656 爆头兼容 1.0.0

为 Minecraft 1.21.1 NeoForge 桥接 GWO 的原始爆头判定，让 GD656Killicon 显示其原有爆头击杀图标、音效和字幕。

由 HerraStudio 维护，采用 MIT 许可证。

[GitHub 仓库](https://github.com/HerraStudio/gwo-gd656-headshot) · [下载](https://github.com/HerraStudio/gwo-gd656-headshot/releases/latest) · [验证说明](docs/VALIDATION.md)

![原生爆头图标和提示](docs/images/headshot.png)

## 安装

将 `gwo-gd656-headshot-1.21.1-neoforge-1.0.0.jar` 放入游戏 `mods`，保留原有 GWO 与 GD656Killicon，重启生效。联机服务端和客户端均安装此兼容包。

- Minecraft 1.21.1 / Java 21。
- NeoForge 21.1.233 至 21.1.x，实际客户端验证使用 21.1.251。
- GWO 内部版本 2.12.87，验证安装包为 Beta1.0 Fix 0.5。
- GD656Killicon 1.1.0.020-1.21.1-neoforge。

无需按键和额外配置。图标的外观、位置、预设、音效与开关继续使用 GD656 原配置。

## 行为

爆头致死交给 GD656 原来的爆头击杀流程；身体击杀保持普通击杀效果。非致死爆头不会额外制造击杀提示，之后的身体致死不会继承旧爆头标记。穿透同一颗子弹命中多个实体时，分别使用各自的真实命中结果。

以 GWO 服务端原始 `isHeadshot` 返回值为依据，沿用玩家真实部位、生物头部高度规则以及爆头免疫标签。桥接只在当前子弹应用当前受害者伤害期间有效，按受害者 UUID 严格匹配，结束后清空。既有 GWO 命中判定、伤害、倍率、穿甲、弹药和服务端击杀数量保持原值。

GD656 使用其现有网络包和渲染器，不另外发送击杀包。其他枪械模组与普通伤害继续走 GD656 原有分类。

## 编译与验证

本地依赖准备见 [libs/README.md](libs/README.md)。

```powershell
./gradlew.bat clean build
```

`src/test` 包含当前命中作用域与受害者隔离检查；`src/smoke` 只在 `-Psmoke` 编译，使用独立临时世界、真实 GWO 子弹和 GD656 服务器分类/客户端图标队列验证。

10 项单元测试及原生爆头/身体/穿甲/穿透/连续命中检查通过。已实测与 Body Part Damage 1.0.2、GWO Precise Blocks 1.0.3、BetterBloodOverlay 2.2.0 同时运行，保留原部位伤害及 19 米多段飞行后的正确分类。[检查摘要](docs/runtime-validation.txt)。

```powershell
./gradlew.bat -Psmoke runClient
```

测试实例首次运行需要 `run-smoke/options.txt` 中设置 `onboardAccessibility:true`。原生测试隔离测试窗口输入，并在自己创建的世界执行。组合检查需要把 Body Part Damage 1.0.2、GWO Precise Blocks 1.0.3、BetterBloodOverlay 2.2.0 放入 `run-smoke/mods`。

测试枪使用自行创建的公开配置，没有引用受保护模型。测试代码会清理 GWO 自有反馈音频队列，防止缺失测试声音中断 GD656 的原始网络、图标和音效验证。该清理代码不进入正式 JAR。

项目不分发任何第三方模组、原始图标、授权内容包或游戏存档。
