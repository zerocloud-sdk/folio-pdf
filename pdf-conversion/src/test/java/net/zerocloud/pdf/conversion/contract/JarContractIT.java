package net.zerocloud.pdf.conversion.contract;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.Test;

public final class JarContractIT {

    private static final int JAVA_8_CLASS_MAJOR_VERSION = 52;

    @Test
    public void shippedProviderArtifactsHaveStableNamesJava8ClassesAndBomEntries()
            throws Exception {
        inspectJar(
                Paths.get(requiredProperty("providerArtifactPath")),
                "net.zerocloud.pdf.provider",
                "net/zerocloud/pdf/provider/CapabilityProvider.class");
        inspectJar(
                Paths.get(requiredProperty("conversionArtifactPath")),
                "net.zerocloud.pdf.conversion",
                "net/zerocloud/pdf/conversion/SubprocessCapabilityProvider.class");

        Path bom = Paths.get(requiredProperty("repositoryRoot"))
                .resolve("pdf-bom/pom.xml");
        String bomText = new String(
                Files.readAllBytes(bom),
                StandardCharsets.UTF_8);
        assertTrue(bomText.contains(
                "<artifactId>pdf-provider-contract</artifactId>"));
        assertTrue(bomText.contains(
                "<artifactId>pdf-conversion</artifactId>"));
    }

    private static void inspectJar(
            Path artifact,
            String moduleName,
            String requiredClass) throws Exception {
        try (JarFile jar = new JarFile(artifact.toFile())) {
            assertNotNull(jar.getEntry(requiredClass));
            assertNotNull(jar.getEntry("META-INF/LICENSE"));
            assertNotNull(jar.getEntry("META-INF/NOTICE"));
            assertEquals(
                    moduleName,
                    jar.getManifest().getMainAttributes().getValue(
                            new Attributes.Name("Automatic-Module-Name")));
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                assertFalse("Dependencies must not be bundled: " + entry.getName(),
                        entry.getName().startsWith("org/apache/pdfbox/"));
                if (entry.getName().endsWith(".class")) {
                    assertJava8Class(jar, entry);
                }
            }
        }
    }

    private static void assertJava8Class(JarFile jar, JarEntry entry)
            throws IOException {
        try (InputStream input = jar.getInputStream(entry);
                DataInputStream data = new DataInputStream(input)) {
            assertEquals(0xCAFEBABE, data.readInt());
            data.readUnsignedShort();
            assertEquals("Wrong class-file version for " + entry.getName(),
                    JAVA_8_CLASS_MAJOR_VERSION, data.readUnsignedShort());
        }
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing system property: " + name);
        }
        return value;
    }
}
