package de.tki.comfymodels.service;

import de.tki.comfymodels.service.impl.ConfigService;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import de.tki.comfymodels.Main.AppConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class DeepModelDiagnosticTest {

    @Test
    public void inspectNodeOptions() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        ConfigService configService = context.getBean(ConfigService.class);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        
        String targetModel = "ltx2.3-transition.safetensors";
        String url = configService.getComfyUIUrl() + "/object_info";
        
        System.out.println("--- DEEP API INSPECTION ---");
        System.out.println("Target: " + targetModel);
        System.out.println("Querying: " + url);

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            System.out.println("❌ API Error: " + response.statusCode());
            return;
        }

        JSONObject allNodes = new JSONObject(response.body());
        String[] nodesToInspect = {"LoraLoader", "CheckpointLoaderSimple", "CheckpointLoader", "LoraLoaderModelOnly"};

        boolean foundInAny = false;
        for (String nodeName : nodesToInspect) {
            if (allNodes.has(nodeName)) {
                System.out.println("\nChecking Node: " + nodeName);
                JSONObject node = allNodes.getJSONObject(nodeName);
                if (node.has("input")) {
                    JSONObject input = node.getJSONObject("input");
                    JSONObject required = input.optJSONObject("required");
                    if (required != null) {
                        for (String key : required.keySet()) {
                            JSONArray param = required.optJSONArray(key);
                            if (param != null && param.length() > 0 && param.get(0) instanceof JSONArray) {
                                JSONArray options = param.getJSONArray(0);
                                System.out.println("  Parameter '" + key + "' has " + options.length() + " options.");
                                
                                List<String> matches = new ArrayList<>();
                                for (int i = 0; i < options.length(); i++) {
                                    String opt = options.getString(i);
                                    if (opt.toLowerCase().contains("ltx") || opt.equalsIgnoreCase(targetModel)) {
                                        matches.add(opt);
                                    }
                                }
                                
                                if (!matches.isEmpty()) {
                                    System.out.println("  🎯 POTENTIAL MATCHES FOUND:");
                                    for (String m : matches) {
                                        System.out.println("    - " + m);
                                        if (m.equals(targetModel)) foundInAny = true;
                                    }
                                } else {
                                    System.out.println("  (No LTX-related models in this list)");
                                }
                            }
                        }
                    }
                }
            } else {
                System.out.println("\nNode not found: " + nodeName);
            }
        }

        if (foundInAny) {
            System.out.println("\n✅ VERIFIED: The model is technically present in the API options.");
            System.out.println("If it is missing in the GUI, it might be a caching issue in the browser or a custom node conflict.");
        } else {
            System.out.println("\n❌ FAILED: The model is NOT present in any examined node parameters.");
        }

        context.close();
    }
}
