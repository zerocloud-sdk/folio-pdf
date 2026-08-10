package net.zerocloud.pdf.tools.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ValidationResult {

    private final InventoryModel model;
    private final List<String> errors;

    ValidationResult(InventoryModel model, List<String> errors) {
        this.model = model;
        this.errors = Collections.unmodifiableList(new ArrayList<String>(errors));
    }

    InventoryModel model() {
        return model;
    }

    List<String> errors() {
        return errors;
    }

    boolean isValid() {
        return errors.isEmpty();
    }
}
