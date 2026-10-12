# 闺蜜机 Android 项目

本仓库包含两个可独立用 Android Studio 打开的 Android 工程：

- `Training` — 包名 `com.example.training`
- `CoachShowcase` — 包名 `com.kun.coachshowcase`

## 开发环境

安装 Android Studio 及项目 Gradle 配置所需的 Android SDK。首次打开工程时，在 Android Studio 中分别打开 `Training/` 和 `CoachShowcase/`，等待 Gradle 同步完成。

也可以在 macOS/Linux 终端分别构建：

```sh
cd Training && ./gradlew assembleDebug
cd ../CoachShowcase && ./gradlew assembleDebug
```

Windows 用户可运行各项目目录中的 `gradlew.bat assembleDebug`。

## 页面资源

CoachShowcase 当前使用的展示页面和静态资源已包含在 App 的 assets 中。页面的独立可编辑源码 `nfc-h5` 不在本仓库；`CoachShowcase/refresh-showcase.sh` 需要将该源码目录放在仓库根目录旁边或通过 `COACH_SHOWCASE_H5_SOURCE` 指定路径后才能刷新页面资源。

## 本机配置

请勿提交 Android 签名密钥、`local.properties`、个人录制内容或任何凭据。Debug 构建会使用 Android Gradle Plugin 的本机默认调试签名。
