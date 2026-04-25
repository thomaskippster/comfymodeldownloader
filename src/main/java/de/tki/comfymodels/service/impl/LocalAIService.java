package de.tki.comfymodels.service.impl;

import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.util.*;

@Service
public class LocalAIService {

    private final Map<String, String[]> KNOWLEDGE_BASE = new LinkedHashMap<>();
    private final Map<String, Double> GLOBAL_IDF = new HashMap<>();

    @PostConstruct
    public void init() {
        // --- TOP RESEARCH LABS ---
        add("black-forest-labs", "flux, flux1, flux2, bfl, schnell, dev, pro, mistral");
        add("stabilityai", "sdxl, sd15, sd21, sd3, svd, cascade, turbo, lightning, stable, diffusion");
        add("Wan-AI", "wan, wan2, wan2.1, wan2.2, ti2v, t2v");
        add("nvidia", "cosmos, world, tokenizer");
        add("genmo", "mochi");
        add("Comfy-Org", "repack, flux_text_encoders, wan_repackaged, mochi_repackaged, lumina");
        add("TencentARC", "hunyuan, hyt");
        
        // --- TOP COMMUNITY CREATORS ---
        add("Lykon", "dreamshaper, absolute, reality, anytime, alys");
        add("PonyDiffusion", "pony, v6, v7, score_9, rating_explicit");
        add("XpucT", "deliberate, real-vision");
        add("city96", "gguf, quantized");
        add("Kijai", "kj, nodes, flux-fp8");
        add("6tZ", "aesthetic, flux_lora");
        add("Peli86", "realism, enhance");
        add("lllyasviel", "controlnet, adapter, annotator, depth, canny, scribble");
        
        calculateIdf();
    }

    private void add(String provider, String keywords) {
        KNOWLEDGE_BASE.put(provider, keywords.split(", "));
    }

    private void calculateIdf() {
        Set<String> allTerms = new HashSet<>();
        for (String[] terms : KNOWLEDGE_BASE.values()) allTerms.addAll(Arrays.asList(terms));
        int docCount = KNOWLEDGE_BASE.size();
        for (String term : allTerms) {
            long count = KNOWLEDGE_BASE.values().stream().filter(list -> Arrays.asList(list).contains(term)).count();
            GLOBAL_IDF.put(term, Math.log((double) docCount / (1.0 + count)));
        }
    }

    public Prediction predictProvider(String fileName) {
        String name = fileName.toLowerCase();
        // Special check for complex filenames
        if (name.contains("mistral") && name.contains("flux2")) return new Prediction("black-forest-labs", 0.95);
        if (name.contains("pony") || name.contains("v6")) return new Prediction("PonyDiffusion", 0.85);

        List<String> queryTokens = Arrays.asList(name.split("[_\\- \\.]"));
        Map<String, Double> similarityScores = new HashMap<>();

        for (Map.Entry<String, String[]> entry : KNOWLEDGE_BASE.entrySet()) {
            String provider = entry.getKey();
            double score = 0.0;
            for (String token : queryTokens) {
                if (token.length() < 2) continue;
                for (String docTerm : entry.getValue()) {
                    if (token.equals(docTerm) || (token.contains(docTerm) && docTerm.length() > 3)) {
                        score += GLOBAL_IDF.getOrDefault(docTerm, 1.0) * (docTerm.length() / 4.0);
                    }
                }
            }
            if (score > 0) similarityScores.put(provider, score);
        }

        String bestProvider = "community";
        double maxScore = 0.0;
        for (Map.Entry<String, Double> entry : similarityScores.entrySet()) {
            if (entry.getValue() > maxScore) { maxScore = entry.getValue(); bestProvider = entry.getKey(); }
        }

        return new Prediction(bestProvider, Math.min(0.99, maxScore / 3.5));
    }

    public class Prediction {
        public final String provider;
        public final double confidence;
        public Prediction(String provider, double confidence) { this.provider = provider; this.confidence = confidence; }
        public String getLabel() {
            if (confidence > 0.75) return "🧠 AI Verified: " + provider;
            if (confidence > 0.35) return "🎯 AI Predicted: " + provider;
            return "👥 Community";
        }
    }
}
