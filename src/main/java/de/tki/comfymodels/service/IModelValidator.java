package de.tki.comfymodels.service;

import java.io.File;

public interface IModelValidator {
    ValidationResult validateFile(File file);
    String calculateHash(File file);

    class ValidationResult {
        public final boolean ok;
        public final String message;
        public final String filePath;

        public ValidationResult(boolean ok, String message, String filePath) {
            this.ok = ok;
            this.message = message;
            this.filePath = filePath;
        }
    }
}
