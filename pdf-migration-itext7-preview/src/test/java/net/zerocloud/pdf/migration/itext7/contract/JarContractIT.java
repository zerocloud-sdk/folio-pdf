package net.zerocloud.pdf.migration.itext7.contract;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfPage;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;
import org.junit.Test;

public final class JarContractIT {

    private static final int JAVA_8_CLASS_MAJOR_VERSION = 52;
    private static final String EDITION_MARKER =
            "META-INF/folio-pdf/migration-itext7.edition";

    @Test
    public void previewIsJava8StrictSupersetWithExactMappedSurface() throws Exception {
        Path previewArtifact = Paths.get(ContractTestProperties.required("artifactPath"));
        Path stableArtifact = Paths.get(
                ContractTestProperties.required("stableArtifactPath"));
        Path repositoryRoot = Paths.get(
                ContractTestProperties.required("repositoryRoot"));

        Set<String> expectedPreviewMappings = new LinkedHashSet<String>(Arrays.asList(
                "net/zerocloud/pdf/itext7/kernel/exceptions/PdfException.class",
                "net/zerocloud/pdf/itext7/kernel/pdf/PdfDocument.class",
                "net/zerocloud/pdf/itext7/kernel/pdf/PdfPage.class",
                "net/zerocloud/pdf/itext7/kernel/pdf/PdfReader.class",
                "net/zerocloud/pdf/itext7/kernel/pdf/PdfWriter.class",
                "net/zerocloud/pdf/itext7/layout/Document.class"));

        try (JarFile preview = new JarFile(previewArtifact.toFile());
                JarFile stable = new JarFile(stableArtifact.toFile())) {
            assertNotNull(stable.getEntry(EDITION_MARKER));
            assertNotNull(stable.getEntry("META-INF/LICENSE"));
            assertNotNull(stable.getEntry("META-INF/NOTICE"));
            assertEquals("stable\n", read(stable, stable.getJarEntry(EDITION_MARKER)));
            assertEquals(
                    "net.zerocloud.pdf.migration.itext7",
                    stable.getManifest().getMainAttributes().getValue(
                            new Attributes.Name("Automatic-Module-Name")));

            assertNotNull(preview.getEntry(EDITION_MARKER));
            assertNotNull(preview.getEntry("META-INF/LICENSE"));
            assertNotNull(preview.getEntry("META-INF/NOTICE"));
            assertEquals("preview\n", read(preview, preview.getJarEntry(EDITION_MARKER)));
            assertEquals(
                    "net.zerocloud.pdf.migration.itext7.preview",
                    preview.getManifest().getMainAttributes().getValue(
                            new Attributes.Name("Automatic-Module-Name")));

            Set<String> stableMappings = facadeMappings(stable);
            Set<String> previewMappings = facadeMappings(preview);
            assertTrue(stableMappings.isEmpty());
            assertEquals(expectedPreviewMappings, previewMappings);
            assertTrue(previewMappings.containsAll(stableMappings));
            assertTrue(previewMappings.size() > stableMappings.size());

            Enumeration<JarEntry> stableEntries = stable.entries();
            while (stableEntries.hasMoreElements()) {
                JarEntry entry = stableEntries.nextElement();
                assertFalse("The empty stable facade must not contain classes: "
                                + entry.getName(),
                        entry.getName().endsWith(".class"));
                assertFalse("PDFBox classes must not be bundled in the facade",
                        entry.getName().startsWith("org/apache/pdfbox/"));
            }

            Enumeration<JarEntry> entries = preview.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                assertFalse("PDFBox classes must not be bundled in the facade",
                        entry.getName().startsWith("org/apache/pdfbox/"));
                if (entry.getName().endsWith(".class")) {
                    assertJava8Class(preview, entry);
                }
            }
        }

        assertExactPublicSurface();

