package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import java.util.List;

public interface IModelAnalyzer {
    List<ModelInfo> analyze(String jsonText, String fileName);
}
