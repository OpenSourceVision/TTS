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

- **🚀 流式音频合成** — 基于 Ktor CIO 高性能网络引擎，支持 WAV 音频流式实时推流
- **🤖 引擎故障自愈** — 支持超时自动打断（15s）与连续失败（3次）引擎自动初始化重置
- **🔤 智能标点分句** — 按标点自然切分段落，显著提升首包响应速度与朗读流畅度
- **🔠 发音修正规则** — 支持自定义文本替换与多音字纠错映射
- **🩺 全盘日志诊断** — 自动记录请求明细与崩溃堆栈，支持一键复制与报告导出
- **🔋 稳定后台保活** — 前台服务结合电池优化白名单，保障锁屏长时间稳定朗读

---

## 🚀 API 配置指南

### 1.服务开启后，默认通过本地 `http://127.0.0.1:8080/tts` 接收请求（支持 GET 与 POST）。



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
