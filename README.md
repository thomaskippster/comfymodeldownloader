# ComfyUI Model Downloader 🚀

Stop wasting time manually moving files! This tool automates your model management so you can stay in the creative flow. It analyzes your workflows, identifies missing models, and downloads them directly into the correct ComfyUI directories.

## ✨ Features

*   **🚀 1-Click Workflow Import:** Send workflows directly from ComfyUI to the downloader via the integrated Bridge.
*   **🧠 AI-Powered Analysis:** Uses Gemini AI (or local AI) to intelligently identify models and find download links even if metadata is missing.
*   **📦 Archive Manager:** Keep your models directory clean! Move rarely used models to an archive and restore them instantly when a workflow requires them.
*   **👯 Storage Optimizer:** Detects duplicate models using SHA-256 hashing to save disk space.
*   **🔍 Corrupted File Detection:** Validates models (Safetensors, etc.) to ensure they aren't corrupted or incomplete.
*   **🌐 Online Search:** Automatically searches CivitAI, HuggingFace, and other sources for missing models.
*   **🛡️ Vault Security:** Encrypted storage for your sensitive API keys and tokens.
*   **📉 Smart Queue:** Multi-threaded downloading with resume support and low disk space warnings.
*   **🌗 ComfyUI Aesthetic:** A modern dark UI that matches the ComfyUI look and feel.
*   **💤 Automation:** Background mode (Tray) and optional system shutdown after queue completion.

## 🔌 ComfyUI Bridge Setup

The "Bridge" is a small extension for ComfyUI that adds a "Send to Downloader" button.

### Automatic Installation (Recommended)
1.  Open the Downloader App.
2.  Go to **Settings** -> **ComfyUI Bridge...**.
3.  Select your ComfyUI main directory and click **Start Installation**.
4.  Restart ComfyUI.

### Manual Installation
1.  Copy the folder `src/main/resources/comfyui-bridge` into your `ComfyUI/custom_nodes/` directory.
2.  Rename it to `comfyui-model-downloader`.
3.  Restart ComfyUI.

## 🚀 Getting Started

Choose one of the following three ways to install and run the application:

### 1. 📥 Direct Download (SourceForge) - Recommended for most users
The fastest way to get the latest stable version.
*   Download the latest release from [SourceForge](https://sourceforge.net/projects/comfymodeldownloader/).

### 2. ⚡ Quick Install (Pinokio)
If you use [Pinokio](https://pinokio.computer), you can install this app with one click:
1. Open Pinokio and click **Discover**.
2. Search for `ComfyUI Model Downloader`.
3. Click **Download** and then **Install**.

### 3. 🛠️ Manual Install (GitHub) - For developers
Download the source code and build it yourself.

**Prerequisites:**
*   **Java 17 or higher**
*   **Maven**

**Steps:**
1.  Clone the repository:
    ```bash
    git clone https://github.com/thomaskippster/comfymodeldownloader
    ```
2.  Build the application:
    ```bash
    mvn clean package
    ```
3. Run the application:
    ```bash
    java -jar target/ComfyUIModelDownloader.jar
    ```


## 📖 How to use

1.  **Configure Paths:** Go to **Settings -> Directories** and set your ComfyUI models path and an archive path.
2.  **Unlock Vault:** Set a password to encrypt your API keys.
3.  **Add Keys:** Under **Settings -> AI & API Keys**, add your Gemini API Key and HuggingFace Token for best results.
4.  **Analyze Workflow:**
    *   Drag & Drop a `.json` or `.png` file into the app.
    *   OR: Click the 🚀 icon in ComfyUI to send it automatically.
5.  **Review & Download:** The app lists all models. It checks if they already exist, are in the archive, or need to be downloaded.
6.  **Start Queue:** Hit "Start Queue". Archived models will be restored automatically, and missing ones will be downloaded.

---

### Support & Feedback
If you find this tool useful, please leave a star ⭐ on GitHub!
Feedback is highly appreciated – feel free to open an issue or reach out.
