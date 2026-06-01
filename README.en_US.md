# ScreenTrans

[简体中文](README.md) | [English](README.en_US.md) | [日本語](README.ja_JP.md)

[![Android Build](https://github.com/longipinnatus/screentrans/actions/workflows/android-build.yml/badge.svg)](https://github.com/longipinnatus/screentrans/actions/workflows/android-build.yml) ![GitHub all releases](https://img.shields.io/github/downloads/longipinnatus/ScreenTrans/total)

An open-source Android OCR Screen Translator with customizable LLM API integration.

> [!IMPORTANT]  
> **This project is in its early stages. Some features, error handling, documentation, and usage instructions are not yet complete.**
> 
> **During the development of this project, AI-assisted programming techniques were used. Although every effort has been made to ensure the correctness and stability of the code, there is no guarantee that it is entirely free of errors or defects. In the event that any bugs in this project lead to abnormal calls to the API, misuse, data breaches, service interruptions, or any other direct or indirect losses or consequences, I (and the project contributors) shall not be held liable. You, as the user of this project and its associated APIs, should assess the risks on your own and bear all potential consequences arising from such use.**


## Download

You can download the latest version from [GitHub Releases](https://github.com/longipinnatus/screentrans/releases/latest).

For most Android devices, just use [![Download APK](https://img.shields.io/badge/Download-APK%20(arm64--v8a)-brightgreen?style=flat-square&logo=android)](https://github.com/longipinnatus/ScreenTrans/releases/latest/download/app-arm64-v8a-release.apk).


## Project Features

* **Multi-model Support**: Built-in lightweight PaddleOCR models (Simplified/Traditional Chinese, English, Japanese), plus support for custom PaddleOCR ONNX models for high-accuracy OCR or language-specific adaptation (in progress).
* **Tunable Model Parameters**: OCR model parameters can be adjusted to improve recognition rates in specific scenarios.
* **Vertical Text Support**: Supports vertical text recognition for Chinese and Japanese, useful for manga, novels, and classical texts.
* **Landscape/Portrait Adaptation**: Recognition remains available when switching between landscape and portrait modes.
---
* **Standard API Integration**: Supports OpenAI-compatible API protocol with custom endpoints.
* **Streaming Output**: No need to wait for the full response when processing multiple text boxes, reducing wait time.
* **Custom Translation Style**: Prompt is configurable, so users can tune translation style and add a glossary for different scenarios.
* **API Usage Metrics**: Built-in token usage tracking and billing estimation for real-time API cost awareness.
---
* **Custom Result Filtering**: Automatically filters irrelevant text by textbox size and regular expressions (such as page numbers and watermarks).
* **Auto Copy to Clipboard**: Recognized text can be copied automatically, with selectable copy mode (source only, translation only, or source + translation).
---
* **Custom Display Fonts**: Supports system fonts and importing external TTF/OTF font files.
* **UI Transparency Control**: Both overlay textboxes and the floating button support adjustable transparency.
* **Auto-hide Translation**: Translated overlays can hide automatically after a countdown, or be set to manual close mode.
* **Adaptive Colors**: Detects background colors and blends translation overlays with the source background.


## How to Use

The DeepSeek API parameters are preset by default; you only need to enter your API Key in the settings to start translating.

The program runs in region selection mode by default, controlled via a floating button:

* Region Selection (default):
  * Single-click the floating button: Activates free box selection. Drag on the screen to draw any rectangular area; release to instantly recognize and translate.
  * Double-click the floating button: Activates vertical range selection. The selection automatically spans the full width; simply swipe up or down to adjust the vertical range. Ideal for reading long paragraphs or conversations.
  * Cancel selection: If the drawn area is very small after releasing, the recognition and translation will be canceled.

* Full-Screen Translation Mode (switchable in settings):
  * Single-click the floating button: Recognizes and translates the entire current screen. (If you find that many text blocks are incorrectly merged, you can turn off the "Settings -> OCR -> Merge Text Boxes" option.)

Once triggered, the OCR recognition results are displayed first. When the translated text is obtained, the translation results will appear as an overlay, covering the original text. Tap the screen to close the overlay.

If the screen is turned off or rotated, the MediaProjection permission need to be re-requested. If the permission is invalid when clicking or double-clicking the floating button, a permission request will pop up in the current screen.


## Screenshots

Game: IDOLY PRIDE (アイプラ), filtering of certain texts has been enabled.

| Main Activity | Region Select | Translated 1 | Translated 2 |
| :---: | :---: | :---: | :---: |
| <img src="images/demo_main_activity.jpg" width="200"> | <img src="images/demo_region_select.jpg" width="200"> | <img src="images/demo_region_select_translated_1.jpg" width="200"> | <img src="images/demo_region_select_translated_2.jpg" width="200"> |


## Permission Requirements

Required Permissions:

* Floating Window Permission: Necessary for covering the original text. (If unable to open, you may need to click "Remove All Permission Restrictions")

* Screen Recording Permission: Used to capture screenshots for OCR recognition.

Recommended Permissions:

* Notification Permission: Enables stable background Toast notifications once granted.

* Clipboard Write Permission: On some customized ROMs, needs to be set to "Always allow".

* Background Pop-up Permission: Since the system automatically revokes screen recording permission when the screen is off, enabling this allows you to conveniently re-request screen recording permission.

| Permission Settings | Special Permission Settings |
| :---: | :---: |
| <img src="images/permission_config.jpg" width="300"> | <img src="images/permission_special.jpg" width="300"> |
