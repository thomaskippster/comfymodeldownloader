# ComfyUI Model Download Manager

A robust, feature-rich Java Swing application designed to automate the process of identifying and downloading missing models for ComfyUI workflows. It bridges the gap between sharing a JSON workflow and having a fully functional local setup.

## 🚀 Key Features

### 🔍 Advanced JSON Parsing
- **Recursive Traversal**: Scans deeply nested structures, including subgraphs and custom node definitions.
- **Filename Detection**: Uses smart regex to find model files (`.safetensors`, `.ckpt`, `.pt`, etc.) inside `widgets_values`, `inputs`, and other string fields.
- **Metadata Support**: Automatically detects and prioritizes embedded model metadata (standard `models` array used by ComfyUI managers).

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

## 🏗️ Installation & Build

1. Clone the repository:
   ```bash
   git clone https://github.com/thomaskippster/comfymodeldownloader.git
   cd comfymodeldownloader
   ```

2. Build the "Fat JAR" using Maven:
   ```bash
   mvn clean package
   ```

3. Locate the runnable JAR in the `target/` directory:
   `comfymodeldownloader-1.0-SNAPSHOT.jar`

## 📖 How to Use

1. **Launch**: Start the application via `java -jar target/comfymodeldownloader-1.0-SNAPSHOT.jar`.
2. **Setup Path**: Enter or browse to your local ComfyUI models directory (e.g., `C:\ComfyUI\models`).
3. **Provide Workflow**:
    - **Paste**: Directly paste the content of your ComfyUI JSON file into the text area.
    - **Load**: Use the "Load File..." button to select a `.json` file from your disk.
4. **Analyze**: Click **Analyze Models**. The table will populate with detected models and their types.
5. **Download**:
    - Select models using the checkboxes.
    - Click **Start Queue**.
    - If a file exists, choose to **Resume** or **Overwrite**.

## 📜 License
This project is licensed under the MIT License - see the LICENSE file for details (or just use it freely!).

---
*Developed to make ComfyUI workflow sharing seamless.*
