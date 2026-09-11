# Android Release 包

| 项目 | 值 |
| --- | --- |
| 文件 | `StockChat-1.0-release-debugsigned.apk` |
| 大小 | 5.3M |
| SHA-256 | `9245482b78ed3cd65826a17a61b30201dca2a13b59143720df7ea3f579b54aaa` |
| applicationId | `com.guet.liang.stockchat` |
| versionName / versionCode | 1.0 / 1 |
| minSdk / targetSdk | 23 / 34 |
| 构建类型 | `release`，未开启混淆 |
| 签名 | Android Debug 证书（`~/.android/debug.keystore`，CN=Android Debug） |
| 签名证书 SHA-256 | `6382cc4c93571f27be5c4e00f08154f44eac8c68e3b683276210a4f76da84653` |
| 源码基线 | `18256d0` 加上本地未提交的三端修复 |

## 说明

- 这是一个用调试签名的 release 包，只用于演示与体验，不能上架应用商店。
- 包内**不含**任何 API Key。`release` 构建类型会把 `QWEN_API_KEY` 与 `MIMO_VOICE_API_KEY` 置空，安装后请在应用内「设置 → 模型配置」填入服务商与 Key，行情与今日市场功能无需 Key 即可使用。
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
