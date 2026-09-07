package net.zerocloud.pdf;

import java.io.IOException;
import net.zerocloud.pdf.composition.Barcode1D;
import net.zerocloud.pdf.composition.BarcodeText;
import net.zerocloud.pdf.composition.FontLimits;
import net.zerocloud.pdf.composition.FontSelection;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.composition.command.DrawBarcode1D;

/** Closed, bounded representation of semantic barcode commands. */
final class WorkerBarcodeCodec {
    private WorkerBarcodeCodec() { }

    static void write(WorkerCodecIO.Output output, DrawBarcode1D command, WorkerFontSourceCache fonts) throws IOException, DocumentFailure {
        Barcode1D barcode = command.getBarcode();
        if (barcode.getContent().length() > 256 || barcode.getRawCodewordCount() > 256) {
            throw PdfBoxBarcodeOperations.limitFailure();
        }
        output.writeInt(command.getVersion());
        output.writeInt(command.getPageNumber());
        output.writeInt(barcode.getVersion());
        output.writeString(barcode.getMode().name());
        output.writeString(barcode.getContent());
        output.writeString(barcode.getCodeSet().name());
        output.writeInt(barcode.getRawCodewordCount());
        for (int word : barcode.getRawCodewords()) { output.writeInt(word); }
        output.writeDouble(barcode.getModuleWidth());
        output.writeDouble(barcode.getBarHeight());
        output.writeDouble(barcode.getQuietZone());
        output.writeBoolean(barcode.isGenerateChecksum());
        output.writeDouble(barcode.getWideToNarrowRatio());
        output.writeString(barcode.getSupplement());
        output.writeDouble(barcode.getGuardExtension());
        output.writeDouble(barcode.getPostalPitch());
        output.writeDouble(barcode.getShortBarHeight());
        output.writeBoolean(barcode.getHumanReadable().isPresent());
        if (barcode.getHumanReadable().isPresent()) {
            BarcodeText text = barcode.getHumanReadable().get();
            output.writeInt(text.getVersion());
            WorkerCompositionCodec.writeFontLimits(output, text.getFontLimits());
            WorkerCompositionCodec.writeFontSelection(output, text.getFontSelection(), text.getFontLimits(), fonts);
            output.writeDouble(text.getFontSize());
            output.writeBoolean(text.isShowChecksum());
            output.writeBoolean(text.isShowStartStop());
            output.writeBoolean(text.getAlternateText().isPresent());
            if (text.getAlternateText().isPresent()) { output.writeString(text.getAlternateText().get()); }
            output.writeString(text.getPosition().name());
            output.writeString(text.getAlignment().name());
            output.writeDouble(text.getGap());
        }
        WorkerCompositionCodec.writeMatrix(output, command.getPlacement());
    }

    static DrawBarcode1D read(WorkerCodecIO.Input input, WorkerCompositionCodec.RemoteFontSource fonts) throws DocumentFailure {
        WorkerCommandCodec.requireVersion(input.readInt(), DrawBarcode1D.VERSION_1);
        int page = input.readInt();
        WorkerCommandCodec.requireVersion(input.readInt(), Barcode1D.VERSION_1);
        Barcode1D.Mode mode = WorkerCommandCodec.enumValue(Barcode1D.Mode.class, input.readString(), "barcode mode");
        String content = input.readString();
        Barcode1D.CodeSet codeSet = WorkerCommandCodec.enumValue(Barcode1D.CodeSet.class, input.readString(), "barcode code set");
        int count = input.readInt();
        if (count < 0 || count > 256) { throw WorkerCommandCodec.rejected("The Worker barcode symbol count is invalid."); }
        input.accountCollectionEntries(count);
        int[] words = new int[count];
        for (int index = 0; index < count; index++) { words[index] = input.readInt(); }
        if (mode == Barcode1D.Mode.CODE128_RAW && !content.isEmpty()) { throw PdfBoxBarcodeOperations.invalidMode(); }
        Barcode1D.Builder barcode = mode == Barcode1D.Mode.CODE128_RAW ? Barcode1D.rawCode128(words) : Barcode1D.builder(mode, content);
        barcode.codeSet(codeSet).moduleWidth(input.readDouble()).barHeight(input.readDouble()).quietZone(input.readDouble())
                .generateChecksum(input.readBoolean()).wideToNarrowRatio(input.readDouble()).supplement(input.readString());
        barcode.guardExtension(input.readDouble()).postalPitch(input.readDouble()).shortBarHeight(input.readDouble());
        if (input.readBoolean()) {
            WorkerCommandCodec.requireVersion(input.readInt(), BarcodeText.VERSION_1);
            FontLimits limits = WorkerCompositionCodec.readFontLimits(input);
            FontSelection selection = WorkerCompositionCodec.readFontSelection(input, fonts);
            BarcodeText.Builder text = BarcodeText.builder(selection, limits, input.readDouble())
                    .showChecksum(input.readBoolean()).showStartStop(input.readBoolean());
            if (input.readBoolean()) { text.alternateText(input.readString()); }
            text.position(WorkerCommandCodec.enumValue(BarcodeText.Position.class, input.readString(), "barcode text position"))
                    .alignment(WorkerCommandCodec.enumValue(BarcodeText.Alignment.class, input.readString(), "barcode text alignment"))
                    .gap(input.readDouble());
            barcode.humanReadable(text.build());
        }
        CanvasMatrix matrix = WorkerCompositionCodec.readMatrix(input);
        return DrawBarcode1D.version1(page, barcode.build(), matrix);
    }
}
