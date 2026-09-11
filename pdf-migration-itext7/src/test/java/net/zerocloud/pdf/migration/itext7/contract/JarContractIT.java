package net.zerocloud.pdf.migration.itext7.contract;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeSet;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfPage;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;
import net.zerocloud.pdf.itext7.kernel.utils.PdfMerger;
import net.zerocloud.pdf.itext7.kernel.utils.PdfSplitter;
import org.junit.Test;

public final class JarContractIT {

    private static final int JAVA_8_CLASS_MAJOR_VERSION = 52;
    private static final String EDITION_MARKER =
            "META-INF/folio-pdf/migration-itext7.edition";

    @Test
    public void bothJava8ArtifactsContainTheExactDeclaredSurface() throws Exception {
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
                "net/zerocloud/pdf/itext7/kernel/pdf/PdfObject.class",
                "net/zerocloud/pdf/itext7/kernel/pdf/PdfNull.class",
                "net/zerocloud/pdf/itext7/kernel/pdf/PdfBoolean.class",
                "net/zerocloud/pdf/itext7/kernel/pdf/PdfNumber.class",
                "net/zerocloud/pdf/itext7/kernel/pdf/PdfString.class",
                "net/zerocloud/pdf/itext7/kernel/pdf/PdfName.class",
                "net/zerocloud/pdf/itext7/kernel/pdf/PdfArray.class",
                "net/zerocloud/pdf/itext7/kernel/pdf/PdfDictionary.class",
                "net/zerocloud/pdf/itext7/kernel/pdf/PdfStream.class",
                "net/zerocloud/pdf/itext7/kernel/pdf/PdfIndirectReference.class",
                "net/zerocloud/pdf/itext7/kernel/pdf/PdfCatalog.class",
                "net/zerocloud/pdf/itext7/kernel/pdf/PdfDocumentInfo.class",
                "net/zerocloud/pdf/itext7/kernel/utils/PdfMerger.class",
                "net/zerocloud/pdf/itext7/kernel/utils/PdfSplitter.class",
                "net/zerocloud/pdf/itext7/layout/Document.class"));

        try (JarFile preview = new JarFile(previewArtifact.toFile());
                JarFile stable = new JarFile(stableArtifact.toFile());
                ArtifactLoader previewLoader = new ArtifactLoader(previewArtifact);
                ArtifactLoader stableLoader = new ArtifactLoader(stableArtifact)) {
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

            Set<String> stableMappings = facadeMappings(stable, stableLoader);
            Set<String> previewMappings = facadeMappings(preview, previewLoader);
            assertEquals(expectedPreviewMappings, stableMappings);
            assertEquals(expectedPreviewMappings, previewMappings);
            assertTrue(previewMappings.containsAll(stableMappings));
            assertDeclaredSurface(repositoryRoot, "stable", stableMappings, stableLoader);
            assertDeclaredSurface(repositoryRoot, "preview", previewMappings, previewLoader);

            Enumeration<JarEntry> stableEntries = stable.entries();
            while (stableEntries.hasMoreElements()) {
                JarEntry entry = stableEntries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    assertJava8Class(stable, entry);
                }
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
            assertExactPublicSurface(stableLoader);
            assertExactPublicSurface(previewLoader);
        }

        String parentPom = read(repositoryRoot.resolve("pom.xml"));
        String stablePom = read(repositoryRoot.resolve("pdf-migration-itext7/pom.xml"));
        String bomPom = read(repositoryRoot.resolve("pdf-bom/pom.xml"));
        assertTrue(parentPom.contains("<module>pdf-migration-itext7</module>"));
        assertTrue(stablePom.contains("<artifactId>pdf-document</artifactId>"));
        assertTrue(bomPom.contains("<artifactId>pdf-migration-itext7</artifactId>"));
        assertTrue(bomPom.contains(
                "<artifactId>pdf-migration-itext7-preview</artifactId>"));
    }

    private static void assertDeclaredSurface(Path root, String edition, Set<String> types,
            ClassLoader loader) throws Exception {
        Map<?, ?> inventory;
        try (InputStream input = Files.newInputStream(root.resolve("capabilities/facade-surface.yaml"))) {
            inventory = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
        }
        Set<String> declared = new TreeSet<String>();
        Map<?, ?> surfaces = (Map<?, ?>) inventory.get("surfaces");
        List<Object> entries = new ArrayList<Object>((List<?>) surfaces.get("stable"));
        if ("preview".equals(edition)) {
            entries.addAll((List<?>) surfaces.get("preview"));
        }
        for (Object value : entries) {
            Map<?, ?> entry = (Map<?, ?>) ((Map<?, ?>) value).get("folio-pdf");
            assertTrue("Duplicate declared mapping", declared.add(entry.get("type") + "#" + entry.get("member")));
        }
        Set<String> observed = new TreeSet<String>();
        for (String entry : types) {
            Class<?> type = Class.forName(entry.substring(0, entry.length() - 6).replace('/', '.'), true, loader);
            int before = observed.size();
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                if (Modifier.isPublic(constructor.getModifiers()) || Modifier.isProtected(constructor.getModifiers())) {
                    observed.add(type.getName() + "#<init>" + parameters(constructor.getParameterTypes()));
                }
            }
            for (Method method : type.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers()) || Modifier.isProtected(method.getModifiers())) {
                    observed.add(type.getName() + "#" + method.getName() + parameters(method.getParameterTypes()));
                }
            }
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isPublic(field.getModifiers()) || Modifier.isProtected(field.getModifiers())) {
                    observed.add(type.getName() + "#" + field.getName());
                }
            }
            if (before == observed.size()) {
                observed.add(type.getName() + "#<type>");
            }
        }
        assertEquals("Built " + edition + " jar must exactly match its declaration", declared, observed);
    }

    private static void assertExactPublicSurface(ClassLoader loader) throws Exception {
        Class<?> writer = loader.loadClass(PdfWriter.class.getName());
        Class<?> reader = loader.loadClass(PdfReader.class.getName());
        Class<?> document = loader.loadClass(PdfDocument.class.getName());
        Class<?> page = loader.loadClass(PdfPage.class.getName());
        Class<?> merger = loader.loadClass(PdfMerger.class.getName());
        Class<?> splitter = loader.loadClass(PdfSplitter.class.getName());
        Class<?> layout = loader.loadClass("net.zerocloud.pdf.itext7.layout.Document");
        Class<?> exception = loader.loadClass(PdfException.class.getName());
        assertConstructors(writer,
                "PdfWriter(java.lang.String) throws java.io.FileNotFoundException",
                "PdfWriter(java.io.OutputStream)");
        assertMethods(writer);

        assertConstructors(reader,
                "PdfReader(java.lang.String) throws java.io.IOException",
                "PdfReader(java.io.InputStream) throws java.io.IOException");
        assertMethods(reader, "void close() throws java.io.IOException");

        assertConstructors(document,
                "PdfDocument(net.zerocloud.pdf.itext7.kernel.pdf.PdfReader)",
                "PdfDocument(net.zerocloud.pdf.itext7.kernel.pdf.PdfReader,net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter)",
                "PdfDocument(net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter)",
                "PdfDocument(java.util.Map,java.lang.String,java.util.Map)",
                "PdfDocument(java.util.Map,java.lang.String,java.util.Map,net.zerocloud.pdf.PdfVersion)");
        assertMethods(document,
                "net.zerocloud.pdf.itext7.kernel.pdf.PdfPage addNewPage()",
                "net.zerocloud.pdf.itext7.kernel.pdf.PdfPage addNewPage(int)",
                "java.util.List copyPages(int,int,int)",
                "net.zerocloud.pdf.itext7.kernel.pdf.PdfCatalog getCatalog()",
                "net.zerocloud.pdf.itext7.kernel.pdf.PdfDocumentInfo getDocumentInfo()",
                "byte[] getXmpMetadata()",
                "byte[] getXmpMetadata(long)",
                "void setXmpMetadata(byte[])",
                "java.util.Map getNamedDestinations(int)",
                "void addNamedDestination(java.lang.String,net.zerocloud.pdf.PageDestination)",
                "void setNamedDestinations(java.util.Map,java.util.List)",
                "java.util.List getOutlines(int)",
                "void setOutlines(java.util.List)",
                "void addFileAttachment(net.zerocloud.pdf.EmbeddedFile)",
                "java.util.List getFileAttachments(int)",
                "java.util.Optional getFileAttachment(java.lang.String,long)",
                "java.util.List getAnnotations(int,long,long)",
                "void updateAnnotations(java.util.List,java.util.List)",
                "net.zerocloud.pdf.DocumentActions getActions(int)",
                "void flattenAnnotations(java.lang.String[])",
                "net.zerocloud.pdf.itext7.kernel.utils.PdfMerger getMerger()",
                "int getNumberOfPages()",
                "net.zerocloud.pdf.itext7.kernel.pdf.PdfPage getPage(int)",
                "java.util.List getPublicationReceipts()",
                "net.zerocloud.pdf.itext7.kernel.utils.PdfSplitter getSplitter()",
                "void movePage(int,int)",
                "void movePages(int,int,int)",
                "void removePage(int)",
                "void removePages(int,int)",
                "void close()");

        assertConstructors(page);
        assertMethods(page,
                "net.zerocloud.pdf.itext7.kernel.pdf.PdfDictionary getPdfObject()",
                "java.util.List getAnnotations(int,long,long)",
                "net.zerocloud.pdf.itext7.kernel.pdf.PdfPage addAnnotation(net.zerocloud.pdf.Annotation)",
                "net.zerocloud.pdf.itext7.kernel.pdf.PdfPage removeAnnotation(java.lang.String)",
                "net.zerocloud.pdf.itext7.kernel.pdf.PdfPage setNormalAppearance(java.lang.String,net.zerocloud.pdf.AnnotationAppearance)",
                "net.zerocloud.pdf.itext7.kernel.pdf.PdfPage setAdditionalAction(net.zerocloud.pdf.itext7.kernel.pdf.PdfName,net.zerocloud.pdf.GoToAction)",
                "java.util.Optional getAdditionalActions(int)");

        assertConstructors(merger,
                "PdfMerger(net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument)");
        assertMethods(merger,
                "void close()",
                "net.zerocloud.pdf.itext7.kernel.utils.PdfMerger merge(java.lang.String[])");

        assertConstructors(splitter,
                "PdfSplitter(net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument)");
        assertMethods(splitter,
                "void extractPageRanges(java.lang.String[],net.zerocloud.pdf.PageRange[])");

        assertConstructors(layout,
                "Document(net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument)");
        assertMethods(layout, "void close()");

        assertConstructors(exception,
                "PdfException(java.lang.String,java.lang.Throwable)");
        assertMethods(exception);
        assertValueSurface(loader);
        assertMetadataSurface(loader);
    }

    private static void assertMetadataSurface(ClassLoader loader) throws Exception {
        String prefix = "net.zerocloud.pdf.itext7.kernel.pdf.";
        Class<?> info = loader.loadClass(prefix + "PdfDocumentInfo");
        assertConstructors(info);
        assertMethods(info,
                prefix + "PdfDocumentInfo addCreationDate()",
                prefix + "PdfDocumentInfo addModDate()",
                "java.lang.String getTitle()",
                prefix + "PdfDocumentInfo setTitle(java.lang.String)",
                "java.lang.String getAuthor()",
                prefix + "PdfDocumentInfo setAuthor(java.lang.String)",
                "java.lang.String getSubject()",
                prefix + "PdfDocumentInfo setSubject(java.lang.String)",
                "java.lang.String getKeywords()",
                prefix + "PdfDocumentInfo setKeywords(java.lang.String)",
                "java.lang.String getCreator()",
                prefix + "PdfDocumentInfo setCreator(java.lang.String)",
                "java.lang.String getProducer()",
                prefix + "PdfDocumentInfo setProducer(java.lang.String)",
                prefix + "PdfName getTrapped()",
                prefix + "PdfDocumentInfo setTrapped(" + prefix + "PdfName)",
                "java.lang.String getMoreInfo(java.lang.String)",
                "void setMoreInfo(java.lang.String,java.lang.String)",
                "void setMoreInfo(java.util.Map)",
                "net.zerocloud.pdf.PdfDictionary getEntries()",
                "void updateEntries(java.util.Map,java.util.List)");
    }

    private static void assertValueSurface(ClassLoader loader) throws Exception {
        String prefix = "net.zerocloud.pdf.itext7.kernel.pdf.";
        Class<?> object = loader.loadClass(prefix + "PdfObject");
        assertConstructors(object);
        assertMethods(object, "byte getType()", prefix + "PdfIndirectReference getIndirectReference()",
                "boolean isArray()", "boolean isBoolean()", "boolean isDictionary()",
                "boolean isIndirectReference()", "boolean isName()", "boolean isNull()",
                "boolean isNumber()", "boolean isStream()", "boolean isString()");
        String[] kinds = {"ARRAY", "BOOLEAN", "DICTIONARY", "INDIRECT_REFERENCE", "NAME", "NULL", "NUMBER", "STREAM", "STRING"};
        byte[] codes = {1, 2, 3, 5, 6, 7, 8, 9, 10};
        for (int index = 0; index < kinds.length; index++) {
            Field constant = object.getField(kinds[index]);
            assertEquals(byte.class, constant.getType());
            assertEquals(codes[index], constant.getByte(null));
        }
        Class<?> nil = loader.loadClass(prefix + "PdfNull");
        assertConstructors(nil, "PdfNull()");
        assertMethods(nil, "byte getType()");
        assertEquals(nil, nil.getField("PDF_NULL").getType());
        assertNotNull(nil.getField("PDF_NULL").get(null));
        Class<?> bool = loader.loadClass(prefix + "PdfBoolean");
        assertConstructors(bool, "PdfBoolean(boolean)");
        assertMethods(bool, "byte getType()", "boolean getValue()");
        assertEquals(Boolean.TRUE, bool.getMethod("getValue").invoke(bool.getField("TRUE").get(null)));
        assertEquals(Boolean.FALSE, bool.getMethod("getValue").invoke(bool.getField("FALSE").get(null)));
        Class<?> number = loader.loadClass(prefix + "PdfNumber");
        assertConstructors(number, "PdfNumber(int)", "PdfNumber(double)");
        assertMethods(number, "byte getType()", "int intValue()", "double doubleValue()",
                "void setValue(int)", "void setValue(double)");
        Class<?> string = loader.loadClass(prefix + "PdfString");
        assertConstructors(string, "PdfString(java.lang.String)", "PdfString(byte[])");
        assertMethods(string, "byte getType()", "java.lang.String getValue()", "byte[] getValueBytes()");
        Class<?> name = loader.loadClass(prefix + "PdfName");
        assertConstructors(name, "PdfName(java.lang.String)");
        assertMethods(name, "byte getType()", "java.lang.String getValue()", "boolean equals(java.lang.Object)", "int hashCode()");
        Class<?> array = loader.loadClass(prefix + "PdfArray");
        assertConstructors(array, "PdfArray()", "PdfArray(java.util.List)");
        assertEquals("java.util.List<? extends " + prefix + "PdfObject>",
                array.getConstructor(List.class).getGenericParameterTypes()[0].getTypeName());
        assertMethods(array, "byte getType()", "int size()", prefix + "PdfObject get(int)",
                prefix + "PdfObject get(int,boolean)", prefix + "PdfObject set(int," + prefix + "PdfObject)",
                "void add(" + prefix + "PdfObject)", "void add(int," + prefix + "PdfObject)", "void remove(int)");
        Class<?> dictionary = loader.loadClass(prefix + "PdfDictionary");
        assertConstructors(dictionary, "PdfDictionary()");
        assertMethods(dictionary, "byte getType()", "int size()", prefix + "PdfObject get(" + prefix + "PdfName)",
                prefix + "PdfObject get(" + prefix + "PdfName,boolean)", "java.util.Set keySet()",
                "boolean containsKey(" + prefix + "PdfName)",
                prefix + "PdfObject put(" + prefix + "PdfName," + prefix + "PdfObject)",
                prefix + "PdfObject remove(" + prefix + "PdfName)");
        assertEquals("java.util.Set<" + prefix + "PdfName>", dictionary.getMethod("keySet").getGenericReturnType().getTypeName());
        Class<?> stream = loader.loadClass(prefix + "PdfStream");
        assertEquals(dictionary, stream.getSuperclass());
        assertConstructors(stream, "PdfStream(byte[])");
        assertMethods(stream, "byte getType()", "byte[] getBytes()", "void setData(byte[])");
        Class<?> reference = loader.loadClass(prefix + "PdfIndirectReference");
        assertConstructors(reference);
        assertMethods(reference, "byte getType()", prefix + "PdfObject getRefersTo()",
                "boolean equals(java.lang.Object)", "int hashCode()");
        Class<?> catalog = loader.loadClass(prefix + "PdfCatalog");
        assertConstructors(catalog);
        assertMethods(catalog, prefix + "PdfDictionary getPdfObject()",
                prefix + "PdfCatalog setOpenAction(net.zerocloud.pdf.GoToAction)",
                "java.util.Optional getOpenAction(int)");
    }

    private static void assertConstructors(Class<?> type, String... expected) {
        Set<String> actual = new LinkedHashSet<String>();
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (Modifier.isPublic(constructor.getModifiers())
                    || Modifier.isProtected(constructor.getModifiers())) {
                assertEquals("No generic constructor is declared", 0,
                        constructor.getTypeParameters().length);
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
                assertEquals("No generic method is declared", 0, method.getTypeParameters().length);
                actual.add(method.getReturnType().getTypeName()
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
            signature.append(parameterTypes[index].getTypeName());
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

    private static Set<String> facadeMappings(JarFile jar, ClassLoader loader)
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
                        true,
                        loader);
                if (Modifier.isPublic(candidate.getModifiers())) {
                    assertEquals("No generic type is declared by this surface", 0,
                            candidate.getTypeParameters().length);
                    for (Field field : candidate.getDeclaredFields()) {
                        if (Modifier.isPublic(field.getModifiers()) || Modifier.isProtected(field.getModifiers())) {
                            assertEquals("Mapped fields must be immutable public constants", Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL,
                                    field.getModifiers());
                        }
                    }
                    result.add(entry.getName());
                }
            }
        }
        return result;
    }

    /** Isolates each real jar's mapped types and marker from the test classpath. */
    private static final class ArtifactLoader extends URLClassLoader {
        ArtifactLoader(Path artifact) throws IOException {
            super(new URL[] {artifact.toUri().toURL()}, JarContractIT.class.getClassLoader());
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!name.startsWith("net.zerocloud.pdf.itext7.")) {
                return super.loadClass(name, resolve);
            }
            Class<?> found = findLoadedClass(name);
            if (found == null) {
                found = findClass(name);
            }
            if (resolve) {
                resolveClass(found);
            }
            return found;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return EDITION_MARKER.equals(name) ? findResources(name) : super.getResources(name);
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
