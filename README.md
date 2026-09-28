# AudioMemory

[![CI](https://github.com/Enchore/AudioMemory/actions/workflows/ci.yml/badge.svg)](https://github.com/Enchore/AudioMemory/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

> 录音转文字、识别说话人、再交给大模型提炼成结构化记忆的 Android 应用

## 项目简介

AudioMemory 是一个 Android 端的"对话记忆"应用。它在后台持续录音，把音频切成片段后转写成文字，识别每一段是谁说的，最后调用大模型从对话中提炼出结构化的记忆条目（含标签、说话人、置信度），存入本地数据库供检索。

所有数据留在设备本地的 Room 数据库中；只有调用云端转写或大模型接口时才会联网，且端点与密钥均可自行配置。

当前版本：`0.1.0-mvp`

## 处理流水线

音频片段进入流水线后，由 WorkManager 串联四个 Worker 依次处理：

```
录音（前台服务）
   │
   ▼
AudioChunk ──▶ TranscribeWorker     转写：sherpa-onnx 端侧 Whisper / OpenAI 云端
   │
   ▼
             DiarizeWorker          说话人分离：提取声纹并与已知说话人匹配
   │
   ▼
             SummarizeWorker        大模型提炼：结构化记忆条目 + 更新主人画像
   │
   ▼
             CleanupWorker          清理已处理的临时音频
```

`PipelineOrchestrator` 负责编排这条链路，每一步的状态写回 `audio_chunks.processing_status`，中断后可续跑。

## 功能特性

- **后台持续录音**：前台服务保活，支持唤醒锁，通知栏显示录制状态
- **双引擎转写**：端侧 sherpa-onnx Whisper（tiny 模型，离线可用）或 OpenAI 云端 Whisper，可在设置中切换
- **说话人分离与识别**：提取声纹特征，与已注册的说话人做相似度匹配，可标记设备主人
- **结构化记忆提取**：调用任意 OpenAI 兼容接口，把对话提炼为带标签的记忆条目
- **记忆检索**：Room + FTS 全文搜索，支持按标签筛选
- **主人画像**：随记忆积累持续更新对设备主人的认知（`owner_profile` 表）
- **中英双语界面**：界面语言可切换
- **可插拔 AI 配置**：LLM 与 Whisper 的 base URL、模型名、密钥均可自定义，兼容 OpenAI / DeepSeek / Groq / 本地部署

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| 界面 | Jetpack Compose + Material 3 |
| 导航 | Navigation Compose |
| 数据库 | Room 2.6（含 FTS 全文检索） |
| 依赖注入 | Hilt |
| 后台任务 | WorkManager + Hilt Work |
| 网络 | Retrofit + OkHttp |
| 配置存储 | DataStore Preferences |
| 端侧推理 | sherpa-onnx（源码集成 + JNI） |

最低支持 Android 8.0（API 26），目标 SDK 35，JDK 17。

## 项目结构

```
app/src/main/java/com/audiomemory/
├── ui/
│   ├── MainActivity.kt               # 唯一 Activity，承载 Compose 导航
│   ├── navigation/Screen.kt          # 5 个目的地：首页/录音/记忆/说话人/设置
│   ├── home/                         # 首页与概览
│   ├── recording/                    # 录音界面
│   ├── memory/                       # 记忆浏览与检索
│   ├── speaker/                      # 说话人管理
│   └── settings/                     # API 配置与偏好设置
├── service/
│   ├── recording/AudioRecordingService.kt   # 前台录音服务
│   └── processing/
│       ├── PipelineWorkers.kt        # 四个 Worker + 编排器
│       └── GptMemoryExtractor.kt     # 大模型记忆提取
├── ml/
│   ├── transcription/                # sherpa-onnx / OpenAI Whisper 转写
│   ├── diarization/                  # 说话人分离
│   └── speaker/SpeakerMatcher.kt     # 声纹匹配
├── data/
│   ├── AudioMemoryDatabase.kt        # Room 数据库
│   ├── entity/Entities.kt            # 9 张表
│   ├── dao/Daos.kt
│   └── repository/MemoryRepository.kt
├── di/AppModule.kt                   # Hilt 模块
└── util/ApiConfig.kt                 # API 配置（DataStore）

app/src/main/java/com/k2fsa/sherpa/onnx/    # sherpa-onnx Kotlin API 源码
```

## 数据模型

| 表 | 用途 |
|----|------|
| `recording_sessions` | 录音会话 |
| `audio_chunks` | 音频分片与处理状态 |
| `transcriptions` | 转写结果与所用引擎 |
| `speakers` | 说话人档案 |
| `speaker_segments` | 片段级说话人归属 |
| `memories` | 结构化记忆条目 |
| `memory_tags` / `memory_tag_cross_ref` | 标签与多对多关联 |
| `owner_profile` | 主人画像 |
| `memories_fts` | 记忆全文检索索引 |

## 构建与运行

```bash
git clone https://github.com/Enchore/AudioMemory.git
cd AudioMemory

# 命令行构建
./gradlew assembleDebug

# 或直接用 Android Studio 打开项目根目录
```

首次运行需授予录音与通知权限。若使用云端转写或大模型提炼，请在「设置」中填写对应的 base URL、模型名与 API 密钥——这些值保存在 DataStore 中，不会写入版本库。

若仅使用端侧 sherpa-onnx 转写，则无需配置任何密钥，但需自备 JNI 库（`src/main/jniLibs/`）。

## 权限说明

| 权限 | 用途 |
|------|------|
| `RECORD_AUDIO` | 录音 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE` | 后台持续录音 |
| `POST_NOTIFICATIONS` | 录音状态通知 |
| `INTERNET` / `ACCESS_NETWORK_STATE` | 云端转写与大模型调用 |
| `WAKE_LOCK` | 处理期间保持唤醒 |

## 当前边界

- 版本为 MVP（`0.1.0-mvp`），部分流程尚未打磨
- 仓库中**未包含** sherpa-onnx 的 JNI 原生库（`src/main/jniLibs/`），需自行构建并放入，否则端侧转写不可用
- 仅包含一个插桩测试样例，无完整测试覆盖，无 CI
- `app/schemas/` 中导出的是 Room v1 schema

## 许可证

本项目基于 [MIT License](LICENSE) 开源。
