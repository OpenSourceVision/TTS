<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# TTS 转发器

一个 Android 本地 TTS（语音合成）转发服务，将手机系统内已安装的 TTS 引擎封装成一个本机 HTTP 接口，方便 **阅读 / Legado** 等小说阅读 App 直接调用进行朗读。

## ✨ 功能特性

- **本地 HTTP TTS 服务**：在手机后台启动一个轻量 HTTP 服务器（默认端口 `8080`），通过 `/tts` 或 `/api/tts` 接口接收文本并返回合成后的 WAV 音频流。
- **系统引擎自由切换**：自动扫描设备上所有已安装的 TTS 引擎（如讯飞、华为、Google TTS 等），可在「主页」一键切换目标合成引擎。
- **语速 / 语调参数兼容**：自动识别并归一化多种常见参数命名（`rate`/`speed`/`speakSpeed`、`pitch`/`speakPitch` 等），兼容不同阅读 App 的请求格式。
- **中文智能识别**：自动检测文本是否包含中文字符，自动切换为简体中文语音，无中文环境时回退到系统默认语言。
- **阅读 App 一键联动**：
  - 一键「复制配置」生成 Legado/阅读 App 所需的 HTTP TTS JSON 配置到剪贴板；
  - 一键「导入阅读」通过 `legado://` / `yuedu://` scheme 直接唤起阅读 App 完成导入，失败时自动降级为复制配置。
- **测试朗读**：在主页输入任意文本即可直接试听当前所选引擎、语速、语调的合成效果。
- **运行日志**：记录每一次合成请求的文本摘要、使用引擎、耗时、成功/失败状态，便于排查问题。
- **常驻后台保障**：
  - 前台服务 + 通知栏快捷停止按钮，避免被系统后台杀掉；
  - 电池优化白名单一键引导设置，保障朗读服务长时间稳定运行。
- **缓存管理**：可视化展示语音临时缓存占用大小，支持一键清理。
- **基础设置**：支持浅色/深色/跟随系统主题切换，以及监听端口自定义。

## 🧩 工作原理

```
阅读 App（Legado/阅读）
        │  HTTP 请求 (text, rate, pitch, engine...)
        ▼
TTS 转发器（本机 HTTP 服务，默认 127.0.0.1:8080）
        │  调用 Android TextToSpeech 引擎合成
        ▼
返回 audio/wav 音频流
```

## 🚀 本地运行

**环境要求：** [Android Studio](https://developer.android.com/studio)

1. 打开 Android Studio；
2. 选择 **Open**，并选中本项目所在目录；
3. 等待 Android Studio 自动修复依赖与配置；
4. 在项目根目录创建 `.env` 文件，并设置 `GEMINI_API_KEY`（参考 `.env.example`）；
5. 移除 `app/build.gradle.kts` 中的这一行：`signingConfig = signingConfigs.getByName("debugConfig")`；
6. 在模拟器或真机上运行该应用。

## 📱 使用方法

1. 打开 App，在「主页」选择目标 TTS 引擎，并确认监听端口；
2. 打开服务开关，启动本机 TTS 转发服务；
3. 点击「复制配置」或「导入阅读」，将朗读引擎接入阅读类 App；
4. 在阅读 App 中即可使用本机引擎进行小说朗读。

## 🙏 致谢

感谢 [Google AI Studio](https://ai.studio/) 提供的开发支持。
