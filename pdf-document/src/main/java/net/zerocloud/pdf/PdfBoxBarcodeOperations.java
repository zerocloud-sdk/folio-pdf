package net.zerocloud.pdf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import net.zerocloud.pdf.composition.Barcode1D;
import net.zerocloud.pdf.composition.BarcodeText;
import net.zerocloud.pdf.composition.CanvasColor;
import net.zerocloud.pdf.composition.CanvasResourceLimits;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.composition.CanvasProgram;
import net.zerocloud.pdf.composition.CanvasWindingRule;
import net.zerocloud.pdf.composition.command.DrawBarcode1D;
import net.zerocloud.pdf.composition.command.DrawCanvas;
import uk.org.okapibarcode.backend.Code128;
import uk.org.okapibarcode.backend.Code3Of9;
import uk.org.okapibarcode.backend.Code3Of9Extended;
import uk.org.okapibarcode.backend.Code2Of5;
import uk.org.okapibarcode.backend.Codabar;
import uk.org.okapibarcode.backend.Ean;
import uk.org.okapibarcode.backend.EanUpcAddOn;
import uk.org.okapibarcode.backend.Upc;
import uk.org.okapibarcode.backend.MsiPlessey;
import uk.org.okapibarcode.backend.Postnet;
import uk.org.okapibarcode.backend.HumanReadableLocation;
import uk.org.okapibarcode.backend.OkapiInputException;
import uk.org.okapibarcode.backend.Symbol;
import uk.org.okapibarcode.graphics.Rectangle;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/** Okapi owns symbol encoding; Folio owns the bounded PDF vector program. */
final class PdfBoxBarcodeOperations {
    static final String CAPABILITY_ID = "composition.barcodes.one-dimensional";
    private final PdfBoxCanvasOperations canvas;
    private final WorkflowResourceContext resources;
    private final PDDocument document;
    private final PdfBoxPositionedTextOperations text;

    PdfBoxBarcodeOperations(PDDocument document, PdfBoxCanvasOperations canvas,
            PdfBoxPositionedTextOperations text, WorkflowResourceContext resources) {
        this.document = document; this.canvas = canvas; this.text = text; this.resources = resources;
    }

