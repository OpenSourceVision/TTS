<div align="center">

#  Android TTS 转发服务器

将 Android 手机本地的语音引擎转换为标准的 **HTTP 音频流接口**，供第三方应用进行流式语音合成。

[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-7.4%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

</div>

---

## ✨ 核心特性

- **🚀 极速流式响应** — 点击朗读后立即播放，支持随时打断
- **🔤 智能发音纠正** — 多音字纠错与自定义文本替换
- **🛡️ 防错自动清洗** — 配置规则时自动净化多余空格和分隔符
- **⚡ 稳定防崩保护** — 智能排队处理，拦截垃圾超长文本
- **🔒 规则安全保存** — 应用更新时自动保留所有配置
- **🔋 省心省电优化** — 电池白名单、缓存自动清理
- **🎯 丝滑后台体验** — 锁屏后台持续朗读不中断

---

## 🚀 快速开始

### 1. 启动服务

1. 打开 App 主界面，开启「**启动服务**」开关
2. 获取服务地址（例如：`http://127.0.0.1:8080/tts`）

### 2. 对接第三方应用

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

## 🔧 推荐 TTS 引擎

| 项目 | 特点 |
|---|---|
| [CloneTTS](https://github.com/sipeter/CloneTTS) | 离线原生 TTS，支持 1–3 秒音色克隆、注册为系统引擎并提供本地 HTTP API，适合完全离线或注重隐私的设备端合成 |
| [TalkifyTTS](https://github.com/LonePheasantWarrior/TalkifyTTS) | 多云引擎连接器，支持微软/腾讯/阿里等云端服务，提供流式合成与系统集成，适合需要高音质或频繁切换云厂商的场景 |

---

## 🛠️ 技术栈

| 技术 | 说明 |
|---|---|
| 语言 | Kotlin 100% |
| UI 框架 | Jetpack Compose + Material 3 |
| 数据库 | Android Room ORM |
| 网络 | 原生 Socket（轻量无依赖） |
| 最小 SDK | Android 7.4+（API 24） |

---

## 💡 常见问题

**Q: 支持哪些 Android 版本？**
A: 最低 Android 7.4（API 24），推荐 Android 10+

**Q: 服务会占用大量流量吗？**
A: 否，HTTP 服务仅在本地运行，无云端上传

---

<div align="center">

如果这个项目对你有帮助，欢迎 ⭐ Star 支持

</div>
