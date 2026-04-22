# ComfyUI Model Download Manager

A robust, feature-rich Java Swing application designed to automate the process of identifying and downloading missing models for ComfyUI workflows. It bridges the gap between sharing a JSON workflow and having a fully functional local setup.

## 🚀 Key Features

### 🔍 Advanced Workflow Extraction
- **JSON & PNG Support**: Directly load `.json` workflows or drop ComfyUI-generated `.png` files to extract embedded workflow and prompt metadata (tEXt, iTXt, zTXt chunks).
- **Recursive Traversal**: Scans deeply nested structures, including subgraphs and custom node definitions.
- **Smart Detection**: Uses regex and node-type analysis to find model files (`.safetensors`, `.ckpt`, etc.) and infer their target directory (e.g., `checkpoints`, `loras`, `vae`).

### 🤖 AI-Powered Discovery
- **Local AI Heuristics**: Built-in scoring engine to predict model providers (e.g., StabilityAI, Black Forest Labs) offline based on filenames.
- **Gemini Deep Search**: Integrated "Deep Search" using the Google Gemini API to find official Hugging Face repositories and direct download links for obscure models.
- **Context Awareness**: Disambiguates generic filenames (like `ae.safetensors`) by analyzing the global workflow context (e.g., detecting FLUX or SD3 usage).

### 📥 Powerful Download Manager
- **Resume Capability**: Implements HTTP Range requests to continue interrupted downloads exactly where they left off.
- **Queue Control**: Start, **Pause**, **Resume**, and **Stop** functionality for full control over your bandwidth.
- **Overwrite Protection**: Interactive dialogs prevent accidental data loss by asking to Resume, Overwrite, or Skip existing files.
- **Live Metrics**: Real-time display of download speed (MB/s), percentage progress, and a global progress bar.

### 📂 Intelligent Sorting
- **Automatic Path Mapping**: Infers the correct subdirectory (e.g., `checkpoints`, `loras`, `vae`, `controlnet`) based on node types like `LoraLoader` or `VAELoader`.
- **Customizable Base Path**: Easily set your ComfyUI models root directory.

## 🛠️ Requirements
- **Java 11** or higher
- **Maven** (for building from source)
- **API Key (Optional)**: A Google Gemini API key for "Deep Search" functionality.

## 🏗️ Build & Run

1. **Build the Fat JAR**:
   ```bash
   mvn clean package
   ```

2. **Launch**:
   ```bash
   java -jar target/comfymodeldownloader-1.0-SNAPSHOT.jar
   ```

## 📖 How to Use

1. **Setup Path**: Enter or browse to your local ComfyUI models directory.
2. **Provide Workflow**: Paste JSON content, load a `.json` file, or load a `.png` with embedded metadata.
3. **Analyze**: Click **Analyze Models**. The application will identify missing models and categorize them.
4. **Deep Search**: For unknown models, use the "Deep Search" feature to let the AI find the source.
5. **Download**: Select models and click **Start Queue**.

## 📜 License
This project is licensed under the MIT License.

---
*Developed to make ComfyUI workflow sharing seamless.*
