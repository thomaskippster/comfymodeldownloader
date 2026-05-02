#!/bin/bash
echo "=== Building ComfyUI Model Downloader ==="
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "[ERROR] Build failed!"
    exit 1
fi

echo ""
echo "=== Starting Application ==="
java --enable-native-access=ALL-UNNAMED -jar target/ComfyUIModelDownloader.jar "$@"
