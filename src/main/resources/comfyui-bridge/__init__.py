# ComfyUI Model Downloader Bridge
import server
from aiohttp import web

print("\033[95m[Model-Downloader] UI Extension Bridge active\033[0m")

@server.PromptServer.instance.routes.post("/kippster/model-downloaded")
async def model_downloaded(request):
    # Dieser Text MUSS in der ComfyUI-Konsole auftauchen, wenn Java fertig ist!
    print("\033[92m🔥 [Model-Downloader] PING ERHALTEN! Sende Signal ans Frontend...\033[0m")
    
    server.PromptServer.instance.send_sync("kippster-refresh-ui", {"message": "ready"})
    return web.json_response({"status": "success", "message": "UI refreshed"})

WEB_DIRECTORY = "web"
NODE_CLASS_MAPPINGS = {}
__all__ = ["WEB_DIRECTORY", "NODE_CLASS_MAPPINGS"]
