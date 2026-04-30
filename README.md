# ComfyUI Model Downloader 🚀

Stop wasting time manually moving files! This tool automates your model management so you can stay in the creative flow.

## ✨ Features
* **Automated Downloads:** Get your models directly into the right ComfyUI folders.
* **Organized Structure:** No more messy `models/` directory.
* **Fast & Lightweight:** Built for speed and minimal overhead.
* **Shutdown after Queue:** Automatically shut down your PC after long download sessions.
* **Background Mode:** Keep the downloader running in the system tray.
* **Vault Security:** Encrypted storage for your API keys (Gemini, Hugging Face).

## 🔌 ComfyUI Integration

To send workflows directly from ComfyUI to the downloader with a single click:

1. Copy or symlink the folder `comfyui-model-downloader` into your `ComfyUI/custom_nodes/` directory.
2. Restart ComfyUI.
3. You will now see a **🚀 Downloader** button in the ComfyUI menu (or under the download icon in the new UI v2).
4. Make sure the Java application is running to receive the workflow.

## 🚀 Getting Started

### Prerequisites
* Java 17 or higher
* Maven

### Build and Run
1. Clone this repo:
   ```bash
   git clone https://github.com/thomaskippster/comfymodeldownloader
   ```
2. Build the application:
   ```bash
   mvn clean package
   ```
3. Run the application:
   ```bash
   java -jar target/ComfyUIModelDownloader.jar
   ```

## 💡 Why use this?
Manually downloading GBs of data and navigating deep folder structures is tedious. This tool bridges the gap between finding a model and using it in your workflow instantly by analyzing your ComfyUI JSON or PNG files.

---

### Support & Feedback
If you find this tool useful, please leave a star ⭐ on GitHub!
Feedback is highly appreciated – feel free to open an issue or reach out.
