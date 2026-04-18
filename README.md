# ComfyUI Model Download Manager

A robust Java Swing application to identify and download missing models for ComfyUI workflows.

## Features
- **Advanced JSON Parsing**: Recursively scans ComfyUI JSON workflows (including subgraphs and `widgets_values`) for model filenames.
- **Model Metadata Support**: Detects and uses embedded model download metadata (URLs, directories).
- **Download Manager**:
    - **Resume Capability**: Uses HTTP Range requests to resume interrupted downloads.
    - **Pause/Resume/Stop**: Full control over the download queue.
    - **Overwrite Protection**: Prompts before overwriting existing local files.
    - **Real-time Status**: Shows download speed (MB/s) and per-file progress.
- **Intelligent Sorting**: Automatically infers the correct subfolder (e.g., `checkpoints`, `loras`, `vae`) based on node types.

## Requirements
- **Java 11 or higher**
- **Maven** (to build)

## How to Build
1. Clone the repository.
2. Run the following command:
   ```powershell
   mvn clean package
   ```
3. The runnable JAR will be created in the `target/` directory:
   `target/comfymodeldownloader-1.0-SNAPSHOT.jar`

## How to Use
1. Run the JAR:
   ```powershell
   java -jar target/comfymodeldownloader-1.0-SNAPSHOT.jar
   ```
2. Set your ComfyUI models directory path (e.g., `C:\ComfyUI\models`).
3. Paste a ComfyUI JSON workflow or load a `.json` file.
4. Click **Analyze Models**.
5. Select the models you want to download and click **Start Queue**.

## License
MIT
