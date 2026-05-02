package de.tki.comfymodels.domain;

public enum ModelFolder {
    CHECKPOINTS("checkpoints"),
    LORAS("loras"),
    VAE("vae"),
    CLIP("clip"),
    CONTROLNET("controlnet"),
    UPSCALE_MODELS("upscale_models"),
    VAEECODE("vae_approx"),
    UNET("unet"),
    CLIP_VISION("clip_vision"),
    STYLE_MODELS("style_models"),
    HYPERNETWORKS("hypernetworks"),
    PHOTOMAKER("photomaker"),
    IP_ADAPTER("ipadapter"),
    ONNX("onnx"),
    LLM("llm"),
    DIFFUSION_MODELS("diffusion_models"),
    GLIGEN("gligen"),
    EMBEDDINGS("embeddings"),
    AUDIO_ENCODERS("audio_encoders"),
    DIFFUSERS("diffusers"),
    CONFIGS("configs"),
    MODEL_PATCHES("model_patches"),
    LATENT_UPSCALE_MODELS("latent_upscale_models"),
    ARCHIVE("archive");

    private final String defaultFolderName;

    ModelFolder(String defaultFolderName) {
        this.defaultFolderName = defaultFolderName;
    }

    public String getDefaultFolderName() {
        return defaultFolderName;
    }

    public static ModelFolder fromString(String text) {
        if (text == null) return CHECKPOINTS;
        for (ModelFolder b : ModelFolder.values()) {
            if (b.defaultFolderName.equalsIgnoreCase(text) || b.name().equalsIgnoreCase(text)) {
                return b;
            }
        }
        
        // Handle common variations
        String lower = text.toLowerCase();
        if (lower.contains("checkpoint")) return CHECKPOINTS;
        if (lower.contains("lora")) return LORAS;
        if (lower.contains("vae")) return VAE;
        if (lower.contains("controlnet")) return CONTROLNET;
        if (lower.contains("embedding")) return EMBEDDINGS;
        
        return CHECKPOINTS; // Default fallback
    }
}
