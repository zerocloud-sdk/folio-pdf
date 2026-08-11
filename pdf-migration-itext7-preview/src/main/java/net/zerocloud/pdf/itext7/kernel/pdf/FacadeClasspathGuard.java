package net.zerocloud.pdf.itext7.kernel.pdf;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

final class FacadeClasspathGuard {

    private static final String EDITION_MARKER =
            "META-INF/open-pdf/migration-itext7.edition";
    private static final String CONFLICT_MESSAGE =
            "pdf-migration-itext7 and pdf-migration-itext7-preview "
                    + "cannot coexist on one classpath.";

    private FacadeClasspathGuard() {
    }

    static void requirePreviewOnly() {
        int markers = 0;
        boolean preview = false;
        boolean stable = false;
        try {
            Enumeration<URL> resources = resources();
            while (resources.hasMoreElements()) {
                markers++;
                String edition = readEdition(resources.nextElement());
                preview |= "preview".equals(edition);
                stable |= "stable".equals(edition);
            }
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "The Migration Facade edition could not be verified.",
                    failure);
        }
        if (markers != 1 || !preview || stable) {
            throw new IllegalStateException(CONFLICT_MESSAGE);
        }
    }

    private static Enumeration<URL> resources() throws IOException {
        ClassLoader loader = FacadeClasspathGuard.class.getClassLoader();
        if (loader == null) {
            return ClassLoader.getSystemResources(EDITION_MARKER);
        }
        return loader.getResources(EDITION_MARKER);
    }

    private static String readEdition(URL resource) throws IOException {
        try (InputStream input = resource.openStream();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String edition = reader.readLine();
            return edition == null ? "" : edition.trim();
        }
    }
}
