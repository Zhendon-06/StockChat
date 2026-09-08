# iOS 输入、流式请求与渐变回归

此适配仅编入 iOS 宿主，保留 Kuikly 的 TextArea、同步事件、输入法和共享业务逻辑。
`StockChatTextAreaAdapter.m` 只交换 `KRTextAreaView` 的两个桥接入口，用于过滤旧文本回写，
不修改 Pods。升级 Kuikly 后需重新运行这些检查，确认桥接入口及文本事件约定仍兼容。

Debug 构建从环境变量或仓库根目录已有的 `local.properties` 读取 `QWEN_API_KEY`、
`MIMO_VOICE_API_KEY`，生成到构建产物中的 `StockChatLocalConfig.plist`；运行环境变量优先。
Release 生成空配置，宿主也不读取此配置。模型配置页中保存的用户设置仍由原有共享逻辑处理。
不要提交本地配置、生成的 plist 或包含凭据的构建产物。

先启动一个 iOS 模拟器并执行：

```sh
xcodebuild -workspace iosApp/iosApp.xcworkspace -scheme iosApp \
  -configuration Debug -sdk iphonesimulator \
  -derivedDataPath /tmp/stockchat-ios-build CODE_SIGNING_ALLOWED=NO build
sh iosApp/tests/run_regressions.sh /tmp/stockchat-ios-build
```

原生检查覆盖滞后回写、事件反馈循环、清空后重输、草稿恢复、中文/emoji/多行、
删除回写、输入法组合状态与多个输入框隔离。Python 检查覆盖配置优先级、
缺失配置、properties 转义及 Debug 切换 Release 后移除凭据。

模拟器手动回归：连续输入长文本、中文拼音选词、移动光标编辑、多行删除，
分别通过发送按钮和键盘发送问题，确认输入清空、AI 返回回答、下一轮仍可输入。

`StockChatStreamRequest` 用独立串行队列处理 NSURLSession 增量数据与 SSE 分帧。
首段立即回调，后续最多每 50ms 合并一次文本更新，结束前刷新剩余文本。
宿主通过 `aliyunNativeStreaming=1` 启用共享页面已有的流式入口；模型自身仍需支持流式输出。
取消或销毁页面会结束请求，兼容服务返回的普通 JSON 回答。

`StreamAndGradientRegression` 用本地 NSURLProtocol 响应检查首段在服务结束前到达、
中文 UTF-8/CRLF 任意分包、多行 SSE、结束信号、提前断流、HTTP 错误、取消和 JSON 兼容路径。
渐变检查覆盖两端透明、浅色/深色背景及普通不透明渐变不受影响。
模拟器应观察到回答先出现部分文本再增长，底部内容自然渐隐且没有灰黑横带。
纯行情卡片查询不一定会调用生成模型，因此不能用卡片一次性显示判断 SSE 是否正常。

`ToastBridgeRegression` 通过实际 `hrv_callWithMethod("toast", ..., nil)` 检查收藏提示桥接，
覆盖主线程/后台调用、提示替换、独立定时消失、空内容以及页面退出后的调用。
此桥接缺失曾导致收藏后的提示触发 `KRBaseModule` 断言崩溃。
手动回归需验证收藏/取消星标、提示出现及重启后收藏列表仍能读取记录。
