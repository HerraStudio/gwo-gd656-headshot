# 本地编译依赖

将自己已有的 NeoForge 1.21.1 模组复制至本目录：

- GWO（内部版本 2.12.87，已验证 Beta1.0 Fix 0.5）命名为 `gwo.jar`。
- GD656Killicon 1.1.0.020-1.21.1-neoforge 命名为 `gd656killicon.jar`。

第三方 JAR 不随本项目分发，已被 `.gitignore` 排除。Java 21 下运行 `gradlew.bat clean build`，正式构建不要加 `-Psmoke`。
