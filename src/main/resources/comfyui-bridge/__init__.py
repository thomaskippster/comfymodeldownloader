# ComfyUI Model Downloader Bridge
import server
from aiohttp import web

print("\033[95m[Model-Downloader] UI Extension Bridge active\033[0m")

@server.PromptServer.instance.routes.post("/kippster/model-downloaded")
async def model_downloaded(request):
    try:
        # Broadcast event to all connected clients
        server.PromptServer.instance.send_sync("kippster-refresh-ui", {"message": "New models available!"})
        return web.json_response({"status": "success", "message": "UI refresh triggered"})
    except Exception as e:
        return web.json_response({"status": "error", "message": str(e)}, status=500)

WEB_DIRECTORY = "web"
NODE_CLASS_MAPPINGS = {}
__all__ = ["WEB_DIRECTORY", "NODE_CLASS_MAPPINGS"]
