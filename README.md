# Precise Volume

安卓系统公开接口只允许把系统全局音量设置到整数挡位，例如 0 到 15。普通 app 不能真正设置 7.3/15 这样的系统音量，也不能让其它 app 的声音做到 2.5% 是 5% 的一半。

这个版本把功能拆成两层：

- 系统媒体音量粗调：仍然按安卓整数挡位设置。
- 本 app 精确增益：用 `AudioTrack` 播放 440Hz 测试音，并对音频样本做连续线性增益。
- 2.5% 增益的样本幅度严格等于 5% 增益的一半。
- 全局微调实验：常驻前台服务会尝试把全局音效挂到 audio session 0，用 `DynamicsProcessing` 或 `Equalizer` 做额外衰减。
- 下拉快捷设置 Tile：安装后可在系统快捷设置里添加“音量微调”，之后像 v2rayNG 一样从下拉菜单开关。
- 开启时会记录当前系统媒体音量挡位；关闭时会恢复到开启前的系统媒体音量。
- Tile 开启时会用一个瞬时透明 Activity 拉起前台服务，避免 app 没在前台时 Tile 点击无效。

结论：真正精确音量必须发生在播放端。普通非 root app 没有官方保证能精细控制其它 app 的全局输出音量；这版会实测你的设备是否允许全局音效链。如果状态显示“不被此设备支持”，就需要 Shizuku、root、厂商音频服务，或把音频放到本 app 内播放。

## 测试方式

1. 安装 APK。
2. 打开 app，把目标设为 5%，点“开启全局微调”。
3. 播放其它 app 的声音，确认是否变小。
4. 切到 2.5%，如果设备支持全局音效链，声音应该约为 5% 的一半。
5. 在系统下拉快捷设置里添加“音量微调”Tile，以后可从下拉菜单开关。
6. 关闭全局微调后，系统媒体音量应恢复到开启前的挡位。

## 打开方式

用 Android Studio 打开 `PreciseVolumeAndroid` 文件夹，等待 Gradle 同步后运行到手机或模拟器。

## GitHub 云编译

项目已经包含 GitHub Actions 配置：`.github/workflows/android-build.yml`。

推送到 GitHub 后，进入仓库的 **Actions** 页面，打开 **Android APK** 工作流。构建成功后，在页面底部的 **Artifacts** 下载 `precise-volume-debug-apk`，里面就是 debug APK。

也可以在 Actions 页面手动点击 **Run workflow** 重新编译。
