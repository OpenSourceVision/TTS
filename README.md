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

- **🚀 Ktor 原生流式响应** — 基于 Ktor CIO 高性能网络引擎，`respondBytesWriter` 秒级推流 WAV 块，点击即播
- **🤖 引擎自愈与熔断重置** — 具备连续失败（3次）自动重建 TTS 引擎机制；合成超时（15s）自动强杀队尾（`tts.stop()`）
- **🔤 标点智能拆句** — 依据句号、问号、感叹号等标点自动切分 300 字段落，极大地提升首包音频响应速度
- **🔠 多音字与替换正则** — 内置替换规则与多音字映射字典，支持发音纠错与文本自动清洗
- **🩺 崩溃与错误日志诊断** — 全局捕获未处理崩溃堆栈（UncaughtExceptionHandler），提供长按复制与 FileProvider 文件导出
- **🔋 后台与省电保护** — 完美适配锁屏后台前台服务（Foreground Service），配合电池白名单长时间稳定朗读

---

## 🚀 API 配置指南

服务开启后，默认通过本地 `http://127.0.0.1:8080/tts` 接收请求（支持 GET 与 POST）。

### 1. HTTP 接口参数

| 参数名 | 别名支持 | 类型 | 说明 |
|---|---|---|---|
| `text` | `key`, `t`, `txt` | String | **必填**。需要合成的朗读文本 |
| `rate` | `speed`, `speakSpeed`, `speechRate`, `r`, `s` | Float | 语速倍率（0.1 ~ 2.0，默认从 App 设定获取） |
| `pitch` | `speakPitch`, `p` | Float | 音调倍率（0.1 ~ 2.0，默认从 App 设定获取） |
| `engine` | `e` | String | 目标 TTS 引擎包名（留空使用默认全局引擎） |

### 2. 阅读软件配置示例 (以 Legado / 阅读 为例)

```json
{
  "id": "local_tts_forwarder",
  "name": "本地TTS转发",
  "url": "http://127.0.0.1:8080/tts?text={speakText}&rate={speakSpeed}",
  "contentType": "audio/wav",
  "concurrentTasks": 1
}
```

---

## 🔧 推荐 TTS 引擎

| 引擎 | 特点说明 |
|---|---|
| **[CloneTTS](https://github.com/sipeter/CloneTTS)** | 离线原生 TTS，支持 1–3 秒音色克隆、注册为系统引擎并提供本地 HTTP API，适合完全离线或注重隐私的设备端合成 |
| **[TalkifyTTS](https://github.com/LonePheasantWarrior/TalkifyTTS)** | 多云引擎连接器，支持微软/腾讯/阿里等云端服务，提供流式合成与系统集成，适合高音质或频换云厂商场景 |

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

**Q: 遇到朗读中途卡住或失败怎么办？**  
A: 服务内置了熔断重置机制。单句超时（15秒）会自动强行打断清空，若连续失败 3 次，服务器会自动释放并重建全新的 TTS 引擎实例。也可在「日志」页面复制或导出详细错误堆栈查看原因。

**Q: 如何导出崩溃或异常诊断日志？**  
A: 打开 App 内的「日志」选项卡，点击右上角的复制或分享图标，即可一键生成并导出包含设备信息、未捕获崩溃堆栈与历史合成错误的诊断报告。

---

<div align="center">

如果这个项目对你有帮助，欢迎 ⭐ Star 支持

</div>
