package net.zerocloud.pdf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public final class PdfBoxCMapPreflightTest {

    @Test
    public void countsCharactersAndExpandedRanges() throws Exception {
        String cmap = "2 beginbfchar <41> <0041> <42> <0042> endbfchar\n"
                + "1 beginbfrange <43> <45> <0043> endbfrange\n";

        assertEquals(5, PdfBoxCMapPreflight.countMappings(bytes(cmap), 5));
    }

    @Test
    public void decimalCountsUseStrictPdfNumbersAndBackendIntegerConversion()
            throws Exception {
        String cmap = "1.0 beginbfrange <41> <43> <0041> endbfrange\n";
        String fractional = ".9 beginbfchar endbfchar\n";
        String signed = "+1.0 beginbfchar <41> <0041> endbfchar\n";

        assertEquals(3, PdfBoxCMapPreflight.countMappings(bytes(cmap), 3));
        assertEquals(0,
                PdfBoxCMapPreflight.countMappings(bytes(fractional), 0));
        assertEquals(1,
                PdfBoxCMapPreflight.countMappings(bytes(signed), 1));
        assertExhausted(cmap, 2);
        assertMalformed("-1 beginbfchar endbfchar\n");
    }

    @Test
    public void rejectsHugeRangesBeforeBackendExpansion() throws Exception {
        assertExhausted(
                "1 beginbfrange <00000000> <7FFFFFFF> <0041> "
                        + "endbfrange\n",
                2);
        assertExhausted(
                "1 beginbfrange <FFFFFFFF> <7FFFFFFD> <0041> "
                        + "endbfrange\n",
                2);
    }

    @Test
    public void rejectsUnsupportedInheritedCMaps() throws Exception {
        try {
            PdfBoxCMapPreflight.countMappings(
                    bytes("/Adobe-Identity-UCS usecmap\n"), 0);
            fail("Expected inherited CMap rejection");
        } catch (IOException expected) {
            // Expected safe rejection before FontBox parsing.
        }
    }

    @Test
    public void rejectsNonUnicodeDestinations() throws Exception {
        assertMalformed("1 beginbfchar <41> /NotUnicode endbfchar\n");
        assertMalformed("1 beginbfchar <41> <> endbfchar\n");
        assertMalformed("1 beginbfchar <41> <41> endbfchar\n");
        assertMalformed("1 beginbfchar <41> <D800> endbfchar\n");
        assertMalformed("1 beginbfchar <41> <DC00> endbfchar\n");
        assertMalformed(
                "1 beginbfrange <41> <42> <D800> endbfrange\n");
        assertMalformed(
                "1 beginbfrange <41> <42> [<0041> <D800>] "
                        + "endbfrange\n");
    }

    @Test
    public void acceptsPairedSurrogateUnicodeDestinations() throws Exception {
        assertEquals(1, PdfBoxCMapPreflight.countMappings(
                bytes("1 beginbfchar <41> <D83DDE00> endbfchar\n"), 1));
    }

    @Test
    public void rejectsPrematureTerminatorsAndReversedRanges()
            throws Exception {
        assertMalformed(
                "2 beginbfchar <41> <0041> endbfchar\n");
        assertMalformed(
                "2 beginbfrange <41> <41> <0041> endbfrange\n");
        assertMalformed(
                "1 beginbfrange <42> <41> <0041> endbfrange\n");
        assertMalformed(
                "1 beginbfchar <41> <0041> endcmap\n");
    }

    @Test
    public void scalarRangesCountTheEmbeddedBackendCarry()
            throws Exception {
        String cmap =
                "1 beginbfrange <0000> <00FF> <00FF> endbfrange\n";

        assertEquals(256, PdfBoxCMapPreflight.countMappings(bytes(cmap), 256));
        assertExhausted(cmap, 1);
    }

    @Test
    public void stopsAtEndCMapLikeFontBox() throws Exception {
        String cmap = "1 beginbfchar <41> <0041> endbfchar\n"
                + "endcmap\n"
                + "1 beginbfrange <00000000> <7FFFFFFF> <0041> "
                + "endbfrange\n";

        assertEquals(1, PdfBoxCMapPreflight.countMappings(bytes(cmap), 1));
    }

    private static void assertExhausted(String cmap, int maximum)
            throws Exception {
        try {
            PdfBoxCMapPreflight.countMappings(bytes(cmap), maximum);
            fail("Expected mapping limit exhaustion");
        } catch (PdfBoxCMapPreflight.LimitExceededException expected) {
            // Expected caller-bound rejection.
        }
    }

    private static void assertMalformed(String cmap) throws Exception {
        try {
            PdfBoxCMapPreflight.countMappings(bytes(cmap), 16);
            fail("Expected malformed CMap rejection");
        } catch (PdfBoxCMapPreflight.LimitExceededException unexpected) {
            throw unexpected;
        } catch (IOException expected) {
            // Expected safe rejection before FontBox parsing.
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
