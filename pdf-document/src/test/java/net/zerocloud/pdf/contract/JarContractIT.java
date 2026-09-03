package net.zerocloud.pdf.contract;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.DataInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Locale;
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
            String notice = readText(
                    jar,
                    jar.getJarEntry("META-INF/NOTICE"));
            assertTrue(notice.contains("TwelveMonkeys ImageIO 3.14.0"));
            assertTrue(notice.contains("Copyright (c) 2008-2020, Harald Kuhr"));
            assertTrue(notice.contains("BSD 3-Clause License"));
            assertTrue(notice.contains(
                    "docs/third-party/twelvemonkeys-imageio-tiff-3.14.0.md"));

            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                assertFalse("PDFBox classes must not be bundled in pdf-document",
                        entry.getName().startsWith("org/apache/pdfbox/"));
                assertFalse("FontBox classes must not be bundled in pdf-document",
                        entry.getName().startsWith("org/apache/fontbox/"));
                assertFalse("TwelveMonkeys classes must not be bundled in pdf-document",
                        entry.getName().startsWith("com/twelvemonkeys/"));
                String lowerName = entry.getName().toLowerCase(Locale.ROOT);
                assertFalse("Font fixtures must not be bundled in pdf-document",
                        lowerName.endsWith(".ttf")
                                || lowerName.endsWith(".otf")
                                || lowerName.endsWith(".woff")
                                || lowerName.endsWith(".woff2")
                                || lowerName.endsWith(".ttf.base64")
                                || lowerName.endsWith(".otf.base64"));
                if (entry.getName().endsWith(".class")) {
                    assertJava8Class(jar, entry);
                }
            }
        }

        Path repository = Paths.get(requiredProperty("repositoryRoot"));
        String parentPom = new String(
                Files.readAllBytes(repository.resolve("pom.xml")),
                StandardCharsets.UTF_8);
        assertTrue(parentPom.contains(
                "<twelvemonkeys.version>3.14.0</twelvemonkeys.version>"));
        String documentPom = new String(
                Files.readAllBytes(repository.resolve("pdf-document/pom.xml")),
                StandardCharsets.UTF_8);
        int dependency = documentPom.indexOf(
                "<artifactId>imageio-tiff</artifactId>");
        assertTrue("Missing TwelveMonkeys TIFF dependency", dependency >= 0);
        int dependencyEnd = documentPom.indexOf("</dependency>", dependency);
        assertTrue("Incomplete TwelveMonkeys TIFF dependency",
                dependencyEnd > dependency);
        String declaration = documentPom.substring(dependency, dependencyEnd);
        assertTrue(declaration.contains(
                "<version>${twelvemonkeys.version}</version>"));
        assertTrue(declaration.contains("<optional>true</optional>"));
    }

    private static String readText(JarFile jar, JarEntry entry)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        try (InputStream input = jar.getInputStream(entry)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
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
