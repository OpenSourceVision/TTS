<div align="center">

# 🎙️ Android TTS 本地转发服务器

将 Android 手机本地及第三方 TTS 语音引擎转为标准的 **HTTP 音频流接口**，支持流式实时合成与全盘错误诊断。

[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-7.4%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Ktor](https://img.shields.io/badge/Server-Ktor%20CIO-087CFA?logo=ktor&logoColor=white)](https://ktor.io)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

</div>

---

## ✨ 核心特性

- **🚀 高性能转发** — 基于 Ktor CIO 引擎，提供标准 HTTP 音频接口
- **🛡️ 智能防跳段** — 自动清洗不可见字符，纯标点/特殊符号段落静音兜底
- **🤖 引擎自愈** — 超时自动打断，连续失败自动重建 TTS 实例
- **🔠 发音规则** — 支持自定义正则替换与多音字纠错映射
- **🩺 日志诊断** — 实时记录请求明细与耗时，支持一键导出排查
- **🔋 后台保活** — 前台服务结合电量优化，保障锁屏长效稳定朗读

---

## 🚀 阅读（Legado）配置指南

服务开启后，默认监听 `http://127.0.0.1:8080`（支持 `/`、`/tts`、`/api/tts` 路径，支持 GET 与 POST）。

### 推荐配置 1：GET 请求（最常用）

在阅读 App 的「网络导入」或「新建 HTTP-TTS」中填入：

```json
{
  "name": "本地TTS转发 (GET)",
  "url": "http://127.0.0.1:8080/tts?text={{speakText}}&speed={{speakSpeed}}",
  "contentType": "audio/wav",
  "concurrentTasks": 1
}
```

### 推荐配置 2：POST 纯文本 / JSON 请求

```json
{
  "name": "本地TTS转发 (POST)",
  "url": "http://127.0.0.1:8080/tts,{\"method\":\"POST\",\"body\":\"{{speakText}}\"}",
  "contentType": "audio/wav",
  "concurrentTasks": 1
}
```

> **⚠️ 注意事项**：
> - `concurrentTasks` **请务必设置为 1**：由于 Android 系统的 TTS 硬件引擎为单通道串行处理，设置并发大于 1 会导致后续请求在队列中超时排队，从而引发阅读器跳段。
> - `contentType` 请填写 `audio/wav`。

---

## 📋 API 参数对照表

| 参数名 | 别名支持 | 类型 | 说明 |
|---|---|---|---|
| `text` | `key`, `t`, `txt`, `speakText` | String | 待合成文本内容（必填，POST 纯文本时直接读取 Body） |
| `rate` | `speed`, `speakSpeed`, `speechRate`, `r`, `s` | Float | 语速倍率（支持 0.1~3.0，或百分比 0~100/100~300） |
| `pitch` | `speakPitch`, `p` | Float | 语调倍率（支持 0.5~2.0） |
| `engine` | `e` | String | 指定 TTS 引擎包名（留空则使用应用内默认设置） |

---

## 🔧 推荐 TTS 引擎

| 引擎 | 特点说明 |
|---|---|
| **[MultiTTS](https://github.com/nobody/multitts)** | 经典的离线/多音源 TTS 引擎，支持丰富的语音包与规则 |
| **[CloneTTS](https://github.com/sipeter/CloneTTS)** | 离线原生 TTS，支持 1–3 秒音色克隆、注册为系统引擎并提供本地 API |
| **[TalkifyTTS](https://github.com/LonePheasantWarrior/TalkifyTTS)** | 多云引擎连接器，支持微软/腾讯/阿里等云端服务 |

---

## 🛠️ 技术栈

| 模块 | 选型 |
|---|---|
| 编程语言 | 100% Kotlin + Coroutines Flow |
| UI 框架 | Jetpack Compose + Material Design 3 |
| 网络服务 | Ktor Server CIO (`io.ktor:ktor-server-cio`) |
| 依赖管理 | Ktor BOM (`io.ktor:ktor-bom`) |
| 本地存储 | Room Database (与 KSP 增量编译) |
| 日志导出 | FileProvider + 自定义 CrashHandler |

---

## 💡 常见问题与排查

**Q: 为什么日志中会偶尔出现 `Broken pipe` 记录？**  
A: `Broken pipe` 是由于阅读 App 侧在后台预加载段落时，因用户翻页、切换章节或预加载超时主动关闭了连接。服务已智能将此类请求标记为 `CANCELLED` 并立即释放通道，不会影响当前正在播放的音频。

**Q: 遇到朗读中途卡住或跳段怎么办？**  
1. 请检查阅读 App 中的并发任务数（`concurrentTasks`）是否设为 **1**。
2. 服务内置了自愈机制：单句超过 10 秒会自动打断清空，若连续失败 3 次会自动重建 TTS 引擎。
3. 可在 App 内「日志」页面查看明细，若仍有异常可点击右上角一键复制或分享诊断报告。

**Q: 纯标点或空白段落如何处理？**  
A: 服务已内置智能静音兜底机制，遇到无发音字符或纯特殊符号的段落会自动返回极短的静音音频，保障阅读器平滑过渡，不会跳段或报错。

---

<div align="center">

如果这个项目对你有帮助，欢迎 ⭐ Star 支持

</div>
