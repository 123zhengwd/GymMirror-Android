# 乐刻明星教练 · 闺蜜机 App

独立 Android App，内置乐堤港 NFC 展示页。首页固定使用乐堤港门店 `tenantId=5197`。基础页面和媒体在 APK 内；碰一碰动态数据继续使用原页面的线上接口，需设备联网。

## 更新页面

页面可编辑源码 `nfc-h5` 需单独提供。将它放在两个 Android 工程同级的目录，或设置 `COACH_SHOWCASE_H5_SOURCE` 指向该目录。准备好源码后，在本项目执行：

```sh
sh refresh-showcase.sh
```

脚本会缩小展示卡中的 4K 腿弯举视频，然后执行 `./gradlew :app:assembleDebug`。APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

App 使用独立包名 `com.kun.coachshowcase`，与 Training App 分开安装。当前页面按 1080×1920 竖屏展示；实机上仍需确认触控、视频、NFC 更新及屏幕常亮。

## 门店锁定

App 内置 Android 设备所有者组件 `com.kun.coachshowcase/com.example.coachshowcase.KioskAdminReceiver`。启用设备所有者后，且管理员密码已经在机器上设置时，App 会将自己列入锁定任务许可名单，并在前台进入锁定任务模式。只有这两项都就绪才会锁定；普通全屏或屏幕固定模式不能替代。

在屏幕左上角约 72dp 见方的隐藏区域内，4 秒内连续点 7 次，可打开管理员密码界面。首次进入设置 6–12 位数字密码；以后验证成功才能退出锁定并返回原桌面。密码只保留加盐 PBKDF2 摘要在本机，连续输错 5 次会暂停 30 秒。请勿通过聊天或代码传输正式密码。

启用顺序：先在实机设置并验证密码；再确认机器上只有一个用户、没有账户并保留可回退的 APK；最后以 ADB 为本 App 设置设备所有者并重新启动 App。设备所有者会改变系统级管理状态，需在现场验证上滑、返回桌面、重启后自动展示和管理员退出。验证完毕后，关闭当前开放的 TCP 5555 ADB 调试端口，避免门店网络上的其他设备通过调试接口绕过锁定。