    void execute(DrawBarcode1D command) throws DocumentFailure {
        selectedPage(command.getPageNumber());
        Barcode1D barcode = command.getBarcode();
        if (barcode.getContent().length() > 256 || barcode.getRawCodewordCount() > 256) {
            throw limitFailure();
        }
        validateGeometry(barcode, command.getPlacement());
        boolean twoWidth = barcode.getMode() == Barcode1D.Mode.CODE39 || barcode.getMode() == Barcode1D.Mode.CODE39_EXTENDED
                || barcode.getMode() == Barcode1D.Mode.CODABAR || barcode.getMode() == Barcode1D.Mode.INTERLEAVED_2_OF_5;
        boolean optionalChecksum = twoWidth || barcode.getMode() == Barcode1D.Mode.MSI;
        if ((barcode.getMode() != Barcode1D.Mode.CODE128 && barcode.getMode() != Barcode1D.Mode.GS1_128
                        && barcode.getCodeSet() != Barcode1D.CodeSet.AUTO)
                || (!optionalChecksum && barcode.isGenerateChecksum())
                || (!twoWidth && barcode.getWideToNarrowRatio() != 2)
                || (retailBodyLength(barcode.getMode()) == 0 && barcode.getGuardExtension() != 0)
                || (!postal(barcode) && (barcode.getPostalPitch() != 0 || barcode.getShortBarHeight() != 0))) {
            throw invalidMode();
        }
        boolean raw = barcode.getMode() == Barcode1D.Mode.CODE128_RAW;
        if (raw) {
            if (!barcode.getContent().isEmpty() || barcode.getCodeSet() != Barcode1D.CodeSet.AUTO) {
                throw invalidMode();
            }
            BarcodeRawCode128.validate(barcode.getRawCodewords());
        }
        if (!raw && barcode.getContent().isEmpty()) { throw invalidInput(); }
        for (int index = 0; index < barcode.getContent().length(); index++) {
            char character = barcode.getContent().charAt(index);
            if (character > 127 && !(barcode.getMode() == Barcode1D.Mode.CODE128
                    && character >= Barcode1D.FNC1 && character <= Barcode1D.FNC4)) { throw invalidInput(); }
        }
        if (barcode.getMode() == Barcode1D.Mode.CODE128) { validateFunctions(barcode.getContent()); }
        if (barcode.getMode() == Barcode1D.Mode.GS1_128) {
            String content = barcode.getContent();
            for (int index = 0; index < content.length(); index++) {
                if (content.charAt(index) == ']' && (index + 1 == content.length() || content.charAt(index + 1) == '[')) {
                    throw invalidInput();
                }
            }
        }
        try (WorkflowResourceContext.MemoryReservation ignored = resources.reserveOwnedMemory(
                131072L + 4096L * (barcode.getContent().length() + barcode.getRawCodewordCount()))) {
            boolean literalEscape = barcode.getMode() == Barcode1D.Mode.CODE128 && barcode.getContent().contains("\\<FNC");
            Symbol symbol;
            if (barcode.getMode() == Barcode1D.Mode.CODE39) {
                Code3Of9 standard = new Code3Of9();
                standard.setCheckDigit(barcode.isGenerateChecksum() ? Code3Of9.CheckDigit.MOD43 : Code3Of9.CheckDigit.NONE);
                standard.setModuleWidthRatio(barcode.getWideToNarrowRatio());
                symbol = standard;
            } else if (barcode.getMode() == Barcode1D.Mode.CODE39_EXTENDED) {
                Code3Of9Extended extended = new BarcodeFullAsciiCode39();
                extended.setCheckDigit(barcode.isGenerateChecksum() ? Code3Of9Extended.CheckDigit.MOD43 : Code3Of9Extended.CheckDigit.NONE);
                extended.setModuleWidthRatio(barcode.getWideToNarrowRatio());
                symbol = extended;
            } else if (barcode.getMode() == Barcode1D.Mode.CODABAR) {
                Codabar codabar = new Codabar();
                codabar.setModuleWidthRatio(barcode.getWideToNarrowRatio());
                symbol = codabar;
            } else if (barcode.getMode() == Barcode1D.Mode.EAN13 || barcode.getMode() == Barcode1D.Mode.EAN8) {
                Ean ean = new Ean(Ean.Mode.valueOf(barcode.getMode().name()));
                ean.setGuardPatternExtraHeight(1);
                symbol = ean;
            } else if (barcode.getMode() == Barcode1D.Mode.UPCA || barcode.getMode() == Barcode1D.Mode.UPCE) {
                Upc upc = new Upc(Upc.Mode.valueOf(barcode.getMode().name()));
                upc.setGuardPatternExtraHeight(1);
                symbol = upc;
            } else if (barcode.getMode() == Barcode1D.Mode.SUPPLEMENT2 || barcode.getMode() == Barcode1D.Mode.SUPPLEMENT5) {
                symbol = new EanUpcAddOn();
            } else if (barcode.getMode() == Barcode1D.Mode.INTERLEAVED_2_OF_5) {
                Code2Of5 interleaved = new Code2Of5(barcode.isGenerateChecksum()
                        ? Code2Of5.ToFMode.INTERLEAVED_WITH_CHECK_DIGIT : Code2Of5.ToFMode.INTERLEAVED);
                interleaved.setModuleWidthRatio(barcode.getWideToNarrowRatio());
                symbol = interleaved;
            } else if (barcode.getMode() == Barcode1D.Mode.MSI) {
                MsiPlessey msi = new MsiPlessey();
                msi.setCheckDigit(barcode.isGenerateChecksum() ? MsiPlessey.CheckDigit.MOD10 : MsiPlessey.CheckDigit.NONE);
                msi.setCheckDigitInHumanReadableText(true);
                symbol = msi;
            } else if (postal(barcode)) {
                Postnet postnet = new Postnet(barcode.getMode() == Barcode1D.Mode.POSTNET ? Postnet.Mode.POSTNET : Postnet.Mode.PLANET);
                postnet.setModuleWidthRatio(barcode.getPostalPitch() / barcode.getModuleWidth() - 1);
                symbol = postnet;
            } else {
                Code128.CodeSet codeSet = barcode.getCodeSet() == Barcode1D.CodeSet.AUTO
                        ? Code128.CodeSet.ABC : Code128.CodeSet.valueOf(barcode.getCodeSet().name());
                if (barcode.getCodeSet() == Barcode1D.CodeSet.AUTO && barcode.getContent().indexOf(Barcode1D.FNC4) >= 0) {
                    // Numeric pairs cannot preserve explicit FNC4 upper shifts or latches.
                    codeSet = Code128.CodeSet.AB;
                }
                symbol = raw ? new BarcodeRawCode128(barcode.getRawCodewords())
                    : literalEscape ? BarcodeRawCode128.literal(barcode)
                    : new Code128(codeSet);
            }
            if (barcode.getMode() == Barcode1D.Mode.GS1_128) { symbol.setDataType(Symbol.DataType.GS1); }
            symbol.setHumanReadableLocation(HumanReadableLocation.NONE);
            symbol.setBarHeight(postal(barcode) ? 10 : 1);
            String content = barcode.getContent();
            int retailLength = retailBodyLength(barcode.getMode());
            if (barcode.getMode() == Barcode1D.Mode.POSTNET && !content.matches("[0-9]{5}|[0-9]{9}|[0-9]{11}")
                    || barcode.getMode() == Barcode1D.Mode.PLANET && !content.matches("[0-9]{11}|[0-9]{13}")) { throw invalidInput(); }
            if (barcode.getMode() == Barcode1D.Mode.INTERLEAVED_2_OF_5
                    && content.length() % 2 == (barcode.isGenerateChecksum() ? 0 : 1)) { throw invalidInput(); }
            if (barcode.getMode() == Barcode1D.Mode.SUPPLEMENT2 && !content.matches("[0-9]{2}")
                    || barcode.getMode() == Barcode1D.Mode.SUPPLEMENT5 && !content.matches("[0-9]{5}")) { throw invalidInput(); }
            if (retailLength != 0) {
                if (!content.matches("[0-9]+") || (content.length() != retailLength && content.length() != retailLength + 1)) {
                    throw invalidInput();
                }
                content = content.substring(0, retailLength);
            }
            if (barcode.getMode() == Barcode1D.Mode.CODABAR && barcode.isGenerateChecksum()) {
                if (!content.matches("[A-D][0-9:$./+\\-]+[A-D]")) { throw invalidInput(); }
                String alphabet = "0123456789-$:/.+ABCD";
                int sum = 0;
                for (int index = 0; index < content.length(); index++) { sum += alphabet.indexOf(content.charAt(index)); }
                int end = content.length() - 1;
                content = content.substring(0, end) + alphabet.charAt((16 - sum % 16) % 16) + content.charAt(end);
            }
            if (barcode.getMode() == Barcode1D.Mode.CODE128 && !literalEscape) {
                for (int function = 1; function <= 4; function++) {
                    content = content.replace(String.valueOf((char) (Barcode1D.FNC1 + function - 1)), "\\<FNC" + function + ">");
                }
            }
            symbol.setContent(raw || literalEscape ? "raw" : content);
            if (retailLength != 0 && barcode.getContent().length() == retailLength + 1
                    && !barcode.getContent().equals(symbol.getHumanReadableText())) { throw invalidInput(); }
            CanvasProgram.Builder program = barcode.getHumanReadable().isPresent()
                    ? CanvasProgram.version2().saveState().setFillColor(CanvasColor.gray(0))
                    : CanvasProgram.version1().saveState().transform(command.getPlacement());
            double symbolWidth = appendBars(program, symbol, barcode, barcode.getQuietZone());
            if (!barcode.getSupplement().isEmpty()) {
                if (retailLength == 0) { throw invalidMode(); }
                if (!barcode.getSupplement().matches("[0-9]{2}|[0-9]{5}")) { throw invalidInput(); }
                EanUpcAddOn extension = new EanUpcAddOn();
                extension.setHumanReadableLocation(HumanReadableLocation.NONE);
                extension.setBarHeight(1);
                extension.setContent(barcode.getSupplement());
                symbolWidth += 9 * barcode.getModuleWidth()
                        + appendBars(program, extension, barcode, barcode.getQuietZone() + symbolWidth + 9 * barcode.getModuleWidth());
            }
            CanvasProgram bars = program.restoreState().build();
            if (barcode.getHumanReadable().isPresent()) {
                drawWithText(command, bars, symbolWidth + 2 * barcode.getQuietZone(),
                        BarcodeLabels.caption(barcode, symbol.getHumanReadableText(), content));
            } else {
                canvas.execute(DrawCanvas.version1(command.getPageNumber(), bars));
            }
        } catch (OkapiInputException failure) {
            throw invalidInput();
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw new DocumentFailure(DocumentFailureCode.DOCUMENT_WRITE_FAILED, CAPABILITY_ID,
                    "The barcode could not be applied safely.");
        }
    }

