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
        # 1. Warte kurz auf das Dateisystem (besonders wichtig unter Windows)
        await asyncio.sleep(0.8)
        
        # 2. Alle Caches in folder_paths radikal leeren
        # Wir löschen hier wirklich alles, was ComfyUI im RAM behält
        if hasattr(folder_paths, "folder_names"):
            folder_paths.folder_names.clear()
        
        if hasattr(folder_paths, "base_path_layers"):
            for layer in folder_paths.base_path_layers.values():
                if hasattr(layer, "folder_names"):
                    layer.folder_names.clear()
        
        if hasattr(folder_paths, "cache"):
            folder_paths.cache.clear()

        # 3. Den Definitions-Cache von ComfyUI löschen
        # Dies zwingt ComfyUI, die Node-Metadaten (inkl. Modell-Listen) neu zu generieren
        server.PromptServer.instance.object_info = None
        
        # 4. Aktiven Scan triggern
        # Wir rufen get_filename_list für JEDEN bekannten Ordner auf
        # Das zwingt das Backend, die Festplatte wirklich neu zu lesen
        all_folders = list(folder_paths.folder_names.keys()) if hasattr(folder_paths, "folder_names") else []
        # Fallback auf Standardordner falls Liste leer
        if not all_folders:
            all_folders = ["checkpoints", "loras", "vae", "controlnet", "diffusion_models", "upscale_models", 
                          "clip", "unet", "gligen", "embeddings", "configs", "hypernetworks", "style_models"]
        
        for folder in all_folders:
            try:
                folder_paths.get_filename_list(folder)
            except: pass
        
        print(f"[Model-Downloader] Backend-Reset abgeschlossen. {len(all_folders)} Ordner neu indiziert.")

    except Exception as e:
        print(f"[Model-Downloader] Fehler beim Reset: {e}")
    
    # 5. Signale an alle Browser
    # Wir senden mehrere Signale um sicherzugehen, dass alle Extension-Typen reagieren
    server.PromptServer.instance.send_sync("refresh_models", {})
    server.PromptServer.instance.send_sync("refresh", {})
    server.PromptServer.instance.send_sync("kippster-refresh-ui", {"message": "ready"})
    
    return web.json_response({"status": "success", "message": "Backend reset and rescan complete"})

WEB_DIRECTORY = "web"
NODE_CLASS_MAPPINGS = {}
__all__ = ["WEB_DIRECTORY", "NODE_CLASS_MAPPINGS"]
