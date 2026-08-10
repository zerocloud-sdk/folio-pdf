package net.zerocloud.pdf.contract;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
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
    public void jarHasStableModuleNameAndJava8Classes() throws Exception {
        Path artifact = Paths.get(requiredProperty("artifactPath"));

        try (JarFile jar = new JarFile(artifact.toFile())) {
            assertNotNull(jar.getEntry("net/zerocloud/pdf/DocumentWorkflow.class"));
            assertNotNull(jar.getEntry("META-INF/LICENSE"));
            assertNotNull(jar.getEntry("META-INF/NOTICE"));
            assertEquals(
                    "net.zerocloud.pdf.document",
                    jar.getManifest().getMainAttributes().getValue(
                            new Attributes.Name("Automatic-Module-Name")));

            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                assertFalse("PDFBox classes must not be bundled in pdf-document",
                        entry.getName().startsWith("org/apache/pdfbox/"));
                if (entry.getName().endsWith(".class")) {
                    assertJava8Class(jar, entry);
                }
            }
        }
    }

    private static void assertJava8Class(JarFile jar, JarEntry entry) throws IOException {
        try (InputStream input = jar.getInputStream(entry);
                DataInputStream data = new DataInputStream(input)) {
            assertEquals("Invalid class-file magic for " + entry.getName(),
                    0xCAFEBABE, data.readInt());
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