    private void drawWithText(DrawBarcode1D command, CanvasProgram bars, double symbolWidth, String caption) throws DocumentFailure {
        PDPage page = selectedPage(command.getPageNumber());
        PdfBoxPageContentSupport.ExistingContents existing = PdfBoxPageContentSupport.prepareExistingContents(
                page.getCOSObject(), PdfBoxCanvasOperations::preservationUnsupported, resources);
        COSDictionary effective = PdfBoxPageContentSupport.effectiveResources(
                page.getCOSObject(), PdfBoxCanvasOperations::preservationUnsupported, resources);
        PDPage detached = new PDPage(page.getMediaBox());
        if (effective != null) { detached.getCOSObject().setItem(COSName.RESOURCES, new COSDictionary(effective)); }
        BarcodeText label = command.getBarcode().getHumanReadable().get();
        if (!positive(label.getFontSize()) || !Double.isFinite(label.getGap()) || label.getGap() < 0
                || label.getGap() > 1000000 || caption.isEmpty()) { throw invalidMode(); }
        if (caption.length() > 1024) { throw limitFailure(); }
        int count = caption.codePointCount(0, caption.length());
        try (PdfBoxPositionedTextOperations.PreparedText prepared = text.prepareBarcodeText(
                caption, label.getFontSelection(), label.getFontLimits())) {
            double width = 0, ascent = 0, descent = 0;
            for (int index = 0; index < count; index++) {
                width += prepared.width(index, label.getFontSize());
                ascent = Math.max(ascent, prepared.ascent(index, label.getFontSize()));
                descent = Math.max(descent, prepared.descent(index, label.getFontSize()));
            }
            CanvasResourceLimits limits = CanvasResourceLimits.builder().maximumEncodedImageBytes(0)
                    .maximumDecodedImagePixels(0).maximumDecodedImageBytes(0).maximumIccProfileBytes(0)
                    .maximumMaskBytes(0).maximumGeneratedContentBytes(1048576).maximumResourceDeclarations(0)
                    .maximumTransparencyGroupDepth(0).build();
            long bytes = canvas.drawLayoutGraphic(DrawCanvas.version2(1, bars, limits), detached, 1048576);
            double x = label.getAlignment() == BarcodeText.Alignment.LEFT ? 0
                    : label.getAlignment() == BarcodeText.Alignment.RIGHT ? symbolWidth - width : (symbolWidth - width) / 2;
            double y = label.getPosition() == BarcodeText.Position.ABOVE
                    ? command.getBarcode().getBarHeight() + label.getGap() + descent
                    : -command.getBarcode().getGuardExtension() - label.getGap() - ascent;
            prepared.draw(detached, 0, count, label.getFontSize(), x, y,
                    Math.min(1048576 - bytes, label.getFontLimits().getMaximumGeneratedContentBytes()));
            PdfBoxPageContentSupport.ExistingContents drawing = PdfBoxPageContentSupport.prepareExistingContents(
                    detached.getCOSObject(), PdfBoxCanvasOperations::preservationUnsupported, resources);
            try (WorkflowAsciiOutput prefix = new WorkflowAsciiOutput(resources, 512, PdfBoxBarcodeOperations::limitFailure)) {
                prefix.append("q\n");
                PdfBoxPageContentSupport.appendMatrix(prefix, command.getPlacement(), PdfBoxBarcodeOperations::invalidMode);
                prefix.append(" cm\n");
                try (WorkflowResourceContext.OwnedBytes start = prefix.finishWorking();
                        WorkflowResourceContext.OwnedByteAccumulator combined = resources.ownedByteAccumulator()) {
                    combined.write(start.getBytes());
                    for (COSBase value : drawing.values()) {
                        COSStream stream = (COSStream) PdfBoxPageContentSupport.dereference(value, resources);
                        PdfBoxHostileInputPreflight.decodeStream(stream, resources, combined);
                        combined.write('\n');
                    }
                    combined.write("Q\n".getBytes(StandardCharsets.US_ASCII));
                    try (WorkflowResourceContext.OwnedBytes operators = combined.finishWorking()) {
                        PdfBoxPageContentSupport.apply(document, page, existing, operators.getBytes(),
                                (COSDictionary) detached.getCOSObject().getDictionaryObject(COSName.RESOURCES),
                                true, resources, PdfBoxBarcodeOperations::writeFailure);
                    }
                }
            } catch (IOException failure) {
                resources.rethrowResourceOrTerminalFailure(failure);
                throw writeFailure();
            }
        }
    }

