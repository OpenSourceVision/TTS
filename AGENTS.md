# TTS Server Android Project Guidelines & Architecture Notes

## 1. 核心架构设计

- **服务模型**: `TtsServerService` 通过前台服务运行 Ktor CIO HTTP 服务器，监听 `8080` 端口（支持 `/`, `/tts`, `/api/tts`）。
- **单通道串行合成**: Android `TextToSpeech` 底层硬件引擎是全局单通道的，不支持真正的多线程并发合成。
  - 必须使用 `synthesisMutex` 保证同一时刻只有一个合成任务占用 TTS 引擎。
  - 请求参数解析与规则替换在锁外完成，进入 `synthesizeText` 时加锁。

## 2. 阅读（Legado）对接与防跳段规范

1. **标准 MIME 类型**:
   - HTTP 响应必须返回 `Content-Type: audio/wav`，切勿使用 `audio/x-wav` 或 `audio/mpeg`（否则阅读 App 抛出 `ContentType not match` 并跳段）。
2. **纯标点与空白兜底 (`isSpeakableText`)**:
   - 当文本经过规则过滤后只剩下排版标点、空格、特殊符号时，**禁止调用底层 TTS 引擎**（TTS 引擎对纯标点会报错或生成 < 44 字节的空文件）。
   - 必须通过 `getSilenceWav()` 直接返回极短的标准静音 WAV 音频。
3. **字符清洗 (`cleanAndSanitizeText`)**:
   - 在合成前清洗零宽字符（`\u200B`~`\u200D`、`\uFEFF`）、不可见控制字符以及全角空格（`\u3000`），避免第三方引擎挂起或出现异常时延。
4. **客户端断连与预加载取消 (`Broken pipe`)**:
   - 阅读 App 后台预加载在切章/翻页时会主动关闭 Socket，捕获 `Broken pipe`、`Connection reset` 时记录为 `CANCELLED`，不计入连续失败重置计数，并及时释放锁资源。
5. **音频有效性与落盘缓冲**:
   - 有效 WAV 文件大小必须 `> 44` 字节。
   - 收到 `onDone` 回调后若文件处于落盘临界状态，应轮询等待最多 300ms 保证 IO 完全落盘。
6. **引擎故障自愈**:
   - 单次合成超时上限为 10 秒（防止阻塞阅读客户端并发队列）。
   - 连续 3 次非客户端取消的合成失败时，主动 `shutdown` 并在主线程重新构建 TTS 引擎实例。
