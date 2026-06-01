# ScreenTrans

[简体中文](README.md) | [English](README.en_US.md) | [日本語](README.ja_JP.md)

[![Android Build](https://github.com/longipinnatus/screentrans/actions/workflows/android-build.yml/badge.svg)](https://github.com/longipinnatus/screentrans/actions/workflows/android-build.yml) ![GitHub all releases](https://img.shields.io/github/downloads/longipinnatus/ScreenTrans/total)

一款支持自定义大模型 API 接口的开源 Android 屏幕 OCR 实时翻译工具。

> [!IMPORTANT]
> **该项目尚处于早期阶段。部分功能、错误处理、文档以及使用说明仍未完善。**
>
> **本项目在开发过程中使用了 AI 辅助编程技术。尽管已尽力确保代码的正确性与稳定性，但无法保证完全没有错误或缺陷。如因本项目存在的任何 Bug 导致 API 被异常调用、滥用、数据泄露、服务中断或其他任何直接或间接的损失或后果，本人（及项目贡献者）不承担任何责任。您在使用本项目及相关 API 时，应自行评估风险，并承担由此可能带来的一切后果。**


## 下载

可以在 [GitHub Releases](https://github.com/longipinnatus/screentrans/releases/latest) 获取最新版本。

一般的 Android 设备选择 [![Download APK](https://img.shields.io/badge/Download-APK%20(arm64--v8a)-brightgreen?style=flat-square&logo=android)](https://github.com/longipinnatus/ScreenTrans/releases/latest/download/app-arm64-v8a-release.apk) 就可以了。


## 项目特点

* **多模型支持**：内置 PaddleOCR 轻量化 OCR 模型（支持简/繁中、英、日），并支持加载自定义 PaddleOCR ONNX 模型，满足高精度识别或特定语言适配需求（正在适配中）。
* **可微调模型参数**：提供 OCR 模型参数调节功能，提升特定场景下的识别率。
* **支持竖排文本**：支持中日文竖排文本识别，契合漫画、小说及古籍的阅读场景。
* **适配横竖屏**：设备在横竖屏模式间切换后不影响识别功能。
---
* **标准 API 接入**：支持 OpenAI API 协议接口，兼容自定义 Endpoint。
* **支持流式输出**：在处理多个文本框时无需等待整体响应，减少等待时间。
* **自定义翻译风格**：支持用户修改 Prompt，可根据场景调整翻译风格、添加术语表。
* **API 用量统计**：内置 Token 使用统计与计费功能，实时估计 API 使用成本。
---
* **自定义过滤结果**：支持基于文本框尺寸及正则表达式自动过滤无关文本（页码、水印等）。
* **自动复制到剪贴板**：支持自动复制识别结果，可选择复制内容（仅原文、仅译文、原译文对照）。
---
* **自定义显示字体**：支持选择系统内置字体，也可导入外部 TTF/OTF 字体文件。
* **UI 透明度调节**：覆盖文本框与悬浮球透明度均可调节。
* **自动隐藏翻译**：支持翻译结果倒计时后自动隐藏，也可以选择手动关闭模式。
* **自适应颜色**：自动识别背景颜色，使翻译文本框颜色与原背景融合。


## 如何使用

默认已经设置好 DeepSeek API 的参数，只需要在设置里输入 API Key 就可以翻译了。

程序默认运行在区域选择模式下，通过悬浮球进行操作：

* 区域选择（默认设置）：
  * 单击悬浮球：开启自由框选。在屏幕上拖动划出任意矩形区域，松手后立即识别翻译。
  * 双击悬浮球：开启垂直范围选择。此时自动覆盖全宽，只需上下滑动确定纵向范围，适合阅读长篇段落或对话。
  * 取消选择：在松手之后，如果划定的区域非常小，将取消本次识别翻译操作。

* 全屏翻译模式（可在设置中切换）：
  * 单击悬浮球：识别并翻译当前整个屏幕的内容（如果发现很多文本被错误地合并在一起了，可以关闭“设置 -> OCR -> 合并文本框”选项）。

触发识别后首先会展示 OCR 识别的结果。当获取到翻译文本后，翻译结果将以悬浮层的形式覆盖在原文字位置，此时点击屏幕即可关闭。

当熄屏或者屏幕旋转后，屏幕录制权限需要重新申请。当点击或双击悬浮球时，如果权限失效，会在当前界面弹出授权申请。


## 界面展示

游戏：偶像荣耀（アイプラ，IDOLY PRIDE），已设置过滤部分文字。

| 主界面 | 区域选择 | 翻译示例 1 | 翻译示例 2 |
| :---: | :---: | :---: | :---: |
| <img src="images/demo_main_activity.jpg" width="200"> | <img src="images/demo_region_select.jpg" width="200"> | <img src="images/demo_region_select_translated_1.jpg" width="200"> | <img src="images/demo_region_select_translated_2.jpg" width="200"> |


## 权限要求

必须开启的权限：

* 悬浮窗权限：覆盖原文翻译所必需（如果无法打开，可能需要点击“解除所有权限限制”）
* 屏幕录制权限：用于截取屏幕图片进行 OCR 识别

推荐开启的权限：

* 通知权限：开启后可以稳定地通过后台 Toast 通知；
* 写入剪贴版权限：在一些定制系统里，需要设置为“始终允许”；
* 后台弹出界面权限：由于熄屏后系统会自动收回录屏权限，启用后可以方便地重新请求录屏权限；

| 权限设置 | 其他特殊权限 |
| :---: | :---: |
| <img src="images/permission_config.jpg" width="300"> | <img src="images/permission_special.jpg" width="300"> |