        String parentPom = read(repositoryRoot.resolve("pom.xml"));
        String stablePom = read(repositoryRoot.resolve("pdf-migration-itext7/pom.xml"));
        String bomPom = read(repositoryRoot.resolve("pdf-bom/pom.xml"));
        assertTrue(parentPom.contains("<module>pdf-migration-itext7</module>"));
        assertFalse(stablePom.contains("<artifactId>pdf-document</artifactId>"));
        assertTrue(bomPom.contains("<artifactId>pdf-migration-itext7</artifactId>"));
        assertTrue(bomPom.contains(
                "<artifactId>pdf-migration-itext7-preview</artifactId>"));
    }

    private static void assertExactPublicSurface() throws Exception {
        assertConstructors(PdfWriter.class,
                "PdfWriter(java.lang.String) throws java.io.FileNotFoundException");
        assertMethods(PdfWriter.class);

        assertConstructors(PdfReader.class,
                "PdfReader(java.lang.String) throws java.io.IOException");
        assertMethods(PdfReader.class, "void close() throws java.io.IOException");

        assertConstructors(PdfDocument.class,
                "PdfDocument(net.zerocloud.pdf.itext7.kernel.pdf.PdfReader)",
                "PdfDocument(net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter)");
        assertMethods(PdfDocument.class,
                "net.zerocloud.pdf.itext7.kernel.pdf.PdfPage addNewPage()",
                "int getNumberOfPages()",
                "void close()");

        assertConstructors(PdfPage.class);
        assertMethods(PdfPage.class);

        assertConstructors(net.zerocloud.pdf.itext7.layout.Document.class,
                "Document(net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument)");
        assertMethods(net.zerocloud.pdf.itext7.layout.Document.class, "void close()");

        assertConstructors(PdfException.class,
                "PdfException(java.lang.String,java.lang.Throwable)");
        assertMethods(PdfException.class);
    }

    private static void assertConstructors(Class<?> type, String... expected) {
        Set<String> actual = new LinkedHashSet<String>();
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (Modifier.isPublic(constructor.getModifiers())
                    || Modifier.isProtected(constructor.getModifiers())) {
                actual.add(type.getSimpleName()
                        + parameters(constructor.getParameterTypes())
                        + exceptions(constructor.getExceptionTypes()));
            }
        }
        assertEquals(type.getName() + " constructors", ordered(expected), actual);
    }

    private static void assertMethods(Class<?> type, String... expected) {
        Set<String> actual = new LinkedHashSet<String>();
        for (Method method : type.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())
                    || Modifier.isProtected(method.getModifiers())) {
                actual.add(method.getReturnType().getName()
                        + " " + method.getName()
                        + parameters(method.getParameterTypes())
                        + exceptions(method.getExceptionTypes()));
            }
        }
        assertEquals(type.getName() + " methods", ordered(expected), actual);
    }

    private static String parameters(Class<?>[] parameterTypes) {
        StringBuilder signature = new StringBuilder("(");
        for (int index = 0; index < parameterTypes.length; index++) {
            if (index > 0) {
                signature.append(',');
            }
            signature.append(parameterTypes[index].getName());
        }
        return signature.append(')').toString();
    }

    private static String exceptions(Class<?>[] exceptionTypes) {
        if (exceptionTypes.length == 0) {
            return "";
        }
        StringBuilder signature = new StringBuilder(" throws ");
        for (int index = 0; index < exceptionTypes.length; index++) {
            if (index > 0) {
                signature.append(',');
            }
            signature.append(exceptionTypes[index].getName());
        }
        return signature.toString();
    }

    private static Set<String> ordered(String... values) {
        return new LinkedHashSet<String>(Arrays.asList(values));
    }

    private static Set<String> facadeMappings(JarFile jar)
            throws ClassNotFoundException {
        Set<String> result = new LinkedHashSet<String>();
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (!entry.isDirectory()
                    && entry.getName().startsWith("net/zerocloud/pdf/itext7/")
                    && entry.getName().endsWith(".class")) {
                Class<?> candidate = Class.forName(
                        entry.getName()
                                .substring(0, entry.getName().length() - ".class".length())
                                .replace('/', '.'),
                        false,
                        JarContractIT.class.getClassLoader());
                if (Modifier.isPublic(candidate.getModifiers())) {
                    result.add(entry.getName());
                }
            }
        }
        return result;
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

    private static String read(JarFile jar, JarEntry entry) throws IOException {
        try (InputStream input = jar.getInputStream(entry)) {
            byte[] bytes = new byte[(int) entry.getSize()];
            int offset = 0;
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) {
                    throw new IOException("Unexpected end of " + entry.getName());
                }
                offset += count;
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

}
