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
        console.log("%c🔥 [Model-Downloader] SIGNAL ERHALTEN! Erzwinge harten UI-Refresh...", "background: #ff0000; color: #fff; font-size: 14px;");
        
        try {
            // A. Offiziellen ComfyUI Refresh-Endpoint triggern (wie der Button im Manager)
            console.log("[Model-Downloader] Trigger /refresh_models...");
            await fetch('/refresh_models', { method: 'POST' });

            // B. Kurze Pause für das Backend
            await new Promise(r => setTimeout(r, 1000));

            // C. Node-Definitionen neu laden (Zwingt den Browser die neuen Listen anzufragen)
            console.log("[Model-Downloader] Re-fetching Node Definitions...");
            const nodeDefs = await api.getNodeDefs();
            let updatedCount = 0;
            
            // D. Nodes auf dem Canvas manuell aktualisieren
            if (app.graph && app.graph._nodes) {
                for (const node of app.graph._nodes) {
                    const def = nodeDefs[node.type];
                    if (def?.input?.required) {
                        for (const widgetName in def.input.required) {
                            const inputDef = def.input.required[widgetName];
                            if (Array.isArray(inputDef[0])) { // Es ist ein Dropdown (Combo)
                                const newValues = inputDef[0];
                                const widget = node.widgets?.find(w => w.name === widgetName);
                                
                                if (widget && widget.type === "combo") {
                                    const oldValue = widget.value;
                                    widget.options.values = newValues;
                                    
                                    // Check ob das ehemals rote Modell jetzt da ist
                                    if (newValues.includes(oldValue)) {
                                        if (node.color === "#322" || node.color === "#a22") {
                                            node.color = "";
                                            node.bgcolor = "";
                                            updatedCount++;
                                        }
                                    } else if (oldValue && oldValue !== "None" && !newValues.includes(oldValue)) {
                                        // Modell fehlt immer noch -> rot markieren
                                        node.color = "#322";
                                        node.bgcolor = "#a22";
                                    }
                                }
                            }
                        }
                    }
                    if (node.onRefresh) node.onRefresh();
                }
            }

            // E. ComfyUI Standard-Refresh (lädt Sidebars etc. neu)
            if (app.refresh) await app.refresh();
            
            // F. Canvas neu zeichnen
            app.graph.setDirtyCanvas(true, true);
            
            console.log(`[Model-Downloader] Sync abgeschlossen. ${updatedCount} Nodes aktualisiert.`);
            
            const msg = updatedCount > 0 
                ? `✅ Deep-Sync abgeschlossen!\n${updatedCount} Nodes wurden aktualisiert.`
                : "✅ Synchronisation abgeschlossen! Die Modell-Listen sind aktuell.";
            
            if (app.ui?.dialog?.show) app.ui.dialog.show(msg);
            else alert(msg);

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
        if (!document.getElementById("tki-downloader-fab")) {
            const fab = document.createElement("div");
            fab.id = "tki-downloader-fab";
            fab.innerHTML = "🚀";
            fab.title = "An Model Downloader senden";
            fab.style = "position:fixed; bottom:30px; right:30px; z-index:10000; cursor:pointer; font-size:30px; background:#ffcc00; border-radius:50%; width:60px; height:60px; display:flex; align-items:center; justify-content:center; box-shadow:0 0 20px rgba(0,0,0,0.5); border: 2px solid white; transition: transform 0.2s;";
            fab.onmouseover = () => fab.style.transform = "scale(1.1)";
            fab.onmouseout = () => fab.style.transform = "scale(1.0)";
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
        }
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
