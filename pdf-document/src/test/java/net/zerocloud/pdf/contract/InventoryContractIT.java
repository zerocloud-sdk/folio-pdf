package net.zerocloud.pdf.contract;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

public final class InventoryContractIT {

    @Test
    public void capabilityMatrixRecordsExperimentalT01EvidenceAndProvenance() throws Exception {
        Path root = repositoryRoot();
        Map<String, Object> matrix = loadYaml(root.resolve("capabilities/capability-matrix.yaml"));

        assertEquals(Integer.valueOf(1), matrix.get("schema-version"));
        assertEquals("behavioral-capability", matrix.get("authority"));

        List<Map<String, Object>> capabilities = listOfMaps(matrix.get("capabilities"));
        assertEquals(1, capabilities.size());
        Map<String, Object> capability = capabilities.get(0);
        assertEquals("document.blank.create-publish-reopen", capability.get("id"));
        assertEquals("document-engine", capability.get("context"));
        assertEquals("experimental", capability.get("status"));
        assertTrue(list(capability.get("certified-platforms")).isEmpty());
        assertFalse(listOfMaps(capability.get("evidence")).isEmpty());

        Map<String, Object> provenance = map(capability.get("provenance"));
        assertEquals("PROVENANCE.md", provenance.get("path"));
        assertTrue(Files.isRegularFile(root.resolve(provenance.get("path").toString())));

        List<Map<String, Object>> promotionGates = listOfMaps(capability.get("promotion-gates"));
        assertEquals("T06", promotionGates.get(0).get("ticket"));
    }

    @Test
    public void facadeSurfaceSeedHasNoStableOrPreviewT01Mapping() throws Exception {
        Map<String, Object> manifest = loadYaml(
                repositoryRoot().resolve("capabilities/facade-surface.yaml"));

        assertEquals("migration-source-surface", manifest.get("authority"));
        Map<String, Object> surfaces = map(manifest.get("surfaces"));
        assertTrue(list(surfaces.get("stable")).isEmpty());
        assertTrue(list(surfaces.get("preview")).isEmpty());

        List<Map<String, Object>> excluded = listOfMaps(manifest.get("excluded-capabilities"));
        assertEquals(1, excluded.size());
        assertEquals("document.blank.create-publish-reopen", excluded.get(0).get("id"));
        assertEquals("T01", excluded.get(0).get("ticket"));
    }

    @Test
    public void rootReactorContainsOnlyTheT01Modules() throws Exception {
        String pom = new String(
                Files.readAllBytes(repositoryRoot().resolve("pom.xml")),
                "UTF-8");
        assertTrue(pom.contains("<maven.compiler.release>8</maven.compiler.release>"));
        assertTrue(pom.contains("<maven.compiler.plugin.version>3.15.0</maven.compiler.plugin.version>"));

        int modulesStart = pom.indexOf("<modules>");
        int modulesEnd = pom.indexOf("</modules>", modulesStart);
        String modules = pom.substring(modulesStart, modulesEnd);
        assertEquals(Arrays.asList("pdf-bom", "pdf-document"),
                extractModuleNames(modules));
    }

    private static List<String> extractModuleNames(String modules) {
        java.util.ArrayList<String> names = new java.util.ArrayList<String>();
        int cursor = 0;
        while (true) {
            int start = modules.indexOf("<module>", cursor);
            if (start < 0) {
                return names;
            }
            int valueStart = start + "<module>".length();
            int end = modules.indexOf("</module>", valueStart);
            names.add(modules.substring(valueStart, end).trim());
            cursor = end + "</module>".length();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return (Map<String, Object>) new Yaml().load(input);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return (List<Object>) value;
    }

    private static Path repositoryRoot() {
        String value = System.getProperty("repositoryRoot");
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing system property: repositoryRoot");
        }
        return Paths.get(value);
    }
}
