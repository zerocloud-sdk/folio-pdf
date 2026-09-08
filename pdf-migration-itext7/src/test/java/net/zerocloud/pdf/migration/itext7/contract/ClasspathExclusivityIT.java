package net.zerocloud.pdf.migration.itext7.contract;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class ClasspathExclusivityIT {

    private static final String CONFLICT_MESSAGE =
            "pdf-migration-itext7 and pdf-migration-itext7-preview "
                    + "cannot coexist on one classpath.";
    private static final List<String> MAPPED_PUBLIC_CLASSES = Arrays.asList(
            "net.zerocloud.pdf.itext7.kernel.exceptions.PdfException",
            "net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument",
            "net.zerocloud.pdf.itext7.kernel.pdf.PdfPage",
            "net.zerocloud.pdf.itext7.kernel.pdf.PdfReader",
            "net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter",
            "net.zerocloud.pdf.itext7.layout.Document");

    @Test
    public void stableAndPreviewFailForBothClasspathOrders() throws Exception {
        Path stable = Paths.get(ContractTestProperties.required("stableArtifactPath"));
        Path preview = Paths.get(ContractTestProperties.required("artifactPath"));

        for (String mappedPublicClass : MAPPED_PUBLIC_CLASSES) {
            assertClasspathConflict(stable, preview, mappedPublicClass);
            assertClasspathConflict(preview, stable, mappedPublicClass);
        }
    }

    private static void assertClasspathConflict(
            Path first,
            Path second,
            String mappedPublicClass)
            throws IOException, InterruptedException {
        Path java = Paths.get(
                System.getProperty("java.home"),
                "bin",
                isWindows() ? "java.exe" : "java");
        String classpath = ContractTestProperties.required("testClassesPath")
                + File.pathSeparator + first
                + File.pathSeparator + second
                + File.pathSeparator
                + ContractTestProperties.required("documentArtifactPath");
        Process process = new ProcessBuilder(
                java.toString(),
                "-cp",
                classpath,
                ClasspathProbe.class.getName(),
                mappedPublicClass)
                .redirectErrorStream(true)
                .start();
        String output;
        try (InputStream input = process.getInputStream()) {
            output = read(input);
        }
        int exitCode = process.waitFor();

        assertTrue(mappedPublicClass + " unexpectedly initialized with both artifacts:\n"
                        + output,
                exitCode != 0);
        assertTrue(mappedPublicClass + " did not expose the conflict message:\n" + output,
                output.contains(CONFLICT_MESSAGE));
    }

    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("win");
    }

}
