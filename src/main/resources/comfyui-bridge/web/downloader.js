// Try different ways to get the ComfyUI app and api objects
let app = null;
let api = null;

const tryImports = async () => {
    const paths = [
        "../../scripts/app.js",
        "/scripts/app.js",
        "../../../scripts/app.js"
    ];
    const apiPaths = [
        "../../scripts/api.js",
        "/scripts/api.js",
        "../../../scripts/api.js"
    ];

    for (const path of paths) {
        try {
            const mod = await import(path);
            if (mod.app) {
                app = mod.app;
                console.log(`[Model-Downloader] Successfully imported app from ${path}`);
                break;
            }
        } catch (e) {}
    }

    for (const path of apiPaths) {
        try {
            const mod = await import(path);
            if (mod.api) {
                api = mod.api;
                console.log(`[Model-Downloader] Successfully imported api from ${path}`);
                break;
            }
        } catch (e) {}
    }
};

await tryImports();

// Define setup logic that can be called by extension or as fallback
const initializeExtension = async () => {
    let apiToken = null;

    if (api) {
        api.addEventListener("kippster-refresh-ui", () => {
            console.log("[Model-Downloader] Refresh event received from WebSocket. Refreshing ComfyUI...");
            if (app?.refresh) {
                app.refresh();
                // We don't use alert() here because it's annoying during background downloads, 
                // but we could use a non-blocking toast if available.
                if (app.ui?.dialog?.show) {
                    // Optional: show a temporary notification if we want
                }
            }
        });
    }

    const loadConfig = async () => {
        try {
            const baseUrl = new URL(".", import.meta.url).href;
            const configUrl = `${baseUrl}config.json?v=${Date.now()}`;
            console.log(`[Model-Downloader] Loading config from: ${configUrl}`);
            const response = await fetch(configUrl);
            if (response.ok) {
                const config = await response.json();
                apiToken = config.token;
                if (apiToken) {
                    const masked = apiToken.substring(0, 4) + "..." + apiToken.substring(apiToken.length - 4);
                    console.log(`[Model-Downloader] Token loaded successfully: ${masked}`);
                } else {
                    console.warn("[Model-Downloader] Token in config.json is empty!");
                }
            } else {
                console.error(`[Model-Downloader] Could not load config.json (Status: ${response.status})`);
            }
        } catch (e) {
            console.error("[Model-Downloader] Failed to load config.json:", e);
        }
    };

    loadConfig();

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
                <p style="line-height:1.5;">Die Model Downloader App antwortet nicht. Bitte stelle sicher, dass:</p>
                <ul style="text-align:left; display:inline-block; margin-bottom:20px;">
                    <li>Die Java-App gestartet wurde.</li>
                    <li>Port 12345 nicht blockiert wird.</li>
                </ul>
                <p>Noch nicht installiert? Lade die App hier herunter:</p>
                <a href="https://sourceforge.net/projects/comfymodeldownloader/" target="_blank" style="display:block; background:#ffcc00; color:black; text-decoration:none; padding:10px; border-radius:5px; font-weight:bold; margin-bottom:20px;">📥 Download via SourceForge</a>
                <button id="tki-error-close" style="background:#444; color:white; border:none; padding:10px 20px; border-radius:5px; cursor:pointer;">Schließen</button>
            </div>
        `;
        document.body.appendChild(dialog);
        dialog.showModal();
        dialog.querySelector("#tki-error-close").onclick = () => {
            dialog.close();
            document.body.removeChild(dialog);
        };
    };

    const sendWorkflow = async () => {
        if (!app || !app.graph) return notify("❌ Fehler: ComfyUI Graph nicht bereit.");
        if (!apiToken) return notify("❌ Sicherheits-Fehler: Kein API-Token gefunden.");

        const workflow = app.graph.serialize();
        try {
            const response = await fetch("http://127.0.0.1:12345/import", {
                method: "POST",
                mode: "cors",
                headers: { 
                    "Content-Type": "application/json",
                    "Authorization": `Bearer ${apiToken}`
                },
                body: JSON.stringify(workflow)
            });
            if (response.ok) notify("🚀 Workflow erfolgreich gesendet!");
            else notify("❌ Fehler: Downloader abgelehnt (Status: " + response.status + ")");
        } catch (e) {
            showConnectionError();
        }
    };

    const openCleanupDialog = async () => {
        if (!apiToken) return notify("❌ Sicherheits-Fehler: Kein API-Token gefunden.");

        const dialog = document.createElement("dialog");
        dialog.id = "tki-cleanup-dialog";
        dialog.style = "background:#222; color:white; border:2px solid #ffcc00; border-radius:10px; padding:20px; width:800px; max-height:80vh; overflow:hidden; font-family:sans-serif; box-shadow:0 0 30px rgba(0,0,0,0.5); z-index:20001;";
        
        dialog.innerHTML = `
            <div id="tki-cleanup-content">
                <h2 style="margin-top:0; color:#ffcc00;">🧹 Lokale Modelle aufräumen</h2>
                <div id="tki-cleanup-loading" style="text-align:center; padding:40px;">
                    <div style="border:4px solid #f3f3f3; border-top:4px solid #ffcc00; border-radius:50%; width:30px; height:30px; animation:spin 1s linear infinite; margin:0 auto 10px;"></div>
                    <span>Lade lokale Modelle...</span>
                </div>
                <div id="tki-cleanup-table-container" style="display:none;">
                    <div style="max-height:50vh; overflow-y:auto; margin-bottom:20px; border:1px solid #444;">
                        <table style="width:100%; border-collapse:collapse; text-align:left;">
                            <thead style="position:sticky; top:0; background:#333;">
                                <tr>
                                    <th style="padding:10px;"><input type="checkbox" id="tki-select-all"></th>
                                    <th style="padding:10px;">Name</th>
                                    <th style="padding:10px;">Typ</th>
                                    <th style="padding:10px;">Größe</th>
                                </tr>
                            </thead>
                            <tbody id="tki-cleanup-tbody"></tbody>
                        </table>
                    </div>
                    <div style="display:flex; justify-content:space-between; align-items:center;">
                        <span id="tki-cleanup-status" style="font-style:italic; color:#aaa;"></span>
                        <div style="display:flex; gap:10px;">
                            <button id="tki-cleanup-cancel" style="background:#444; color:white; border:none; padding:10px 20px; border-radius:5px; cursor:pointer;">Abbrechen</button>
                            <button id="tki-cleanup-archive" style="background:#ffcc00; color:white; border:none; padding:10px 20px; border-radius:5px; cursor:pointer; font-weight:bold;">Ausgewählte archivieren</button>
                        </div>
                    </div>
                </div>
            </div>
            <style>
                @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
            </style>
        `;

        document.body.appendChild(dialog);
        dialog.showModal();

        const refreshData = async () => {
            const loading = dialog.querySelector("#tki-cleanup-loading");
            const container = dialog.querySelector("#tki-cleanup-table-container");
            loading.style.display = "block";
            container.style.display = "none";

            try {
                const response = await fetch("http://127.0.0.1:12345/api/models/local", {
                    headers: { "Authorization": `Bearer ${apiToken}` }
                });
                const models = await response.json();
                
                const tbody = dialog.querySelector("#tki-cleanup-tbody");
                tbody.innerHTML = models.map(m => `
                    <tr style="border-bottom:1px solid #333;">
                        <td style="padding:10px;"><input type="checkbox" class="tki-model-select" data-path="${m.save_path}/${m.name}"></td>
                        <td style="padding:10px;">${m.name}</td>
                        <td style="padding:10px;">${m.type}</td>
                        <td style="padding:10px;">${m.size}</td>
                    </tr>
                `).join("");

                loading.style.display = "none";
                container.style.display = "block";

                // Wire up select all
                const selectAll = dialog.querySelector("#tki-select-all");
                const checkboxes = dialog.querySelectorAll(".tki-model-select");
                selectAll.checked = false;
                selectAll.onchange = () => checkboxes.forEach(cb => cb.checked = selectAll.checked);

            } catch (e) {
                dialog.close();
                document.body.removeChild(dialog);
                showConnectionError();
            }
        };

        refreshData();

        dialog.querySelector("#tki-cleanup-cancel").onclick = () => {
            dialog.close();
            document.body.removeChild(dialog);
        };

        dialog.querySelector("#tki-cleanup-archive").onclick = async () => {
            const checkboxes = dialog.querySelectorAll(".tki-model-select");
            const selectedPaths = Array.from(checkboxes)
                .filter(cb => cb.checked)
                .map(cb => cb.getAttribute("data-path"));

            if (selectedPaths.length === 0) return alert("Bitte wähle mindestens ein Modell aus.");

            const status = dialog.querySelector("#tki-cleanup-status");
            const btn = dialog.querySelector("#tki-cleanup-archive");
            btn.disabled = true;
            status.textContent = `Archiviere ${selectedPaths.length} Dateien...`;

            try {
                const res = await fetch("http://127.0.0.1:12345/api/models/archive", {
                    method: "POST",
                    headers: { "Content-Type": "application/json", "Authorization": `Bearer ${apiToken}` },
                    body: JSON.stringify(selectedPaths)
                });
                if (res.ok) {
                    const data = await res.json();
                    alert(`Erfolgreich ${data.archived} Modelle archiviert!`);
                    status.textContent = "";
                    btn.disabled = false;
                    refreshData(); // Reload list
                } else {
                    throw new Error("API Fehler (Status: " + res.status + ")");
                }
            } catch (e) {
                console.error("[Model-Downloader] Archive error:", e);
                btn.disabled = false;
                status.textContent = "";
                if (e.message.includes("fetch") || e.name === "TypeError") {
                    dialog.close();
                    document.body.removeChild(dialog);
                    showConnectionError();
                } else {
                    alert("Fehler: " + e.message);
                }
            }
        };
    };

    const addFAB = () => {
        if (document.getElementById("tki-downloader-fab")) return;
        const fab = document.createElement("div");
        fab.id = "tki-downloader-fab";
        fab.innerHTML = "🚀";
        fab.style = "position:fixed; bottom:30px; right:30px; z-index:10000; cursor:pointer; font-size:30px; background:#ffcc00; border-radius:50%; width:60px; height:60px; display:flex; align-items:center; justify-content:center; box-shadow:0 0 20px rgba(255,204,0,0.5); border: 2px solid white; transition: transform 0.2s;";
        fab.onclick = sendWorkflow;
        document.body.appendChild(fab);
    };

    const addV2Action = () => {
        if (app?.ui?.menu?.addPrimaryAction) {
            app.ui.menu.addPrimaryAction({ id: "tki.model-downloader.send", icon: "pi pi-download", label: "Send to Downloader", callback: sendWorkflow });
            app.ui.menu.addPrimaryAction({ id: "tki.model-downloader.cleanup", icon: "pi pi-trash", label: "Aufräumen / Archivieren", callback: openCleanupDialog });
        }
    };

    const addClassic = () => {
        const menu = document.querySelector(".comfy-menu");
        if (menu) {
            if (!document.getElementById("tki-downloader-classic")) {
                const btn = document.createElement("button");
                btn.id = "tki-downloader-classic";
                btn.textContent = "🚀 Downloader";
                btn.style.background = "#ffcc00"; btn.style.color = "white";
                btn.onclick = sendWorkflow;
                menu.appendChild(btn);
            }
            if (!document.getElementById("tki-cleanup-classic")) {
                const cleanBtn = document.createElement("button");
                cleanBtn.id = "tki-cleanup-classic";
                cleanBtn.textContent = "🧹 Aufräumen";
                cleanBtn.style.background = "#444"; cleanBtn.style.color = "white"; cleanBtn.style.marginLeft = "5px";
                cleanBtn.onclick = openCleanupDialog;
                menu.appendChild(cleanBtn);
            }
        }
    };

    addFAB(); addClassic(); addV2Action();
};

if (app) {
    app.registerExtension({ name: "TKI.ModelDownloader", async setup() { await initializeExtension(); } });
} else if (document.readyState === "complete") {
    initializeExtension();
} else {
    window.addEventListener("load", initializeExtension);
}
