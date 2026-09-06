package net.zerocloud.pdf.acceptance;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Properties;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowEnvironment;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
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
import net.zerocloud.pdf.conversion.HarfBuzzCapabilityProvider;
import net.zerocloud.pdf.provider.ProviderLimits;
import net.zerocloud.pdf.provider.ProviderPreference;
import net.zerocloud.pdf.provider.ShapingRequest;

/** Product-only Workflow producer. It never supplies expected shaping results. */
final class T29ShapingProducts {
    private static final String ROOT = "/net/zerocloud/pdf/acceptance/";

    static WorkflowOutcome<Void> create(Path target, WorkflowExecutionProfile mode,
            Path helper, Path staging) throws Exception {
        Properties corpus = properties("shaping/T29-corpus.properties");
        Properties pins = properties("fonts/noto/fonts.properties");
        HarfBuzzCapabilityProvider provider = new HarfBuzzCapabilityProvider(helper, staging, "10.2.0",
                ProviderLimits.bounded(2 << 20, 32 + 24 * 4096, Duration.ofSeconds(10)));
        return new DocumentWorkflow(WorkflowEnvironment.builder().provider(provider).build())
                .execute(WorkflowRequest.builder().target("result", PublicationTarget.path(target))
                        .providerPreference(ProviderPreference.prefer(ShapingRequest.CAPABILITY_ID,
                                HarfBuzzCapabilityProvider.PROVIDER_ID))
                        .executionProfile(mode).saveMode(SaveMode.REWRITE).build(), session -> {
                    for (String profile : corpus.getProperty("profiles").split(",")) {
                        String[] names = corpus.getProperty(profile + ".fonts").split(",");
                        FontSource[] fonts = new FontSource[names.length];
                        for (int index = 0; index < names.length; index++) {
                            try {
                                Path path = Paths.get(T29ShapingProducts.class.getResource(
                                        ROOT + "fonts/noto/" + names[index]).toURI());
                                if (!pins.getProperty(names[index] + ".sha256").equals(EvidenceFiles.sha256(path))) {
                                    throw new IllegalStateException("The explicit T29 font hash changed");
                                }
                                fonts[index] = FontSource.path(path);
                            } catch (java.io.IOException | java.net.URISyntaxException failure) {
                                throw new IllegalStateException("The offline T29 font could not be loaded", failure);
                            }
                        }
                        LayoutPage page = LayoutPage.version1(240, 192, PageMargins.of(24, 24, 24, 24));
                        ParagraphFlow.Builder flow = ParagraphFlow.version1(FontSelection.explicit(fonts))
                                .page(page).page(page);
                        int paragraphs = Integer.parseInt(corpus.getProperty(profile + ".paragraphs"));
                        for (int index = 1; index <= paragraphs; index++) {
                            String key = profile + "." + index;
                            flow.paragraph(Paragraph.version1(48)
                                    .maximumWidth(Double.parseDouble(corpus.getProperty(key + ".width")))
                                    .text(corpus.getProperty(key + ".text"), 12).build());
                        }
                        session.execute(ComposeParagraphs.version1(flow.build(), limits()));
                    }
                    return null;
                });
    }

    private static Properties properties(String resource) throws java.io.IOException {
        Properties result = new Properties();
        try (InputStream input = T29ShapingProducts.class.getResourceAsStream(ROOT + resource)) {
            if (input == null) { throw new IllegalStateException("The offline T29 resource is missing"); }
            try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) { result.load(reader); }
        }
        return result;
    }

    private static CompositionLimits limits() {
        return CompositionLimits.builder().maximumPages(2).maximumAreas(2).maximumFlowItems(8).maximumInlines(16)
                .maximumLines(32).maximumGeneratedContentBytes(1 << 20)
                .fontLimits(FontLimits.builder().maximumFontSources(2).maximumSourceBytes(2 << 20)
                        .maximumCodePoints(1024).maximumFallbackChecks(4096).maximumGeneratedContentBytes(1 << 20).build())
                .graphicLimits(CanvasResourceLimits.builder().maximumEncodedImageBytes(0).maximumDecodedImagePixels(0)
                        .maximumDecodedImageBytes(0).maximumIccProfileBytes(0).maximumMaskBytes(0)
                        .maximumGeneratedContentBytes(0).maximumResourceDeclarations(0).maximumTransparencyGroupDepth(0).build()).build();
    }

    private T29ShapingProducts() { }
}
