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
        # 1. Dateisystem-Cache löschen (Kurze Pause für Windows-Dateisystem-Handles)
        await asyncio.sleep(0.5)
        
        # Alle Caches in folder_paths radikal leeren
        if hasattr(folder_paths, "folder_names"):
            for k in list(folder_paths.folder_names.keys()):
                folder_paths.folder_names[k] = []
        
        if hasattr(folder_paths, "base_path_layers"):
            for layer in folder_paths.base_path_layers.values():
                if hasattr(layer, "folder_names"):
                    layer.folder_names.clear()
        
        if hasattr(folder_paths, "cache"):
            folder_paths.cache.clear()

        # 2. Den Definitions-Cache von ComfyUI löschen
        # Dies zwingt ComfyUI, die Node-Metadaten (inkl. Modell-Listen) neu zu generieren
        server.PromptServer.instance.object_info = None
        
        # 3. Aktiven Scan triggern (Offizieller Weg via get_filename_list)
        # Wir decken hier alle gängigen Modell-Typen ab
        folders = ["checkpoints", "loras", "vae", "controlnet", "diffusion_models", "upscale_models", 
                   "clip", "unet", "gligen", "embeddings", "configs", "hypernetworks", "style_models"]
        for folder in folders:
            try:
                folder_paths.get_filename_list(folder)
            except: pass
        
        print(f"[Model-Downloader] Backend-Reset abgeschlossen. Gescannte Ordner: {len(folders)}")

    except Exception as e:
        print(f"[Model-Downloader] Fehler beim Reset: {e}")
    
    # 4. Signale an alle Browser
    # "refresh_models" wird von vielen Extensions abgefangen
    server.PromptServer.instance.send_sync("refresh_models", {})
    # "refresh" ist der Standard für ComfyUI
    server.PromptServer.instance.send_sync("refresh", {})
    # Unser spezieller Trigger
    server.PromptServer.instance.send_sync("kippster-refresh-ui", {"message": "ready"})
    
    return web.json_response({"status": "success", "message": "Backend reset and rescan complete"})

WEB_DIRECTORY = "web"
NODE_CLASS_MAPPINGS = {}
__all__ = ["WEB_DIRECTORY", "NODE_CLASS_MAPPINGS"]
