# ComfyUI Model Downloader Bridge
import server
import folder_paths
import os
from aiohttp import web

print("\033[95m[Model-Downloader] UI Extension Bridge active\033[0m")

WEB_DIRECTORY = "web"
NODE_CLASS_MAPPINGS = {}
__all__ = ["WEB_DIRECTORY", "NODE_CLASS_MAPPINGS"]
