# ComfyUI Model Downloader Bridge
import server
import folder_paths
import os
import asyncio
import importlib
from aiohttp import web

print("\033[95m[CMFD] UI Extension Bridge active\033[0m")

@server.PromptServer.instance.routes.post("/cmfd/refresh-models")
async def refresh_models(request):
    data = await request.json() if request.has_body else {}
    force_reload = data.get("force_reload", False)
    
    print(f"\033[92m🔥 [CMFD] Refresh signal received (Force Reload: {force_reload})\033[0m")
    
    # Wait for OS
    await asyncio.sleep(1)
    
    try:
        # Invalidate
        if hasattr(folder_paths, "invalidate_all_cached_folders"):
            folder_paths.invalidate_all_cached_folders()
            
        if hasattr(folder_paths, "filename_list_cache"):
            folder_paths.filename_list_cache.clear()
            
        try:
            importlib.reload(folder_paths)
        except Exception:
            pass

        # Object Info Reset
        server.PromptServer.instance.object_info = None
        
    except Exception as e:
        print(f"[CMFD] Error: {e}")
    
    # One-way broadcast to frontend
    server.PromptServer.instance.send_sync("cmfd-refresh-ui", {
        "status": "ok", 
        "force_reload": force_reload
    })
    
    return web.json_response({"status": "ok"})

WEB_DIRECTORY = "web"
NODE_CLASS_MAPPINGS = {}
__all__ = ["WEB_DIRECTORY", "NODE_CLASS_MAPPINGS"]
