package net.zerocloud.pdf.conversion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.time.Duration;
import net.zerocloud.pdf.provider.ProviderLimits;
import net.zerocloud.pdf.provider.ProviderFailure;
import net.zerocloud.pdf.provider.ProviderFailureCode;
import net.zerocloud.pdf.provider.ProviderRequest;
import net.zerocloud.pdf.provider.ShapingRequest;
import net.zerocloud.pdf.provider.ShapingResult;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Real external Provider contract; the official hb-shape oracle predates this adapter. */
public final class HarfBuzzCapabilityProviderTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void nativeArabicLigatureRetainsSharedInputClusterAndIndependentMarkPosition() throws Exception {
        HarfBuzzCapabilityProvider provider = new HarfBuzzCapabilityProvider(
                Paths.get(helper()), temporary.newFolder().toPath(), "10.2.0",
                ProviderLimits.bounded(1 << 20, 1 << 20, Duration.ofSeconds(10)));
        ShapingRequest shaping = ShapingRequest.version1(arabicFont(), "\u0644\u064e\u0627",
                "Arab", "ar", ShapingRequest.Direction.RIGHT_TO_LEFT, 16);
        ShapingResult result = ShapingResult.decode(provider.execute(
                ProviderRequest.builder(ShapingRequest.CAPABILITY_ID, shaping.encode())
                        .timeout(Duration.ofSeconds(10)).build()), shaping);
        assertEquals("10.2.0", result.getEngineVersion());
        assertEquals(1000, result.getUnitsPerEm());
        assertEquals(ShapingRequest.Direction.RIGHT_TO_LEFT, result.getDirection());
        assertEquals(2, result.getGlyphs().size());
        ShapingResult.Glyph mark = result.getGlyphs().get(0);
        ShapingResult.Glyph ligature = result.getGlyphs().get(1);
        assertEquals(292, mark.getGlyphId());
        assertEquals(704, ligature.getGlyphId());
        assertEquals(0, mark.getClusterStart());
        assertEquals(3, mark.getClusterEnd());
        assertEquals(0, ligature.getClusterStart());
        assertEquals(3, ligature.getClusterEnd());
        assertEquals(0, mark.getXAdvance());
        assertEquals(249, mark.getXOffset());
        assertEquals(256, mark.getYOffset());
        assertEquals(582, ligature.getXAdvance());
        assertEquals(0, ligature.getYOffset());
    }

    @Test
    public void unavailableInstallationAndVersionMismatchHaveSafeStableFailures() throws Exception {
        ProviderLimits limits = ProviderLimits.bounded(1 << 20, 1 << 20, Duration.ofSeconds(10));
        byte[] payload = request(16).encode();
        assertFailure(ProviderFailureCode.PROVIDER_UNAVAILABLE,
                new HarfBuzzCapabilityProvider(temporary.getRoot().toPath().resolve("missing-engine"),
                        temporary.newFolder().toPath(), "10.2.0", limits), payload);
        java.nio.file.Path staging = temporary.newFolder().toPath();
        assertFailure(ProviderFailureCode.PROVIDER_UNAVAILABLE,
                new HarfBuzzCapabilityProvider(Paths.get(helper()), staging, "10.1.0", limits), payload);
        assertEquals(0, staging.toFile().list().length);
    }

    @Test
    public void nativeGlyphLimitAndProviderByteLimitsFailAndCleanOwnedStaging() throws Exception {
        java.nio.file.Path staging = temporary.newFolder().toPath();
        HarfBuzzCapabilityProvider provider = new HarfBuzzCapabilityProvider(Paths.get(helper()), staging,
                "10.2.0", ProviderLimits.bounded(1 << 20, 1 << 20, Duration.ofSeconds(10)));
        assertFailure(ProviderFailureCode.OUTPUT_LIMIT_EXCEEDED, provider, request(1).encode());
        assertEquals(0, staging.toFile().list().length);
        assertFailure(ProviderFailureCode.INPUT_LIMIT_EXCEEDED,
                new HarfBuzzCapabilityProvider(Paths.get(helper()), staging, "10.2.0",
                        ProviderLimits.bounded(100, 1 << 20, Duration.ofSeconds(10))), request(16).encode());
        assertFailure(ProviderFailureCode.OUTPUT_LIMIT_EXCEEDED,
                new HarfBuzzCapabilityProvider(Paths.get(helper()), staging, "10.2.0",
                        ProviderLimits.bounded(1 << 20, 32, Duration.ofSeconds(10))), request(16).encode());
        assertEquals(0, staging.toFile().list().length);
    }

    @Test
    public void malformedRequestAndFontFailThroughTheExternalProviderSeam() throws Exception {
        java.nio.file.Path staging = temporary.newFolder().toPath();
        HarfBuzzCapabilityProvider provider = new HarfBuzzCapabilityProvider(Paths.get(helper()), staging,
                "10.2.0", ProviderLimits.bounded(1 << 20, 1 << 20, Duration.ofSeconds(10)));
        byte[] payload = request(16).encode();
        payload[0] = 0;
        assertFailure(ProviderFailureCode.EXECUTION_FAILED, provider, payload);
        ShapingRequest malformedFont = ShapingRequest.version1(new byte[] {1, 2, 3}, "a",
                "Latn", "en", ShapingRequest.Direction.LEFT_TO_RIGHT, 16);
        assertFailure(ProviderFailureCode.EXECUTION_FAILED, provider, malformedFont.encode());
        assertEquals(0, staging.toFile().list().length);
    }

    private void assertFailure(ProviderFailureCode expected, HarfBuzzCapabilityProvider provider,
            byte[] payload) throws Exception {
        try {
            provider.execute(ProviderRequest.builder(ShapingRequest.CAPABILITY_ID, payload)
                    .timeout(Duration.ofSeconds(10)).build());
            fail("Expected a stable Provider failure");
        } catch (ProviderFailure failure) {
            assertEquals(expected, failure.getCode());
            assertEquals(HarfBuzzCapabilityProvider.PROVIDER_ID, failure.getProviderId());
            assertEquals(ShapingRequest.CAPABILITY_ID, failure.getCapabilityId());
            assertNull(failure.getCause());
            assertFalse(failure.getDiagnostic().contains(temporary.getRoot().toString()));
        }
    }

    private ShapingRequest request(int maximumGlyphs) throws Exception {
        return ShapingRequest.version1(arabicFont(), "\u0644\u064e\u0627", "Arab", "ar",
                ShapingRequest.Direction.RIGHT_TO_LEFT, maximumGlyphs);
    }

    private static String helper() {
        String executable = System.getProperty("folio.harfBuzzHelper");
        if (executable == null) {
            throw new AssertionError("Set folio.harfBuzzHelper to the explicitly installed T29 native helper");
        }
        return executable;
    }

    private byte[] arabicFont() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/net/zerocloud/pdf/fixtures/noto/NotoSansArabic-Regular.ttf")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) != -1;) { output.write(buffer, 0, count); }
            return output.toByteArray();
        }
    }
}
