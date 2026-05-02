package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.ICleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CleanupService implements ICleanupService {

    private final ConfigService configService;
    private final ArchiveService archiveService;

    @Autowired
    public CleanupService(ConfigService configService, ArchiveService archiveService) {
        this.configService = configService;
        this.archiveService = archiveService;
    }

    @Override
    public List<ModelInfo> findUnusedModels(int monthsThreshold) {
        List<ModelInfo> unused = new ArrayList<>();
        String modelsPathStr = configService.getModelsPath();
        if (modelsPathStr == null || modelsPathStr.isEmpty()) return unused;

        Path root = Paths.get(modelsPathStr);
        if (!Files.exists(root)) return unused;

        Instant threshold = Instant.now().minus(monthsThreshold * 30L, ChronoUnit.DAYS);

        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> candidates = walk.filter(Files::isRegularFile)
                    .filter(this::isSupportedModel)
                    .collect(Collectors.toList());

            for (Path p : candidates) {
                BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                // On some systems lastAccessTime might not be available or updated, 
                // so we use the newer of lastAccess and lastModified.
                Instant lastUsed = attrs.lastAccessTime().toInstant();
                if (attrs.lastModifiedTime().toInstant().isAfter(lastUsed)) {
                    lastUsed = attrs.lastModifiedTime().toInstant();
                }

                if (lastUsed.isBefore(threshold)) {
                    ModelInfo info = createInfo(p, root, attrs);
                    unused.add(info);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return unused;
    }

    @Override
    public void archiveModels(List<ModelInfo> models, BiConsumer<Integer, String> onStatusUpdate, Runnable onFinished) {
        new Thread(() -> {
            for (int i = 0; i < models.size(); i++) {
                ModelInfo model = models.get(i);
                if (onStatusUpdate != null) onStatusUpdate.accept(i, "📦 Archiving: " + model.getName());
                try {
                    String relPath = model.getSave_path() + "/" + model.getName();
                    archiveService.moveToArchiveWithProgress(relPath, null);
                    if (onStatusUpdate != null) onStatusUpdate.accept(i, "✅ Archived");
                } catch (IOException e) {
                    if (onStatusUpdate != null) onStatusUpdate.accept(i, "❌ Error: " + e.getMessage());
                }
            }
            if (onFinished != null) onFinished.run();
        }).start();
    }

    private boolean isSupportedModel(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        return n.endsWith(".safetensors") || n.endsWith(".ckpt") || n.endsWith(".pt") || n.endsWith(".pth") || n.endsWith(".bin");
    }

    private ModelInfo createInfo(Path p, Path root, BasicFileAttributes attrs) {
        ModelInfo info = new ModelInfo();
        info.setName(p.getFileName().toString());
        Path rel = root.relativize(p);
        info.setSave_path(rel.getParent() != null ? rel.getParent().toString().replace("\\", "/") : "root");
        info.setType(info.getSave_path());
        
        long bytes = attrs.size();
        double mb = bytes / (1024.0 * 1024.0);
        info.setSize(String.format("%.2f MB", mb));
        info.setByteSize(bytes);
        
        return info;
    }
}
