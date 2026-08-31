package net.zerocloud.pdf.tools.release;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

final class ReleaseModuleSet {

    final List<Module> publishable;
    final Set<String> repositoryOnly;
    final Set<String> resourceContracts;

    private ReleaseModuleSet(List<Module> publishable, Set<String> repositoryOnly,
            Set<String> resourceContracts) {
        this.publishable = Collections.unmodifiableList(publishable);
        this.repositoryOnly = Collections.unmodifiableSet(repositoryOnly);
        this.resourceContracts = Collections.unmodifiableSet(resourceContracts);
    }

    static ReleaseModuleSet read(Path repositoryRoot, List<String> errors) {
        Path path = repositoryRoot.resolve("release/modules.properties");
        if (!Files.isRegularFile(path)) {
            errors.add("release/modules.properties is required");
            return empty();
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException e) {
            errors.add("cannot read release/modules.properties: " + compact(e.getMessage()));
            return empty();
        }
        if (!"1".equals(properties.getProperty("format-version"))) {
            errors.add("release/modules.properties format-version must be 1");
        }
        List<Module> modules = new ArrayList<Module>();
        Set<String> names = new HashSet<String>();
        String configured = properties.getProperty("publishable", "");
        for (String item : split(configured)) {
            String[] fields = item.split(":", -1);
            if (fields.length != 2 || !fields[0].matches("pdf-[a-z0-9-]+")
                    || !("pom".equals(fields[1]) || "jar".equals(fields[1]))) {
                errors.add("invalid publishable module entry: " + item);
                continue;
            }
            if (!names.add(fields[0])) {
                errors.add("duplicate publishable module entry: " + fields[0]);
                continue;
            }
            modules.add(new Module(fields[0], fields[1]));
        }
        if (modules.isEmpty()) {
            errors.add("release/modules.properties must name publishable modules");
        }
        Set<String> repositoryOnly = new HashSet<String>(
                split(properties.getProperty("repository-only", "")));
        Set<String> resourceContracts = new HashSet<String>(
                split(properties.getProperty("resource-contract", "")));
        for (String name : repositoryOnly) {
            if (names.contains(name)) {
                errors.add("module cannot be both publishable and repository-only: " + name);
            }
        }
        for (String name : resourceContracts) {
            if (!names.contains(name)) {
                errors.add("resource-contract module is not publishable: " + name);
            }
        }
        return new ReleaseModuleSet(modules, repositoryOnly, resourceContracts);
    }

    private static ReleaseModuleSet empty() {
        return new ReleaseModuleSet(new ArrayList<Module>(), new HashSet<String>(),
                new HashSet<String>());
    }

    private static List<String> split(String value) {
        List<String> result = new ArrayList<String>();
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static String compact(String value) {
        return value == null ? "unknown error" : value.replace('\n', ' ').replace('\r', ' ');
    }

    static final class Module {
        final String artifactId;
        final String packaging;

        private Module(String artifactId, String packaging) {
            this.artifactId = artifactId;
            this.packaging = packaging;
        }
    }
}
