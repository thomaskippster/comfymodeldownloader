# ComfyUI Model Downloader Bridge
import server
import folder_paths
import asyncio
from aiohttp import web

print("\033[95m[Model-Downloader] UI Extension Bridge active\033[0m")

@server.PromptServer.instance.routes.post("/kippster/model-downloaded")
async def model_downloaded(request):
    print("\033[92m🔥 [Model-Downloader] PING ERHALTEN! Leere Backend-Caches...\033[0m")

    try:
        # 1. Kurze Pause für das Dateisystem (wichtig beim Entpacken/Verschieben)
        await asyncio.sleep(0.5)

        # 2. Lösche gezielt die generierten Dateilisten aus dem ComfyUI Cache
        if hasattr(folder_paths, "filename_list_cache"):
            folder_paths.filename_list_cache.clear()
            
        if hasattr(folder_paths, "cache_helper") and hasattr(folder_paths.cache_helper, "clear"):
            folder_paths.cache_helper.clear()

        # 3. Zwinge den Server, beim nächsten API-Call die Daten frisch zu generieren
        server.PromptServer.instance.object_info = None

        # 4. Sende das Signal an unser JavaScript (welches dann app.refresh() auslöst)
        server.PromptServer.instance.send_sync("kippster-refresh-ui", {"message": "ready"})

        return web.json_response({"status": "success", "message": "Cache cleared and frontend notified"})

    except Exception as e:
        print(f"[Model-Downloader] Fehler beim Backend-Reset: {e}")
        return web.json_response({"status": "error", "message": str(e)})

WEB_DIRECTORY = "web"
NODE_CLASS_MAPPINGS = {}
__all__ = ["WEB_DIRECTORY", "NODE_CLASS_MAPPINGS"]
