# Android Release 包

| 项目 | 值 |
| --- | --- |
| 文件 | `StockChat-1.0-release-debugsigned.apk` |
| 大小 | 5,650,468 字节（约 5.39 MiB） |
| SHA-256 | `b13bf26d3f0bb00ae55fa6d503c84a83681d2ccb8b20b3790abaea9b088542b0` |
| applicationId | `com.guet.liang.stockchat` |
| versionName / versionCode | 1.0 / 1 |
| minSdk / targetSdk | 23 / 34 |
| 构建类型 | `release`，未开启混淆 |
| 签名 | Android Debug 证书（`~/.android/debug.keystore`，CN=Android Debug） |
| 签名证书 SHA-256 | `6382cc4c93571f27be5c4e00f08154f44eac8c68e3b683276210a4f76da84653` |
| 源码基线 | `2dc7614` + 工作区重复提问重新请求修复 |
| 构建日期 | 2026-09-14 |

## 说明

- 这是一个用调试签名的 release 包，只用于演示与体验，不能上架应用商店。
- 这是本次评审专用包，已预置评审所需的 AI 配置，安装后可直接体验 AI 问答；仅用于本次评审，不建议用于其他场景。
- 与已安装的 Debug 包签名相同，可直接覆盖安装。

## 评审验收路径

安装后可按以下路径快速检查核心交付：打开 AI 问答并发送包含股票名称的问题，确认回答中的 Markdown 与行情卡片正常渲染；点击行情卡片进入详情页，切换分时/K 线并查看 AI 解读；返回聊天页后再检查会话记录和失败重试状态。

三端演示视频位于 `docs/media`，文件由 Git LFS 管理，检出仓库后执行 `git lfs pull` 即可播放。

## 安装

```bash
adb install -r docs/release/StockChat-1.0-release-debugsigned.apk
```

## 重新打包

```bash
./gradlew :androidApp:assembleRelease
cp androidApp/build/outputs/apk/release/androidApp-release.apk docs/release/StockChat-1.0-release-debugsigned.apk
```