    private static DocumentFailure writeFailure() {
        return new DocumentFailure(DocumentFailureCode.DOCUMENT_WRITE_FAILED, CAPABILITY_ID,
                "The barcode could not be applied safely.");
    }

    private PDPage selectedPage(int pageNumber) throws DocumentFailure {
        if (pageNumber < 1 || pageNumber > document.getNumberOfPages()) {
            throw new DocumentFailure(DocumentFailureCode.PAGE_RANGE_INVALID, CAPABILITY_ID, "The barcode page selection is invalid.");
        }
        return document.getPage(pageNumber - 1);
    }

    static DocumentFailure signatureFailure() {
        return new DocumentFailure(DocumentFailureCode.SIGNATURE_POLICY_REJECTED, CAPABILITY_ID,
                "The Existing Signature policy does not permit barcode drawing.");
    }

    static void requirePermission(PasswordSecurityInfo security) throws DocumentFailure {
        if (security.isPasswordProtected() && !security.getEffectivePermissions().canModify()) {
            throw new DocumentFailure(DocumentFailureCode.DOCUMENT_PERMISSION_DENIED, CAPABILITY_ID,
                    "The Source credential does not authorize barcode drawing.");
        }
    }

    private double appendBars(CanvasProgram.Builder program, Symbol symbol, Barcode1D barcode, double offset) throws DocumentFailure {
        if (symbol.getRectangles().size() > 1600) { throw limitFailure(); }
        double right = 0;
        for (Rectangle rectangle : symbol.getRectangles()) {
                resources.checkpoint();
                double x = offset + rectangle.x * barcode.getModuleWidth();
                double width = rectangle.width * barcode.getModuleWidth();
                double height = postal(barcode) && rectangle.height < 10 ? barcode.getShortBarHeight() : barcode.getBarHeight();
                double bottom = retailBodyLength(barcode.getMode()) != 0 && rectangle.height > 1 ? -barcode.getGuardExtension() : 0;
                program.moveTo(x, bottom).lineTo(x + width, bottom).lineTo(x + width, height)
                        .lineTo(x, height).closePath().fill(CanvasWindingRule.NONZERO);
                right = Math.max(right, x + width - offset);
        }
        return right;
    }

