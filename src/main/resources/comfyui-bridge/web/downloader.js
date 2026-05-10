// ComfyUI Model Downloader - Bridge Extension
let app = null;
let api = null;

const initializeExtension = async () => {
    // 1. Suche nach ComfyUI App/API
    const paths = ["../../scripts/app.js", "/scripts/app.js", "../../../scripts/app.js"];
    const apiPaths = ["../../scripts/api.js", "/scripts/api.js", "../../../scripts/api.js"];
    for (const path of paths) { try { const mod = await import(path); if (mod.app) { app = mod.app; break; } } catch (e) {} }
    for (const path of apiPaths) { try { const mod = await import(path); if (mod.api) { api = mod.api; break; } } catch (e) {} }

    if (!app || !api) { setTimeout(initializeExtension, 2000); return; }

    // 2. WebSocket Listener für den "Sync"-Trigger
    api.addEventListener("kippster-refresh-ui", async (event) => {
        console.log("%c🔥 [Model-Downloader] SIGNAL ERHALTEN! Starte echten UI-Refresh...", "background: #ff0000; color: #fff; font-size: 14px;");

        try {
            // Zeige Feedback an
            const showToast = (text) => {
                const toast = document.createElement("div");
                toast.style = "position:fixed; top:20px; left:50%; transform:translateX(-50%); background:rgba(255,204,0,0.9); color:black; padding:10px 20px; border-radius:5px; z-index:10001; font-weight:bold; font-family:sans-serif; pointer-events:none; transition: opacity 0.5s;";
                toast.textContent = text;
                document.body.appendChild(toast);
                setTimeout(() => { toast.style.opacity = "0"; setTimeout(() => document.body.removeChild(toast), 500); }, 3000);
            };
            showToast("🔄 Lade Node-Definitionen neu...");

            // WICHTIG: Das ist der Gamechanger! Löscht den Frontend-Cache von ComfyUI.
            // Ohne das hier lädt ComfyUI immer die alten Modell-Listen aus dem Browser-RAM.
            api.nodeDefs = null;

            // Gib dem Windows-Dateisystem kurz Zeit, die kopierten/verschobenen Dateien zu registrieren
            await new Promise(r => setTimeout(r, 800));

            // Ruft die offizielle ComfyUI Refresh-Funktion auf (das entspricht exakt dem Klick auf "Refresh" im Menü)
            // app.refresh() zieht sich automatisch die neuen nodeDefs vom Server und "heilt" fehlerhafte Nodes.
            if (app.refresh) {
                await app.refresh();
            }

            // Canvas zwingen, sich neu zu zeichnen
            app.graph.setDirtyCanvas(true, true);
            console.log("[Model-Downloader] Sync erfolgreich abgeschlossen.");

        } catch (e) {
            console.error("[Model-Downloader] Fehler beim erzwungenen Refresh:", e);
        }
    });

    // 3. Token & FAB UI
    let apiToken = null;
    const loadConfig = async () => {
        try {
            const baseUrl = new URL(".", import.meta.url).href;
            const configUrl = `${baseUrl}config.json?v=${Date.now()}`;
            const response = await fetch(configUrl);
            if (response.ok) { const config = await response.json(); apiToken = config.token; }
        } catch (e) {}
    };
    await loadConfig();

    const addUI = () => {
        if (document.getElementById("tki-downloader-fab")) return;
        const fab = document.createElement("div");
        fab.id = "tki-downloader-fab";
        fab.innerHTML = "🚀";
        fab.style = "position:fixed; bottom:30px; right:30px; z-index:10000; cursor:pointer; font-size:30px; background:#ffcc00; border-radius:50%; width:60px; height:60px; display:flex; align-items:center; justify-content:center; box-shadow:0 0 20px rgba(0,0,0,0.5); border: 2px solid white; transition: transform 0.2s;";
        fab.onclick = async () => {
            if (!app?.graph) return;
            try {
                const response = await fetch("http://127.0.0.1:12345/import", {
                    method: "POST", mode: "cors",
                    headers: { "Content-Type": "application/json", "Authorization": `Bearer ${apiToken}` },
                    body: JSON.stringify(app.graph.serialize())
                });
                if (response.ok) {
                    if (app.ui?.dialog?.show) app.ui.dialog.show("🚀 Workflow an Downloader gesendet!");
                }
            } catch (e) {
                alert("❌ Downloader App nicht erreichbar!");
            }
        };
        document.body.appendChild(fab);
    };
    addUI();
};

const register = () => {
    if (window.app && window.app.registerExtension) {
        window.app.registerExtension({ name: "TKI.ModelDownloader", async setup() { await initializeExtension(); } });
    } else {
        if (document.readyState === "complete") initializeExtension();
        else window.addEventListener("load", initializeExtension);
    }
};
register();
