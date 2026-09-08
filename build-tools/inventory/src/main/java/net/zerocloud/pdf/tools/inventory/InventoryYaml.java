package net.zerocloud.pdf.tools.inventory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/** Strict field reader shared by the Foundation authorities and evidence records. */
final class InventoryYaml {
    final Map<String, Object> values;
    final String location;
    final List<String> errors;

    private InventoryYaml(Map<String, Object> values, String location, List<String> errors) {
        this.values = values;
        this.location = location;
        this.errors = errors;
    }

    static InventoryYaml load(Path file, List<String> errors) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setAllowRecursiveKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(30);
        options.setCodePointLimit(3_000_000);
        try (InputStream input = Files.newInputStream(file)) {
            return object(new Yaml(new SafeConstructor(options)).load(input), file.toString(), errors);
        } catch (IOException | YAMLException exception) {
            errors.add(file + ": cannot read YAML: "
                    + exception.getMessage().replace('\n', ' ').replace('\r', ' '));
            return new InventoryYaml(Collections.<String, Object>emptyMap(), file.toString(), errors);
        }
    }

    static InventoryYaml object(Object value, String location, List<String> errors) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (!(value instanceof Map<?, ?>)) {
            errors.add(location + ": expected a mapping");
        } else {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    errors.add(location + ": mapping keys must be strings");
                } else {
                    result.put((String) entry.getKey(), entry.getValue());
                }
            }
        }
        return new InventoryYaml(result, location, errors);
    }

    void keys(String... allowed) {
        for (String key : values.keySet()) {
            if (!Arrays.asList(allowed).contains(key)) {
                error("unsupported field " + key);
            }
        }
    }

    String string(String key) {
        Object value = values.get(key);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            error(key + ": expected a nonblank string");
            return "";
        }
        return ((String) value).trim();
    }

    String optional(String key) {
        return values.containsKey(key) ? string(key) : "";
    }

    int integer(String key) {
        Object value = values.get(key);
        if (!(value instanceof Integer)) {
            error(key + ": expected an integer");
            return -1;
        }
        return ((Integer) value).intValue();
    }

    InventoryYaml object(String key) {
        return object(values.get(key), location + "." + key, errors);
    }

    List<Object> list(String key) {
        Object value = values.get(key);
        if (!(value instanceof List<?>)) {
            error(key + ": expected a sequence");
            return Collections.emptyList();
        }
        return new ArrayList<Object>((List<?>) value);
    }

    List<String> strings(String key) {
        List<String> result = new ArrayList<String>();
        for (Object value : list(key)) {
            if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
                error(key + ": expected nonblank strings");
            } else if (result.contains(value)) {
                error(key + ": duplicate value " + value);
            } else {
                result.add(((String) value).trim());
            }
        }
        return result;
    }

    List<String> arguments(String key) {
        List<String> result = new ArrayList<String>();
        for (Object value : list(key)) {
            if (!(value instanceof String)) {
                error(key + ": expected string arguments");
            } else {
                // Argument order, repeated values and empty arguments are significant.
                result.add((String) value);
            }
        }
        return result;
    }

    List<InventoryYaml> objects(String key) {
        List<InventoryYaml> result = new ArrayList<InventoryYaml>();
        for (Object value : list(key)) {
            result.add(object(value, location + "." + key + "[" + result.size() + "]", errors));
        }
        return result;
    }

    void error(String message) {
        errors.add(location + ": " + message);
    }
}
