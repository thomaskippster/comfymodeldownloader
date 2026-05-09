// ComfyUI Model Downloader - Bridge Extension
let app = null;
let api = null;

const initializeExtension = async () => {
    console.log("[Model-Downloader] Initializing...");

    // 1. Try to find ComfyUI app and api objects
    const paths = ["../../scripts/app.js", "/scripts/app.js", "../../../scripts/app.js"];
    const apiPaths = ["../../scripts/api.js", "/scripts/api.js", "../../../scripts/api.js"];

    for (const path of paths) {
        try {
            const mod = await import(path);
            if (mod.app) { app = mod.app; break; }
        } catch (e) {}
    }

    for (const path of apiPaths) {
        try {
            const mod = await import(path);
            if (mod.api) { api = mod.api; break; }
        } catch (e) {}
    }

    if (!app) {
        console.warn("[Model-Downloader] Could not find ComfyUI app object. Retrying in 2s...");
        setTimeout(initializeExtension, 2000);
        return;
    }

    // 2. Setup WebSocket Listener for Auto-Refresh
    if (api) {
        console.log("%c[Model-Downloader] WebSocket Listener verknüpft!", "color: #00ff00; font-weight: bold;");
        api.addEventListener("kippster-refresh-ui", (event) => {
            console.log("%c🔥 [Model-Downloader] SIGNAL EMPFANGEN! Lade UI neu...", "background: #ff0000; color: #fff; font-size: 14px;");
            if (app && typeof app.refresh === 'function') {
                app.refresh();
                const msg = "✅ Downloads abgeschlossen! Modelle sind jetzt verfügbar.";
                if (app.ui?.dialog?.show) app.ui.dialog.show(msg);
                else alert(msg);
            }
        });
    }

    // 3. Load Token and setup UI components
    let apiToken = null;
    const loadConfig = async () => {
        try {
            const baseUrl = new URL(".", import.meta.url).href;
            const configUrl = `${baseUrl}config.json?v=${Date.now()}`;
            const response = await fetch(configUrl);
            if (response.ok) {
                const config = await response.json();
                apiToken = config.token;
            }
        } catch (e) { console.error("[Model-Downloader] Config load error:", e); }
    };
    await loadConfig();

    const notify = (msg) => {
        if (app?.ui?.dialog?.show) app.ui.dialog.show(msg);
        else if (window.comfyAPI?.dialog?.show) window.comfyAPI.dialog.show(msg);
        else alert(msg);
    };

    const showConnectionError = () => {
        const dialog = document.createElement("dialog");
        dialog.style = "background:#222; color:white; border:2px solid #ffcc00; border-radius:10px; padding:20px; width:450px; font-family:sans-serif; box-shadow:0 0 30px rgba(0,0,0,0.5); z-index:20002;";
        dialog.innerHTML = `
            <div style="text-align:center;">
                <div style="font-size:50px; margin-bottom:15px;">🚀❌</div>
                <h2 style="margin-top:0; color:#ffcc00;">Downloader nicht erreichbar</h2>
                <p style="line-height:1.5;">Die Model Downloader App antwortet nicht. Bitte stelle sicher, dass sie läuft.</p>
                <a href="https://sourceforge.net/projects/comfymodeldownloader/" target="_blank" style="display:block; background:#ffcc00; color:black; text-decoration:none; padding:10px; border-radius:5px; font-weight:bold; margin-bottom:20px;">📥 Download via SourceForge</a>
                <button id="tki-error-close" style="background:#444; color:white; border:none; padding:10px 20px; border-radius:5px; cursor:pointer;">Schließen</button>
            </div>
        `;
        document.body.appendChild(dialog);
        dialog.showModal();
        dialog.querySelector("#tki-error-close").onclick = () => { dialog.close(); document.body.removeChild(dialog); };
    };

    const sendWorkflow = async () => {
        if (!app?.graph) return notify("❌ Fehler: ComfyUI Graph nicht bereit.");
        if (!apiToken) return notify("❌ Sicherheits-Fehler: Kein API-Token gefunden.");
        const workflow = app.graph.serialize();
        try {
            const response = await fetch("http://127.0.0.1:12345/import", {
                method: "POST", mode: "cors",
                headers: { "Content-Type": "application/json", "Authorization": `Bearer ${apiToken}` },
                body: JSON.stringify(workflow)
            });
            if (response.ok) notify("🚀 Workflow erfolgreich gesendet!");
            else notify("❌ Fehler: Downloader abgelehnt (Status: " + response.status + ")");
        } catch (e) { showConnectionError(); }
    };

    // UI Buttons
    const addUI = () => {
        if (!document.getElementById("tki-downloader-fab")) {
            const fab = document.createElement("div");
            fab.id = "tki-downloader-fab";
            fab.innerHTML = "🚀";
            fab.style = "position:fixed; bottom:30px; right:30px; z-index:10000; cursor:pointer; font-size:30px; background:#ffcc00; border-radius:50%; width:60px; height:60px; display:flex; align-items:center; justify-content:center; box-shadow:0 0 20px rgba(0,0,0,0.5); border: 2px solid white;";
            fab.onclick = sendWorkflow;
            document.body.appendChild(fab);
        }
        const menu = document.querySelector(".comfy-menu");
        if (menu && !document.getElementById("tki-downloader-classic")) {
            const btn = document.createElement("button");
            btn.id = "tki-downloader-classic";
            btn.textContent = "🚀 Downloader";
            btn.style.background = "#ffcc00"; btn.style.color = "white";
            btn.onclick = sendWorkflow;
            menu.appendChild(btn);
        }
        if (app?.ui?.menu?.addPrimaryAction) {
            app.ui.menu.addPrimaryAction({ id: "tki.model-downloader.send", icon: "pi pi-download", label: "Send to Downloader", callback: sendWorkflow });
        }
    };

    addUI();
};

// Register the extension
const register = () => {
    // We try multiple ways to hook into ComfyUI
    if (window.app && window.app.registerExtension) {
        window.app.registerExtension({ name: "TKI.ModelDownloader", async setup() { await initializeExtension(); } });
    } else {
        // Fallback for manual load
        if (document.readyState === "complete") initializeExtension();
        else window.addEventListener("load", initializeExtension);
    }
};

register();
