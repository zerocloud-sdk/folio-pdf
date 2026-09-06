package net.zerocloud.pdf.acceptance;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.HardenedWorkerSettings;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowEnvironment;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.WorkflowResourcePolicy;
import net.zerocloud.pdf.composition.CanvasResourceLimits;
import net.zerocloud.pdf.composition.CompositionLimits;
import net.zerocloud.pdf.composition.FontLimits;
import net.zerocloud.pdf.composition.FontSelection;
import net.zerocloud.pdf.composition.FontSource;
import net.zerocloud.pdf.composition.LayoutPage;
import net.zerocloud.pdf.composition.PageMargins;
import net.zerocloud.pdf.composition.Paragraph;
import net.zerocloud.pdf.composition.ParagraphFlow;
import net.zerocloud.pdf.composition.command.ComposeParagraphs;

/** Only the product producer invokes paragraph layout; reference files are offline fontTools artifacts. */
final class T28UnicodeProducts {
    static final String CAPABILITY = "composition.layout.paragraph-areas";
    static final String RESOURCE = "/net/zerocloud/pdf/acceptance/";
    static final long OWNED_MEMORY_BYTES = 2L << 30;

    static WorkflowOutcome<Void> create(Path target, WorkflowExecutionProfile mode) throws Exception {
        Properties corpus = properties("unicode/T28-corpus.properties");
        Properties pins = properties("fonts/noto/fonts.properties");
        DocumentWorkflow workflow = new DocumentWorkflow(WorkflowEnvironment.builder().hardenedWorkerSettings(
                HardenedWorkerSettings.builder().maximumMessageBytes(64 << 20).maximumHeapBytes(1L << 30).build()).build());
        return workflow.execute(WorkflowRequest.builder().target("result", PublicationTarget.path(target))
                .executionProfile(mode).resourcePolicy(policy()).saveMode(SaveMode.REWRITE).build(), session -> {
                    for (String profile : corpus.getProperty("profiles").split(",")) {
                        String[] names = corpus.getProperty(profile + ".fonts").split(",");
                        FontSource[] fonts = new FontSource[names.length];
                        for (int index = 0; index < fonts.length; index++) {
                            Path path;
                            try {
                                path = Paths.get(T28UnicodeProducts.class.getResource(RESOURCE + "fonts/noto/" + names[index]).toURI());
                                if (!pins.getProperty(names[index] + ".sha256").equals(EvidenceFiles.sha256(path))) {
                                    throw new IllegalStateException("The explicit T28 font hash changed");
                                }
                            } catch (java.io.IOException | java.net.URISyntaxException failure) {
                                throw new IllegalStateException("The offline T28 font could not be loaded", failure);
                            }
                            fonts[index] = FontSource.path(path);
                        }
                        ParagraphFlow.Builder flow = ParagraphFlow.version1(FontSelection.explicit(fonts))
                                .page(LayoutPage.version1(612, 792, PageMargins.of(72, 72, 72, 72)));
                        for (int index = 1; index <= Integer.parseInt(corpus.getProperty(profile + ".paragraphs")); index++) {
                            String key = profile + "." + index;
                            String text = corpus.getProperty(key + ".text");
                            Paragraph.Builder paragraph = Paragraph.version1(48)
                                    .maximumWidth(Double.parseDouble(corpus.getProperty(key + ".width", "0")));
                            int split = Integer.parseInt(corpus.getProperty(key + ".split", "0"));
                            if (split > 0) { paragraph.text(text.substring(0, split), 12).text(text.substring(split), 12); }
                            else { paragraph.text(text, 12); }
                            flow.paragraph(paragraph.build());
                        }
                        session.execute(ComposeParagraphs.version1(flow.build(), limits()));
                    }
                    return null;
                });
    }

    static Properties properties(String name) throws java.io.IOException {
        Properties result = new Properties();
        try (InputStream input = resource(name); InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            result.load(reader);
        }
        return result;
    }

    static InputStream resource(String name) {
        InputStream input = T28UnicodeProducts.class.getResourceAsStream(RESOURCE + name);
        if (input == null) { throw new IllegalStateException("Missing offline T28 reference " + name); }
        return input;
    }

    static void copyReference(Path target) throws java.io.IOException {
        try (InputStream input = resource("unicode/T28-unicode-reference.pdf")) { Files.copy(input, target); }
    }

    private static CompositionLimits limits() {
        return CompositionLimits.builder().maximumPages(1).maximumAreas(1).maximumFlowItems(8).maximumInlines(16)
                .maximumLines(32).maximumGeneratedContentBytes(1 << 20)
                .fontLimits(FontLimits.builder().maximumFontSources(3).maximumSourceBytes(128L << 20)
                        .maximumCodePoints(10000).maximumFallbackChecks(30000).maximumGeneratedContentBytes(1 << 20).build())
                .graphicLimits(CanvasResourceLimits.builder().maximumEncodedImageBytes(0).maximumDecodedImagePixels(0)
                        .maximumDecodedImageBytes(0).maximumIccProfileBytes(0).maximumMaskBytes(0)
                        .maximumGeneratedContentBytes(0).maximumResourceDeclarations(0).maximumTransparencyGroupDepth(0).build()).build();
    }

    // This single transaction retains six complete reference programs until publication.
    private static WorkflowResourcePolicy policy() {
        WorkflowResourcePolicy defaults = WorkflowResourcePolicy.safeDefaults();
        return WorkflowResourcePolicy.builder().maximumOwnedMemoryBytes(OWNED_MEMORY_BYTES)
                .maximumPages(8).maximumInputBytes(defaults.getMaximumInputBytes())
                .maximumObjects(defaults.getMaximumObjects()).maximumNestingDepth(defaults.getMaximumNestingDepth())
                .maximumDecompressedBytes(defaults.getMaximumDecompressedBytes()).maximumDecodedPixels(0)
                .maximumTemporaryStorageBytes(defaults.getMaximumTemporaryStorageBytes())
                .maximumElapsedTime(defaults.getMaximumElapsedTime()).maximumConcurrentWorkflows(1).build();
    }

    private T28UnicodeProducts() { }
}
