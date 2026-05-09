# ComfyUI Model Downloader Bridge
import server
import folder_paths
import os
import asyncio
from aiohttp import web

print("\033[95m[Model-Downloader] UI Extension Bridge active\033[0m")

@server.PromptServer.instance.routes.post("/kippster/model-downloaded")
async def model_downloaded(request):
    print("\033[92m🔥 [Model-Downloader] PING ERHALTEN! Erzwinge harten Backend-Reset...\033[0m")
    
    try:
        # 1. Dateisystem-Cache löschen (Windows braucht hier Zeit)
        await asyncio.sleep(0.5)
        if hasattr(folder_paths, "base_path_layers"):
            for layer in folder_paths.base_path_layers.values():
                layer.folder_names.clear()
        if hasattr(folder_paths, "folder_names"):
            folder_paths.folder_names.clear()

        # 2. Den Definitions-Cache von ComfyUI löschen
        # Das zwingt ComfyUI, die Dropdown-Listen für den Browser komplett neu zu generieren
        server.PromptServer.instance.object_info = None
        
        # 3. Aktiven Scan triggern
        for folder in ["checkpoints", "loras", "vae", "controlnet", "diffusion_models", "upscale_models", "clip"]:
            try:
                folder_paths.get_filename_list(folder)
            except: pass
        
        print("[Model-Downloader] Backend-Reset und Scan abgeschlossen.")

    except Exception as e:
        print(f"[Model-Downloader] Fehler beim Reset: {e}")
    
    # 4. Signale an alle Browser (jetzt mit frischen Daten)
    server.PromptServer.instance.send_sync("refresh", {})
    server.PromptServer.instance.send_sync("kippster-refresh-ui", {"message": "ready"})
    
    return web.json_response({"status": "success", "message": "Backend reset and rescan complete"})

WEB_DIRECTORY = "web"
NODE_CLASS_MAPPINGS = {}
__all__ = ["WEB_DIRECTORY", "NODE_CLASS_MAPPINGS"]
