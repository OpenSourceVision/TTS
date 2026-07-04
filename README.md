# 🎧 Android TTS 转发服务器

> 将 Android 手机本地的 TTS (Text-to-Speech) 语音引擎转换为标准的 **HTTP 音频流接口**，供兼容的第三方应用进行流式语音合成。

---

## ✨ 核心特性

- **🚀 极速流式响应**：点击朗读后立即播放，支持随时打断并快速跟进
- **🔤 智能发音纠正**：支持多音字纠错与自定义文本替换，精准修正小说朗读的错误发音
- **🛡️ 防错自动清洗**：配置规则时自动净化多余空格、回车和分隔符，防止规则配置错误
- **⚡ 稳定防崩保护**：智能排队处理大量连续请求，拦截垃圾超长文本，确保稳定播放
- **🔒 规则安全保存**：应用更新时自动保留所有配置规则、系统设置和历史记录
- **🔋 省心省电优化**：一键加入电池白名单，内置音频缓存自动清理，降低功耗
- **🎯 丝滑后台体验**：
  - 锁屏后台持续朗读不中断
  - 热开关防冲突，避免端口重用问题

---

## 🚀 快速开始

### 前置要求

- Android 7.4+ (API Level 24)
- Kotlin 1.9+
- Gradle 8.0+

### 启动服务

1. 在 App 主界面打开 **「启动服务」** 开关
2. 获取当前服务器监听地址（例如：`http://127.0.0.1:8080/tts`）

### 对接第三方应用

在兼容的第三方客户端中添加网络语音引擎配置（JSON 格式）：

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

## 🏗️ 项目结构

```
app/
  src/
    main/
      java/com/example/
        ├── MainActivity.kt           # 应用主入口 & UI 主页面
        ├── data/                     # 数据层
        │   ├── 数据库实体
        │   └── 存储库实现
        ├── service/                  # 业务逻辑层
        │   └── TTS 转发服务实现
        ├── ui/                       # Compose UI 组件
        │   └── Material 3 界面
        └── viewmodel/                # 视图模型层
            └── UI 状态管理
      res/
        ├── values/                   # 资源配置
        └── drawable/                 # 图片资源
      AndroidManifest.xml             # 应用清单

gradle/                               # Gradle 版本管理
build.gradle.kts                      # 项目级构建配置
settings.gradle.kts                   # 项目设置
gradle.properties                     # Gradle 属性配置
.env.example                          # 环境变量示例
```

### 架构说明

该应用采用 **MVVM + 单一职责** 架构：

- **UI 层** (Compose)：使用 Jetpack Compose 构建现代 Material 3 界面
- **ViewModel 层**：管理 UI 状态，独立于界面重建
- **Service 层**：实现 HTTP TTS 转发服务，使用原生 Socket 处理请求
- **Data 层**：Room 数据库管理发音规则、用户偏好和历史记录

---

## 🛠️ 技术栈

| 技术 | 说明 |
|------|------|
| **语言** | Kotlin 100% |
| **UI 框架** | Jetpack Compose + Material 3 |
| **数据库** | Android Room ORM |
| **网络** | Java/Kotlin 原生 Socket (无外部依赖) |
| **异步** | Kotlin Coroutines |
| **构建系统** | Gradle 8.0+ with KTS |
| **API 集成** | Firebase Gemini AI (可选) |

### 主要依赖

- `androidx.compose.*` - UI 组件库
- `androidx.room:room-*` - 本地数据库
- `androidx.lifecycle:*` - 生命周期管理
- `kotlinx.coroutines.*` - 异步编程
- `retrofit` + `okhttp` - HTTP 客户端
- `moshi` - JSON 序列化
- `firebase-ai` - Gemini API 集成

---

## 🔧 本地开发

### 克隆项目

```bash
git clone https://github.com/OpenSourceVision/TTS.git
cd TTS
```

### 环境配置

1. 复制环境变量文件：
   ```bash
   cp .env.example .env
   ```

2. 在 `.env` 中配置 Gemini API Key（可选）：
   ```dotenv
   GEMINI_API_KEY=your_api_key_here
   ```

3. 配置 Android Keystore（发布版本）：
   ```bash
   # 创建签名密钥
   keytool -genkey -v -keystore my-upload-key.jks -keyalg RSA \
     -keysize 2048 -validity 10000 -alias upload
   ```

### 构建和运行

```bash
# 构建 Debug 版本
./gradlew assembleDebug

# 运行单元测试
./gradlew test

# 运行 UI 测试
./gradlew connectedAndroidTest

# 构建 Release 版本
./gradlew assembleRelease
```

### IDE 建议

推荐使用 **Android Studio Koala (2024.1.1)** 或更新版本

---

## 📋 配置项说明

### build.gradle.kts 关键配置

```kotlin
android {
  namespace = "com.example"
  compileSdk = 36              // 最新编译 SDK
  
  defaultConfig {
    applicationId = "cn.tts.app"
    minSdk = 24                // 支持 Android 7.4+
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }
}
```

### 签名配置

应用支持两套签名配置：

- **Debug**: 自动生成，用于开发测试
- **Release**: 从环境变量读取密钥信息

---

## 🔐 安全性

- ✅ 应用使用 ProGuard 混淆保护代码
- ✅ HTTP 服务仅监听本地接口
- ✅ 规则配置本地存储，无上传
- ✅ 支持 Firebase App Check 防滥用

---

## 📊 项目统计

- **语言**：Kotlin (100%)
- **最小 SDK**：Android 7.4 (API 24)
- **目标 SDK**：Android 15 (API 36)
- **Gradle 版本**：8.0+

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

---

## 📄 许可证

本项目暂未声明许可证。详见 [LICENSE](LICENSE) 文件。

---

## 💡 常见问题

**Q: 如何连接到远程手机？**  
A: 修改 HTTP 监听地址从 `127.0.0.1` 为 `0.0.0.0`，确保防火墙允许 8080 端口访问。

**Q: 支持哪些 Android 版本？**  
A: 最低 Android 7.4 (API Level 24)，推荐 Android 10+ 获得最佳体验。

**Q: 如何调试发音规则？**  
A: App 主界面提供规则编辑器，支持实时预览效果。

**Q: 服务会占用大量流量吗？**  
A: 否。HTTP 服务仅在局域网运行，无任何云端上传。

---

## 📞 联系方式

如有问题，请通过以下方式联系：
- 提交 GitHub Issue
- 查看项目讨论区

**项目来源**：基于 Google AI Studio 仓库模板
