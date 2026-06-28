# Precise Volume

安卓系统公开接口只允许把系统全局音量设置到整数挡位，例如 0 到 15。普通 app 不能真正设置 7.3/15 这样的系统音量，也不能让其它 app 的声音做到 2.5% 是 5% 的一半。

这个版本把功能拆成两层：

- 系统媒体音量粗调：仍然按安卓整数挡位设置。
- 本 app 精确增益：用 `AudioTrack` 播放 440Hz 测试音，并对音频样本做连续线性增益。
- 2.5% 增益的样本幅度严格等于 5% 增益的一半。

结论：真正精确音量必须发生在播放端。普通非 root app 无法精细控制其它 app 的全局输出音量；如果要对任意 app 生效，需要系统级权限、root、厂商音频服务，或把音频放到本 app 内播放。

## 打开方式

用 Android Studio 打开 `PreciseVolumeAndroid` 文件夹，等待 Gradle 同步后运行到手机或模拟器。

## GitHub 云编译

项目已经包含 GitHub Actions 配置：`.github/workflows/android-build.yml`。

推送到 GitHub 后，进入仓库的 **Actions** 页面，打开 **Android APK** 工作流。构建成功后，在页面底部的 **Artifacts** 下载 `precise-volume-debug-apk`，里面就是 debug APK。

也可以在 Actions 页面手动点击 **Run workflow** 重新编译。
