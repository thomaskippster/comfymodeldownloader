@echo off
echo Building the project with Maven...
call mvn clean package
if %ERRORLEVEL% NEQ 0 (
    echo Build failed! Please check the errors above.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo Starting ComfyUI Model Downloader...
java -jar target\comfyuimodel-downloader-1.0-SNAPSHOT.jar
pause
