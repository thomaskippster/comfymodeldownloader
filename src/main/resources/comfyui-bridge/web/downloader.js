// ComfyUI Model Downloader - Bridge Extension
let app = null;
let api = null;

const initializeExtension = async () => {
    const paths = ["../../scripts/app.js", "/scripts/app.js", "../../../scripts/app.js"];
    const apiPaths = ["../../scripts/api.js", "/scripts/api.js", "../../../scripts/api.js"];
    for (const path of paths) { try { const mod = await import(path); if (mod.app) { app = mod.app; break; } } catch (e) {} }
    for (const path of apiPaths) { try { const mod = await import(path); if (mod.api) { api = mod.api; break; } } catch (e) {} }

    if (!app || !api) { setTimeout(initializeExtension, 2000); return; }

    api.addEventListener("kippster-refresh-ui", async (event) => {
        console.log("%c🔥 [Model-Downloader] SIGNAL EMPFANGEN! Starte Deep-Node-Update...", "background: #ff0000; color: #fff; font-size: 14px;");
        
        try {
            // 1. Backend Zeit zum Scannen geben
            await new Promise(r => setTimeout(r, 800));

            // 2. Definitionen neu laden (Zwingt den Browser die neuesten Modell-Listen zu kennen)
            const nodeDefs = await api.getNodeDefs();
            
            // 3. Bestehende Nodes auf dem Canvas aktualisieren
            if (app.graph && app.graph._nodes) {
                console.log("[Model-Downloader] Aktualisiere Dropdowns in " + app.graph._nodes.length + " Nodes...");
                for (const node of app.graph._nodes) {
                    const def = nodeDefs[node.type];
                    if (def && def.input && def.input.required) {
                        for (const widgetName in def.input.required) {
                            const inputDef = def.input.required[widgetName];
                            // Falls es ein Dropdown (Combo) ist
                            if (Array.isArray(inputDef[0])) {
                                const newValues = inputDef[0];
                                const widget = node.widgets?.find(w => w.name === widgetName);
                                if (widget && widget.type === "combo") {
                                    widget.options.values = newValues;
                                    
                                    // Validierung: Ist das aktuell gewählte Modell noch da?
                                    if (newValues.includes(widget.value)) {
                                        // Modell vorhanden -> Node "heilen" (Farbe zurücksetzen)
                                        if (node.color === "#322" || node.color === "#a22") {
                                            node.color = "";
                                            node.bgcolor = "";
                                        }
                                    } else if (widget.value && widget.value !== "None") {
                                        // Modell fehlt -> Node rot markieren (ComfyUI Standard für Fehler)
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

            if (app.refresh) await app.refresh();
            app.graph.setDirtyCanvas(true, true);
            
            if (app.ui?.dialog?.show) {
                app.ui.dialog.show("✅ Synchronisation abgeschlossen! Die Modell-Listen wurden aktualisiert.");
            }

        } catch (e) {
            console.error("[Model-Downloader] Fehler beim Deep-Node-Update:", e);
        }
    });

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
            fab.style = "position:fixed; bottom:30px; right:30px; z-index:10000; cursor:pointer; font-size:30px; background:#ffcc00; border-radius:50%; width:60px; height:60px; display:flex; align-items:center; justify-content:center; box-shadow:0 0 20px rgba(255,204,0,0.5); border: 2px solid white;";
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
