# 🎧 Android TTS 转发服务器 (TTS HTTP Forwarder)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-purple.svg)](https://kotlinlang.org)

**Android TTS 转发服务器** 是一款专门为 Android 平台打造的**高能、轻量、本地化 TTS (Text-to-Speech) 音频流转发工具**。

通过本应用，你可以将 Android 手机上安装的任意 TTS 引擎（如谷歌文字转语音、微软 TTS、小爱同学、华为 TTS 等）转换为标准的 **HTTP 音频流服务器**。使得第三方软件（如最受欢迎的网络小说阅读器 **「阅读」App (Legado)**、跨平台阅读客户端等）可以直接以标准 HTTP 语音引擎接口的方式，调用你手机上的高质量本地 TTS 引擎，极速生成流式 `.wav` 音频并实现自然朗读。

---

## 🎯 为什么需要它？

在移动阅读或视障辅助场景中，原生 TTS 的直接调用往往受到平台和应用限制。而通过 HTTP 转发，可以获得以下巨大优势：
1. **解除隔离**：任意能够发送 HTTP 请求的客户端（甚至局域网内的电脑、iPad）都可以直接调用你 Android 手机中的原生 TTS 进行朗读。
2. **完美定制**：利用内置的**智能多音字与文本替换规则引擎**，你可以修正任何方言、小说角色名字的多音字错误（如将“行（xing）行（hang）好”精准修正）。
3. **极速顺畅**：相比线上云端付费 TTS，本地合成完全免费、零网络延迟，配合**流式输出 (Streaming Output)**，即点即播，畅快听书。

---

## ✨ 核心特色功能

### 🚀 1. 引擎一键锁定与极速转发
*   **引擎统合**：智能扫描系统内所有已安装的 TTS 引擎，一键切换并锁定。
*   **WAV 流式响应**：基于轻量级 Socket 的高性能 HTTP 服务器，采用实时分片机制流式传输 `.wav` 格式音频，让朗读几乎“零延迟”起播。
*   **端口自定义**：支持自由配置 HTTP 监听端口，避免局域网冲突。

### 📝 2. 智能多音字与正则替换规则 (Polyphone Rules)
*   **前向/后向正则匹配**：独创基于上下文关联的匹配模式（如：只有当“行”后面紧跟“好”时才替换为“xing”），解决复杂多音字痛点。
*   **双重编译缓存**：
    *   **文本替换缓存 (`RuleCache`)**：对高频重复词汇和整段替换结果进行二级缓存。
    *   **正则编译缓存 (`RulePatternCache`)**：自动对复杂的匹配规则正则进行预编译和内存级缓存，规避高频 GC 和 CPU 开销。
*   **防误触鲁棒过滤**：在用户配置 `matchWord` 匹配词（如使用 `|` 串联）时，自动进行 `trim()` 噪点过滤和“空值滤除”，彻底杜绝不小心输入多余字符导致系统陷入“处处生效/全字匹配”的系统异常。

### 🛡️ 3. 高并发限流与高载安全保护
*   **协程信号量保护 (`Semaphore`)**：针对短时间内爆发的并发请求，采用轻量级协程信号量（默认限制并发为 4），从源头控制 TTS 引擎高负载，避免出现暴音、重音或服务崩溃。
*   **超大请求防爆破 (Max 64KB)**：严格校验 HTTP 报文长度，对于超过 `64KB`（约合几万字）的异常或攻击性超长文本直接拦截，保护移动端系统内存安全。

### 🔄 4. 极致平滑的用户体验与稳定性
*   **Android 12+ 前台安全启动**：针对现代 Android 系统的严格限制，精细重构服务启停逻辑。只有在确需提升至前台启动时才调用 `startForegroundService`，彻底消除了关闭服务时概率触发的 `ForegroundServiceDidNotStartInTimeException` 崩溃异常。
*   **热重构协程锁 (`settingsJob`)**：当用户在 UI 界面快速反复开关服务、或者后台服务被系统短暂挂起重建时，会自动通过 `Job` 安全取消上一次的配置监听协程，杜绝并发热重启 HTTP Socket 导致的端口抢占和死锁风险。
*   **Room 数据库平滑演进**：手写 V1 至 V6 的完整增量 `Migration` 迁移配置。升级应用时，所有的自定义多音字库、设置项目、历史记录全部通过 `ALTER TABLE` 完美保留，坚决不使用粗暴的 `destructiveMigration` 抹除用户心血。
*   **电池白名单与后台防杀**：一键引导申请电池优化白名单，即使在锁屏状态下也能提供极其稳定、不中断的听书体验。
*   **音频缓存管理**：提供深度缓存扫描及一键清理功能，防止生成的大量临时合成波形文件占用宝贵的存储空间。

---

## 🔧 快速上手使用说明

### 第一步：启动转发服务
1. 打开应用，点击主界面的 **「启动服务」** 开关。
2. 状态栏将常驻一个前台通知，以保证服务不被系统轻易杀死。
3. 记录主界面显示的 **当前服务器地址**（例如：`http://192.168.1.100:8080/tts`）。

### 第二步：一键导入或手动配置至「阅读」App

#### 方法 A：一键导入 (推荐)
在应用主界面的快捷配置面板，点击 **「导入至阅读」**。应用会自动调用系统分享或通过 Schema 将配置直接注入到「阅读」App 的网络发音人列表中。

#### 方法 B：手动配置网络发音人
如果需要手动添加，可以复制以下 JSON 配置并粘贴至「阅读」App 的网络引擎配置中：

```json
{
  "id": "local_tts_forwarder",
  "name": "本地TTS转发",
  "url": "http://127.0.0.1:8080/tts?text={speakText}",
  "contentType": "audio/wav",
  "concurrentTasks": 1
}
```
*(注：如果「阅读」与转发器在同一部手机上运行，IP 填 `127.0.0.1` 即可；如果是局域网其他设备，请填写对应的手机局域网真实 IP。)*

---

## 🛠️ 开源技术架构

本项目的技术选型完全遵循现代 Android 最佳实践：

*   **开发语言**：100% Kotlin (支持协程及 Flow 响应式编程)
*   **UI 框架**：Jetpack Compose & Material Design 3 (支持动态色彩 System Theme / Dynamic Color)
*   **本地存储**：Room Database (手写 Migration 机制实现平滑演进)
*   **并发调度**：Kotlin Coroutines (StateFlow, Mutex, Semaphore) & Jetpack lifecycle-aware 作用域收集
*   **异步网络**：底层采用轻量级、零依赖的高性能 Socket 实现，轻量、敏捷，不额外引入大型 Web 服务框架，极大压缩 APK 体积。

---

## 🤝 参与贡献与开发

如果你发现了任何 Bug，或者有更好的多音字匹配规则优化想法，欢迎提交 Issue 或 Pull Request！

1. **多音字匹配代码**：可以参见 `com.example.data.TextRuleProcessor`
2. **转发核心服务**：主要位于 `com.example.service.TtsServerService`

---

## 📄 开源许可证

```text
Copyright 2026 TTS Forwarder Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
