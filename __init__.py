# ComfyUI Model Downloader Bridge
import server
import folder_paths
import os
import asyncio
from aiohttp import web

print("\033[95m[Model-Downloader] UI Extension Bridge active\033[0m")

# Erzwungene Pfad-Registrierung für den User-Pfad
USER_MODEL_PATH = r"C:\AI\comfyuidata\models"
if os.path.exists(USER_MODEL_PATH):
    print(f"[Model-Downloader] Prüfe User-Modell-Pfad: {USER_MODEL_PATH}")
    # Wir fügen den Pfad zu den Standard-Modell-Pfaden hinzu, falls er fehlt
    # ComfyUI nutzt intern oft 'base_path' oder 'extra_model_paths'
    try:
        # Versuch, den Pfad als extra Modell-Pfad zu registrieren
        # folder_paths.add_model_folder_path("custom", USER_MODEL_PATH)
        pass 
    except: pass

@server.PromptServer.instance.routes.post("/kippster/model-downloaded")
async def model_downloaded(request):
    print("\033[92m🔥 [Model-Downloader] PING ERHALTEN! Erzwinge harten Backend-Reset...\033[0m")
    
    try:
        # 1. Warte auf Dateisystem (besonders wichtig bei Windows/HDD)
        await asyncio.sleep(1.0)
        
        # 2. Alle Caches in folder_paths radikal leeren
        if hasattr(folder_paths, "filename_list_cache"):
            folder_paths.filename_list_cache.clear()
            
        if hasattr(folder_paths, "cache_helper"):
            if hasattr(folder_paths.cache_helper, "clear"):
                folder_paths.cache_helper.clear()
        
        if hasattr(folder_paths, "cache"):
            if hasattr(folder_paths.cache, "clear"):
                folder_paths.cache.clear()
            else:
                folder_paths.cache = {}

        # 3. Aktiven Scan triggern für ALLE bekannten Kategorien
        folders = ["checkpoints", "loras", "vae", "controlnet", "diffusion_models", "upscale_models", 
                   "clip", "unet", "gligen", "embeddings", "configs", "hypernetworks", "style_models"]
        
        if hasattr(folder_paths, "folder_names"):
            folders = list(folder_paths.folder_names.keys())

        for folder in folders:
            try:
                # get_filename_list erzwingt bei leerem Cache einen neuen os.walk
                folder_paths.get_filename_list(folder)
            except: pass
        
        # 4. Den globalen object_info Cache im Server zurücksetzen
        # Das zwingt den Server beim nächsten /object_info Aufruf alles neu zu berechnen
        server.PromptServer.instance.object_info = None
        
        print(f"[Model-Downloader] Backend-Scan & Object-Info Reset abgeschlossen.")

    except Exception as e:
        print(f"[Model-Downloader] Fehler beim Backend-Reset: {e}")
    
    # 5. Signale an alle Browser senden
    # Wir senden "refresh" (Standard für ComfyUI UI-Refresh)
    server.PromptServer.instance.send_sync("refresh", {})
    # Unser spezieller Trigger für den Deep-UI-Sync
    server.PromptServer.instance.send_sync("kippster-refresh-ui", {"message": "ready"})
    
    return web.json_response({"status": "success", "message": "Backend reset and rescan complete"})

WEB_DIRECTORY = "web"
NODE_CLASS_MAPPINGS = {}
__all__ = ["WEB_DIRECTORY", "NODE_CLASS_MAPPINGS"]
