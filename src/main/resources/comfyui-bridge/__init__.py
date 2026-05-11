# ComfyUI Model Downloader Bridge
import server
import folder_paths
import os
import asyncio
from aiohttp import web

print("\033[95m[Model-Downloader] UI Extension Bridge active\033[0m")

USER_MODEL_PATH = r"C:\AI\comfyuidata\models"
if os.path.exists(USER_MODEL_PATH):
    try:
        pass 
    except: pass

@server.PromptServer.instance.routes.post("/kippster/model-downloaded")
async def model_downloaded(request):
    print("\033[92m🔥 [Model-Downloader] PING ERHALTEN! Bereite UI-Refresh vor...\033[0m")
    
    # 1. Dem Dateisystem Zeit geben, BEVOR wir den Cache leeren!
    await asyncio.sleep(2)
    
    try:
        # 2. Erst jetzt die Caches leeren, wenn die Datei sicher im OS registriert ist
        if hasattr(folder_paths, "filename_list_cache"):
            folder_paths.filename_list_cache.clear()
            
        if hasattr(folder_paths, "cache_helper") and hasattr(folder_paths.cache_helper, "clear"):
            folder_paths.cache_helper.clear()
        
        if hasattr(folder_paths, "cache") and hasattr(folder_paths.cache, "clear"):
            folder_paths.cache.clear()

        # Object Info zurücksetzen (zwingt ComfyUI beim nächsten UI-Klick zum Neuladen)
        server.PromptServer.instance.object_info = None
        
    except Exception as e:
        print(f"[Model-Downloader] Fehler beim Cache-Reset: {e}")
    
    # 3. Signal an das Frontend senden (jetzt ist der Cache wirklich leer und das OS bereit!)
    server.PromptServer.instance.send_sync("kippster-refresh-ui", {"message": "ready"})
    
    return web.json_response({"status": "success"})

WEB_DIRECTORY = "web"
NODE_CLASS_MAPPINGS = {}
__all__ = ["WEB_DIRECTORY", "NODE_CLASS_MAPPINGS"]
