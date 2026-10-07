# ImmersiveSwitch

一个简洁的 Android 状态栏图标开关，通过 Shizuku 的 ADB / shell 权限工作，无需 Root。第一版只提供 Shizuku 状态显示、授权以及“隐藏状态栏”和“恢复状态栏”两个按钮。

## 要求

- Android 8.0（API 26）或更新版本。
- 安装 [Shizuku](https://shizuku.rikka.app/download/) 13 或更新版本，并启动服务。
- 无 Root 设备：Android 11 及以上可使用无线调试启动 Shizuku；Android 8–10 需要电脑 ADB。设备重启后需要重新启动 Shizuku。详见 [Shizuku 使用指南](https://shizuku.rikka.app/guide/setup/)。
- 系统必须支持 `cmd statusbar send-disable-flag`。不同厂商系统的表现可能不同。

## 使用方法

1. 先打开 Shizuku，通过无线调试或电脑 ADB 启动服务。
2. 安装并打开 ImmersiveSwitch。应用会检测 Shizuku，并在首次连接时请求授权，选择允许。
3. 状态显示“Shizuku 正在运行，已授权”后，点击“隐藏状态栏”或“恢复状态栏”。
4. 若拒绝了权限，请在 Shizuku 的“已授权应用”中允许 ImmersiveSwitch，再返回应用。
5. 如需恢复图标，请在退出应用前点击“恢复状态栏”。退出应用不会自动执行恢复命令。

隐藏时执行：

```sh
cmd statusbar send-disable-flag clock system-icons notification-icons
```

恢复时执行：

```sh
cmd statusbar send-disable-flag none
```

这些命令控制时钟、系统图标和通知图标，不保证移除状态栏占用的空间，也不会隐藏导航栏。应用通过 Shizuku UserService 执行固定命令；命令失败时会显示错误。界面显示的是 Shizuku 状态和命令结果，不读取实际图标状态。

## 获取 Debug APK

进入仓库的 [Actions](https://github.com/zlm110387-prog/ImmersiveSwitch/actions)，打开成功的 **Build Debug APK** 运行，在 **Artifacts** 下载 **ImmersiveSwitch-debug**，解压后安装 `app-debug.apk`。下载 Artifact 需要登录 GitHub。

推送到 main、Pull Request 和手动运行都会触发构建，同时执行 Android Lint。

## 本地构建

使用 JDK 17、Gradle 8.9 和 Android SDK 35（Build Tools 35.0.0）。可在 Android Studio 中打开项目，或者安装 Gradle 8.9 后执行：

```sh
gradle :app:assembleDebug :app:lintDebug
```

将 SDK 路径配置到环境变量 `ANDROID_HOME`，或在未提交的 `local.properties` 中设置 `sdk.dir`。APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

本项目固定使用 Android Gradle Plugin 8.7.3 和 Shizuku API 13.1.5。
