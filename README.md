# 🎧 Android TTS 转发服务器

> 将 Android 手机本地的 TTS (Text-to-Speech) 语音引擎转换为标准的 **HTTP 音频流接口**，供第三方应用进行流式语音合成。

---

## ✨ 核心特性

- **🚀 极速流式响应** - 点击朗读后立即播放，支持随时打断
- **🔤 智能发音纠正** - 多音字纠错与自定义文本替换
- **🛡️ 防错自动清洗** - 配置规则时自动净化多余空格和分隔符
- **⚡ 稳定防崩保护** - 智能排队处理，拦截垃圾超长文本
- **🔒 规则安全保存** - 应用更新时自动保留所有配置
- **🔋 省心省电优化** - 电池白名单、缓存自动清理
- **🎯 丝滑后台体验** - 锁屏后台持续朗读不中断

---

## 🚀 快速开始

### 启动服务

1. 打开 App 主界面，开启 **「启动服务」** 开关
2. 获取服务地址（例如：`http://127.0.0.1:8080/tts`）

### 对接第三方应用

在兼容的第三方客户端中添加网络语音引擎配置：

```json
{
  "id": "local_tts_forwarder",
  "name": "本地TTS转发",
  "url": "http://127.0.0.1:8080/tts?text={speakText}",
  "contentType": "audio/wav",
  "concurrentTasks": 1
}
```

---

## 🛠️ 技术栈

| 技术 | 说明 |
|------|------|
| 语言 | Kotlin 100% |
| UI 框架 | Jetpack Compose + Material 3 |
| 数据库 | Android Room ORM |
| 网络 | 原生 Socket (轻量无依赖) |
| 最小 SDK | Android 7.4+ (API 24) |

---

## 💡 常见问题

**Q: 支持哪些 Android 版本？**  
A: 最低 Android 7.4 (API 24)，推荐 Android 10+

**Q: 服务会占用大量流量吗？**  
A: 否。HTTP 服务仅在本地运行，无云端上传

---

## 推荐的本地 TTS 引擎

以下为推荐用于本仓库（以本地/离线优先为目标）的开源或轻量级 Android TTS 项目：

- [sipeter/CloneTTS](https://github.com/sipeter/CloneTTS) — 面向 Android 的 TTS 转发/集成项目，可用于在设备端或局域网内提供合成后端，便于与本仓库的 HTTP 转发逻辑对接。

- [LonePheasantWarrior/TalkifyTTS](https://github.com/LonePheasantWarrior/TalkifyTTS) — 轻量的移动端 TTS 实现，适合快速集成与试验，与本项目结合可实现本地合成与转发场景。

### 简要集成建议

1. 抽象出 TTS Provider 接口（文本→音频），便于在不同引擎间切换。  
2. 优先使用本地引擎，若不可用可考虑回退到云端或自托管服务。  
3. 对常用/重复文本做音频缓存，减少合成频次与延迟。  
4. 确认合成音频格式（wav/mp3/ogg）与 Android 播放兼容性，必要时做转码或流式播放。

---

如果你希望，我可以继续：
- 把上述两个项目的 README 摘要抓取并合并到本 README 中；
- 为本仓库写一个示例实现（包含 TTS Provider 接口与对 CloneTTS/TalkifyTTS 的接入示例）。
