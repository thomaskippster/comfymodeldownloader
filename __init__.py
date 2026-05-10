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
        await asyncio.sleep(0.5)
        
        # 2. Caches in folder_paths löschen
        # WICHTIG: Nicht folder_names löschen! Das sind die Konfigurationen der Pfade.
        # Wir löschen nur die berechneten Dateilisten (Caches).
        
        if hasattr(folder_paths, "filename_list_cache"):
            folder_paths.filename_list_cache.clear()
            print("[Model-Downloader] filename_list_cache geleert.")
            
        if hasattr(folder_paths, "cache_helper"):
            if hasattr(folder_paths.cache_helper, "clear"):
                folder_paths.cache_helper.clear()
                print("[Model-Downloader] cache_helper geleert.")
        
        if hasattr(folder_paths, "cache"):
            if hasattr(folder_paths.cache, "clear"):
                folder_paths.cache.clear()
            else:
                folder_paths.cache = {}
            print("[Model-Downloader] interner folder_paths.cache geleert.")

        # 3. Aktiven Scan triggern
        # Wir rufen get_filename_list für die wichtigsten Ordner auf
        # Dies repopuliert den Cache mit den neuen Dateien von der Platte
        folders = ["checkpoints", "loras", "vae", "controlnet", "diffusion_models", "upscale_models", 
                   "clip", "unet", "gligen", "embeddings", "configs", "hypernetworks", "style_models"]
        
        # Falls folder_names existiert, nehmen wir alle dort registrierten Kategorien
        if hasattr(folder_paths, "folder_names"):
            folders = list(folder_paths.folder_names.keys())

        for folder in folders:
            try:
                folder_paths.get_filename_list(folder)
            except: pass
        
        print(f"[Model-Downloader] Backend-Scan abgeschlossen. {len(folders)} Kategorien aktualisiert.")

    except Exception as e:
        print(f"[Model-Downloader] Fehler beim Backend-Reset: {e}")
    
    # 4. Signale an alle Browser senden
    # Wir senden "refresh", was das Standard-Signal für den UI-Reload ist
    server.PromptServer.instance.send_sync("refresh", {})
    # Und unser spezielles Signal für die JS-Bridge
    server.PromptServer.instance.send_sync("kippster-refresh-ui", {"message": "ready"})
    
    return web.json_response({"status": "success", "message": "Backend reset and rescan complete"})

WEB_DIRECTORY = "web"
NODE_CLASS_MAPPINGS = {}
__all__ = ["WEB_DIRECTORY", "NODE_CLASS_MAPPINGS"]