    static DocumentFailure invalidInput() {
        return new DocumentFailure(DocumentFailureCode.BARCODE_INPUT_INVALID, CAPABILITY_ID,
                "The barcode input is invalid for the selected symbology.");
    }

    private static void validateFunctions(String content) throws DocumentFailure {
        boolean data = false;
        for (int index = 0; index < content.length(); index++) {
            char character = content.charAt(index);
            if (character <= 127) { data = true; }
            if (character == Barcode1D.FNC4) {
                if (index + 1 < content.length() && content.charAt(index + 1) == Barcode1D.FNC4) { index++; }
                if (index + 1 == content.length() || content.charAt(index + 1) > 127) { throw invalidInput(); }
            }
        }
        if (!data) { throw invalidInput(); }
    }

    static DocumentFailure invalidMode() {
        return new DocumentFailure(DocumentFailureCode.BARCODE_MODE_INVALID, CAPABILITY_ID,
                "The barcode options are invalid for the selected mode.");
    }

    static DocumentFailure limitFailure() {
        return new DocumentFailure(DocumentFailureCode.BARCODE_LIMIT_EXCEEDED, CAPABILITY_ID,
                "The barcode operation limit was exceeded.");
    }

    private static void validateGeometry(Barcode1D barcode, CanvasMatrix matrix) throws DocumentFailure {
        if (!positive(barcode.getModuleWidth()) || !positive(barcode.getBarHeight())
                || !positive(barcode.getQuietZone())
                || barcode.getQuietZone() < (postal(barcode) ? 9 : (barcode.getMode() == Barcode1D.Mode.EAN13 ? 11 : 10) * barcode.getModuleWidth())
                || !Double.isFinite(barcode.getGuardExtension()) || barcode.getGuardExtension() < 0 || barcode.getGuardExtension() > 1000000
                || (postal(barcode) && (!positive(barcode.getPostalPitch()) || barcode.getPostalPitch() <= barcode.getModuleWidth()
                        || !positive(barcode.getShortBarHeight()) || barcode.getShortBarHeight() >= barcode.getBarHeight()))
                || !(barcode.getWideToNarrowRatio() >= 2 && barcode.getWideToNarrowRatio() <= 3)
                || !PdfBoxPageContentSupport.isValidMatrix(matrix)
                || matrix.getA() * matrix.getD() - matrix.getB() * matrix.getC() == 0) {
            throw new DocumentFailure(DocumentFailureCode.BARCODE_GEOMETRY_INVALID, CAPABILITY_ID,
                    "The barcode dimensions or placement are invalid.");
        }
    }

    private static boolean positive(double number) {
        return number > 0 && number <= 1000000 && Double.isFinite(number);
    }

    private static int retailBodyLength(Barcode1D.Mode mode) {
        switch (mode) {
            case EAN13: return 12;
            case EAN8: case UPCE: return 7;
            case UPCA: return 11;
            default: return 0;
        }
    }

    private static boolean postal(Barcode1D barcode) {
        return barcode.getMode() == Barcode1D.Mode.POSTNET || barcode.getMode() == Barcode1D.Mode.PLANET;
    }
}
