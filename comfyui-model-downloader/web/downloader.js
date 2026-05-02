// Try different ways to get the ComfyUI app object
let app = null;

const tryImports = async () => {
    const paths = [
        "../../scripts/app.js",
        "/scripts/app.js",
        "../../../scripts/app.js"
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
};

await tryImports();

console.log("%c[Model-Downloader] DEBUG: JS File Loaded", "background: #222; color: #ffcc00; font-size: 20px;");

// Define setup logic that can be called by extension or as fallback
const initializeExtension = async () => {
    console.log("%c[Model-Downloader] DEBUG: Initializing Extension Logic", "background: #222; color: #ffcc00; font-size: 16px;");
    
    let apiToken = null;

    // Load token from local config.json (generated during installation)
    const loadConfig = async () => {
        try {
            // Use import.meta.url to get the correct relative path for the extension
            const baseUrl = new URL(".", import.meta.url).href;
            const configUrl = `${baseUrl}config.json?v=${Date.now()}`;
            
            console.log("[Model-Downloader] Fetching config from:", configUrl);
            const response = await fetch(configUrl);
            if (response.ok) {
                const config = await response.json();
                apiToken = config.token;
                if (apiToken) {
                    console.log("[Model-Downloader] Pre-shared Token loaded.");
                } else {
                    console.warn("[Model-Downloader] Token in config.json is empty!");
                }
            } else {
                console.warn("[Model-Downloader] config.json not found (Status: " + response.status + "). Did you run the installer in the Java App?");
            }
        } catch (e) {
            console.error("[Model-Downloader] Failed to load config.json:", e);
        }
    };

    // Trigger config load in background
    loadConfig();

    const sendWorkflow = async () => {
        if (!app || !app.graph) {
            console.error("[Model-Downloader] App or Graph not ready.");
            const notify = (msg) => {
                if (window.comfyAPI?.dialog?.show) window.comfyAPI.dialog.show(msg);
                else alert(msg);
            };
            notify("❌ Fehler: ComfyUI Graph nicht bereit.");
            return;
        }
        const workflow = app.graph.serialize();
        console.log("[Model-Downloader] Workflow:", workflow);
        
        const notify = (msg) => {
            if (app.ui?.dialog?.show) app.ui.dialog.show(msg);
            else if (window.comfyAPI?.dialog?.show) window.comfyAPI.dialog.show(msg);
            else alert(msg);
        };

        if (!apiToken) {
            notify("❌ Sicherheits-Fehler: Kein API-Token gefunden. Bitte führe den Installer in der Java-App aus.");
            return;
        }

        try {
            console.log("[Model-Downloader] Sending request to 127.0.0.1:12345...");
            const response = await fetch("http://127.0.0.1:12345/import", {
                method: "POST",
                mode: "cors",
                headers: { 
                    "Content-Type": "application/json",
                    "Authorization": `Bearer ${apiToken}`
                },
                body: JSON.stringify(workflow)
            });
            
            if (response.ok) {
                notify("🚀 Workflow erfolgreich gesendet!");
            } else if (response.status === 401) {
                notify("❌ Sicherheits-Fehler: Token ungültig. Bitte Downloader neu starten.");
                apiToken = null; 
            } else {
                notify("❌ Fehler: Downloader abgelehnt (Status: " + response.status + ")");
            }
        } catch (e) {
            notify("❌ Keine Verbindung zum Downloader-Tool! Läuft die Java-App?");
        }
    };

    // Strategy 1: The Modern FAB (Always works if DOM is ready)
    const addFAB = () => {
        if (document.getElementById("tki-downloader-fab")) return;
        console.log("[Model-Downloader] Adding FAB...");
        const fab = document.createElement("div");
        fab.id = "tki-downloader-fab";
        fab.innerHTML = "🚀";
        fab.title = "Send to Model Downloader";
        fab.style = "position:fixed; bottom:30px; right:30px; z-index:10000; cursor:pointer; font-size:30px; background:#ffcc00; border-radius:50%; width:60px; height:60px; display:flex; align-items:center; justify-content:center; box-shadow:0 0 20px rgba(255,105,180,0.5); border: 2px solid white; transition: transform 0.2s;";
        fab.onmouseover = () => fab.style.transform = "scale(1.1)";
        fab.onmouseout = () => fab.style.transform = "scale(1.0)";
        fab.onclick = sendWorkflow;
        document.body.appendChild(fab);
    };

    // Strategy 2: Primary Action (V2 UI)
    const addV2Action = () => {
        if (app?.ui?.menu?.addPrimaryAction) {
            console.log("[Model-Downloader] Adding to V2 Menu...");
            app.ui.menu.addPrimaryAction({
                id: "tki.model-downloader.send",
                icon: "pi pi-download",
                label: "Send to Downloader",
                callback: sendWorkflow
            });
        }
        if (app?.ui?.toolbar?.addPrimaryAction) {
             console.log("[Model-Downloader] Adding to V2 Toolbar...");
             app.ui.toolbar.addPrimaryAction({
                id: "tki.model-downloader.send-toolbar",
                icon: "pi pi-download",
                label: "Send to Downloader",
                callback: sendWorkflow
            });
        }
    };

    // Strategy 3: Classic Menu
    const addClassic = () => {
        const menu = document.querySelector(".comfy-menu");
        if (menu) {
            if (document.getElementById("tki-downloader-classic")) return;
            console.log("[Model-Downloader] Adding to Classic Menu...");
            const btn = document.createElement("button");
            btn.id = "tki-downloader-classic";
            btn.textContent = "🚀 Downloader";
            btn.style.background = "#ffcc00";
            btn.style.color = "white";
            btn.onclick = sendWorkflow;
            menu.appendChild(btn);
        }
    };

    // Run immediately
    addFAB();
    addClassic();
    addV2Action();
    
    // Retry with delays to ensure UI is ready
    setTimeout(addFAB, 1000);
    setTimeout(addFAB, 5000);
    setTimeout(addClassic, 2000);
    setTimeout(addV2Action, 3000);
};

if (app) {
    app.registerExtension({
        name: "TKI.ModelDownloader",
        async setup() {
            await initializeExtension();
        }
    });
} else {
    console.warn("[Model-Downloader] App object not found. Falling back to manual initialization.");
    // Fallback if app is not found or registration fails
    if (document.readyState === "complete") {
        initializeExtension();
    } else {
        window.addEventListener("load", initializeExtension);
    }
}
