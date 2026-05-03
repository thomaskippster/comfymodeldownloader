module.exports = {
  title: "ComfyUI Model Downloader",
  description: "Identify and download missing models for ComfyUI workflows automatically.",
  icon: "icon.png",
  menu: async (kernel) => {
    let installed = await kernel.exists("target/ComfyUIModelDownloader.jar")
    let results = [
      { icon: "fa-solid fa-play", text: "Start", href: "start.json" },
      { icon: "fa-solid fa-rotate", text: "Update", href: "update.json" }
    ]
    if (installed) {
      results.push({ icon: "fa-solid fa-plug", text: "Reinstall", href: "install.json" })
    } else {
      results.unshift({ icon: "fa-solid fa-plug", text: "Install", href: "install.json" })
    }
    return results
  }
}
