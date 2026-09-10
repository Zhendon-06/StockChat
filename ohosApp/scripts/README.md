# 鸿蒙宿主回归测试

在仓库根目录执行：

```bash
node --test ohosApp/scripts/*.test.cjs
```

流式测试转译并执行实际 ArkTS 请求与解析源码，模拟鸿蒙 HTTP 事件、UTF-8 解码和定时器，覆盖增量提前回调、中文及 emoji 跨包、SSE 分帧、普通 JSON 兼容、错误与中断、取消清理和并发隔离，无需 API Key 或设备。

默认使用 macOS DevEco Studio 自带的 TypeScript。安装在其他位置时设置 `DEV_STUDIO_HOME`（指向 `Contents`），也可用 `STOCKCHAT_TYPESCRIPT_PATH` 指向 TypeScript 模块。测试中的 SDK 接口为模拟实现；仍需通过 DevEco/Hvigor 构建检查实际 ArkTS 语法和 SDK 类型，并在设备上验证聊天逐步显示。
