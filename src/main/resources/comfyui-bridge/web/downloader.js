// ComfyUI Model Downloader - Bridge Extension
let app = null;
let api = null;

const initializeExtension = async () => {
    const paths = ["../../scripts/app.js", "/scripts/app.js", "../../../scripts/app.js"];
    const apiPaths = ["../../scripts/api.js", "/scripts/api.js", "../../../scripts/api.js"];
    for (const path of paths) { try { const mod = await import(path); if (mod.app) { app = mod.app; break; } } catch (e) {} }
    for (const path of apiPaths) { try { const mod = await import(path); if (mod.api) { api = mod.api; break; } } catch (e) {} }

    if (!app || !api) { setTimeout(initializeExtension, 2000); return; }

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

    // Event Listener for UI Refresh
    api.addEventListener("cmfd-refresh-ui", async (event) => {
        const data = event.detail || {};
        const forceReload = data.force_reload === true;

        console.log(`%c🔥 [CMFD] Refresh Signal Received. Force Reload: ${forceReload}`, "background: #ff0000; color: #fff; font-size: 14px;");
        
        if (forceReload) {
            window.location.reload();
            return;
        }

        try {
            // 1. Clear Frontend Caches
            api.nodeDefs = null;

            // 2. Trigger standard ComfyUI refresh
            if (app.refresh) {
                await app.refresh(); 
            }

            // 3. Update existing combo widgets
            const nodeDefs = await api.getNodeDefs();
            if (app.graph && app.graph._nodes) {
                for (const node of app.graph._nodes) {
                    const def = nodeDefs[node.type];
                    if (!def || !node.widgets) continue;

                    for (const widget of node.widgets) {
                        if (widget.type === "combo") {
                            const inputDef = def.input?.required?.[widget.name] || def.input?.optional?.[widget.name];
                            if (inputDef && Array.isArray(inputDef[0])) {
                                widget.options.values = inputDef[0];
                            }
                        }
                    }
                    node.setDirtyCanvas(true, true);
                }
            }
            console.log("[CMFD] UI Sync Complete.");
        } catch (e) {
            console.error("[CMFD] UI Refresh Failed:", e);
        }
    });

    const addUI = () => {
        if (document.getElementById("tki-downloader-fab")) return;
        const fab = document.createElement("div");
        fab.id = "tki-downloader-fab";
        fab.innerHTML = "🚀";
        fab.style = "position:fixed; bottom:30px; right:30px; z-index:10000; cursor:pointer; font-size:30px; background:#ffcc00; border-radius:50%; width:60px; height:60px; display:flex; align-items:center; justify-content:center; box-shadow:0 0 20px rgba(0,0,0,0.5); border: 2px solid white; transition: transform 0.2s;";
        fab.onclick = async () => {
            if (!app?.graph) return;
            try {
                await fetch("http://127.0.0.1:12345/import", {
                    method: "POST", mode: "cors",
                    headers: { "Content-Type": "application/json", "Authorization": `Bearer ${apiToken}` },
                    body: JSON.stringify(app.graph.serialize())
                });
            } catch (e) {}
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
