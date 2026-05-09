# ComfyUI Model Downloader Bridge
import server
from aiohttp import web

print("\033[95m[Model-Downloader] UI Extension Bridge active\033[0m")

# NEU: Der API-Endpunkt für den Java-Ping
@server.PromptServer.instance.routes.post("/kippster/model-downloaded")
async def model_downloaded(request):
    print("[Model-Downloader] Download finished trigger received from Java!")
    # Sendet das Signal ans Frontend
    server.PromptServer.instance.send_sync("kippster-refresh-ui", {"message": "New models available!"})
    return web.json_response({"status": "success", "message": "UI refresh triggered"})

WEB_DIRECTORY = "web"
NODE_CLASS_MAPPINGS = {}
__all__ = ["WEB_DIRECTORY", "NODE_CLASS_MAPPINGS"]
