Attempting to perform the InitializeDefaultDrives operation on the 'FileSystem' provider failed.
# 工时日记 Android 版

原生 Android 个人考勤应用。每日记录写入手机本地 SQLite 数据库，不使用浏览器存储，也不需要联网。支持按月查看、工作日/休息日调整、计薪时段自动计算，以及 CSV 导出和恢复。应用会把数据库纳入 Android 系统备份；系统备份是否运行取决于设备的备份设置，建议定期另存 CSV。

## 规则

- 上班打卡须在 08:00–09:00。
- 08:00–12:00、13:30–17:30、18:00–24:00 为计薪时段。
- 12:00–13:30 和 17:30–18:00 不计工时。
- 周一至周五默认工作日，周末默认休息；日期可单独切换。
- 欠工时按已过工作日 × 8 小时减去已记录计薪工时计算；未录入的工作日计入欠工时。

## 本机构建

使用 Android Studio 打开此目录，等待 Gradle 同步后运行 app 配置，或在已安装 JDK 17、Android SDK Platform 34 与 Build Tools 的环境中执行：

    gradle assembleDebug

构建产物为 app/build/outputs/apk/debug/app-debug.apk。项目附有 GitHub Actions 构建流程，推送到 main 后会生成可下载 30 天的 Debug APK 构建产物。

## 数据备份与恢复

在应用顶部点击“导出备份”，选择手机上的文件位置保存 CSV。恢复时点击“恢复备份”并选择 CSV；相同日期的内容会被导入记录覆盖。CSV 列格式与网页版本兼容，可从网页版本导出后导入 Android 版。

此版本使用 Android 原生 API，不依赖第三方库。

