# Precise Volume

安卓系统公开接口只允许把系统音量设置到整数挡位，例如 0 到 15。普通 app 不能真正设置 7.3/15 这样的系统音量，也不能精细控制其他 app 的播放音量。

这个原型做的是系统允许范围内最稳的体验：

- 用 0.1% 精度的滑杆输入目标音量。
- 自动换算成当前音频通道可用的最近系统挡位。
- 支持媒体、铃声、通知、闹钟、通话等通道。
- 提供微调按钮和一键应用。
- 在界面里明确显示“目标值”和“实际系统挡位”，避免误以为系统真的支持小数挡。

后续如果要做到真正连续音量，只能用于本 app 自己播放的音频：用播放器内部增益把系统挡位之内再细分。对其它 app 的全局音量，普通非 root app 做不到。

## 打开方式

用 Android Studio 打开 `PreciseVolumeAndroid` 文件夹，等待 Gradle 同步后运行到手机或模拟器。

## GitHub 云编译

项目已经包含 GitHub Actions 配置：`.github/workflows/android-build.yml`。

推送到 GitHub 后，进入仓库的 **Actions** 页面，打开 **Android APK** 工作流。构建成功后，在页面底部的 **Artifacts** 下载 `precise-volume-debug-apk`，里面就是 debug APK。

也可以在 Actions 页面手动点击 **Run workflow** 重新编译。
