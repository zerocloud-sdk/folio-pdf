package net.zerocloud.pdf;

import java.io.IOException;
import net.zerocloud.pdf.composition.Barcode2D;
import net.zerocloud.pdf.composition.command.DrawBarcode2D;

/** Closed representation of immutable two-dimensional barcode declarations. */
final class WorkerBarcode2DCodec {
    private WorkerBarcode2DCodec() { }
    static void write(WorkerCodecIO.Output output, DrawBarcode2D command) throws IOException, DocumentFailure {
        PdfBoxBarcode2DOperations.admit(command.getBarcode());
        output.writeInt(command.getVersion());
        output.writeInt(command.getPageNumber());
        writeDeclaration(output,command.getBarcode());
        WorkerCompositionCodec.writeMatrix(output, command.getPlacement());
    }
    static void writeDeclaration(WorkerCodecIO.Output output, Barcode2D barcode) throws IOException, DocumentFailure {
        PdfBoxBarcode2DOperations.admit(barcode);
        output.writeInt(barcode.getVersion());
        output.writeString(barcode.getMode().name());
        output.writeString(barcode.getContent());
        output.writeString(barcode.getEncoding());
        output.writeString(barcode.getQrErrorCorrection().name());
        output.writeInt(barcode.getQrVersion());
        output.writeInt(barcode.getDataMatrixWidth());
        output.writeInt(barcode.getDataMatrixHeight());
        output.writeString(barcode.getDataMatrixEncoding().name());
        output.writeString(barcode.getDataMatrixMacro().name());
        output.writeBoolean(barcode.isDataMatrixFnc1());
        output.writeBoolean(barcode.isDataMatrixReaderProgramming());
        output.writeInt(barcode.getDataMatrixSequencePosition());
        output.writeInt(barcode.getDataMatrixSequenceTotal());
        output.writeInt(barcode.getDataMatrixFileId());
        output.writeInt(barcode.getPdf417Columns()); output.writeInt(barcode.getPdf417Rows()); output.writeInt(barcode.getPdf417ErrorCorrection());
        output.writeString(barcode.getPdf417Encoding().name());
        output.writeDouble(barcode.getPdf417AspectRatio());
        output.writeString(barcode.getPdf417MacroFileId()); output.writeInt(barcode.getPdf417MacroSegment()); output.writeInt(barcode.getPdf417MacroCount());
        output.writeInt(barcode.getRawCodewordCount());
        for (int word : barcode.getRawCodewords()) { output.writeInt(word); }
        output.writeDouble(barcode.getModuleWidth());
        output.writeDouble(barcode.getModuleHeight());
        output.writeDouble(barcode.getQuietZone());
        for (double component : barcode.getForegroundRgb()) { output.writeDouble(component); }
    }
    static DrawBarcode2D read(WorkerCodecIO.Input input) throws DocumentFailure {
        WorkerCommandCodec.requireVersion(input.readInt(), DrawBarcode2D.VERSION_1);
        int page = input.readInt();
        Barcode2D barcode = readDeclaration(input);
        return DrawBarcode2D.version1(page, barcode, WorkerCompositionCodec.readMatrix(input));
    }
    static Barcode2D readDeclaration(WorkerCodecIO.Input input) throws DocumentFailure {
        WorkerCommandCodec.requireVersion(input.readInt(), Barcode2D.VERSION_1);
        Barcode2D.Mode mode = WorkerCommandCodec.enumValue(Barcode2D.Mode.class, input.readString(), "barcode mode");
        String content = input.readString(), encoding = input.readString();
        Barcode2D.QrErrorCorrection correction = WorkerCommandCodec.enumValue(Barcode2D.QrErrorCorrection.class, input.readString(), "barcode correction");
        int version = input.readInt(), width = input.readInt(), height = input.readInt();
        Barcode2D.DataMatrixEncoding compaction = WorkerCommandCodec.enumValue(Barcode2D.DataMatrixEncoding.class, input.readString(), "barcode compaction");
        Barcode2D.DataMatrixMacro macro = WorkerCommandCodec.enumValue(Barcode2D.DataMatrixMacro.class, input.readString(), "barcode macro");
        boolean fnc1 = input.readBoolean(), reader = input.readBoolean();
        int position = input.readInt(), total = input.readInt(), fileId = input.readInt();
        int columns = input.readInt(), rows = input.readInt(), pdfEcc = input.readInt();
        Barcode2D.Pdf417Encoding pdfCompaction = WorkerCommandCodec.enumValue(Barcode2D.Pdf417Encoding.class,input.readString(),"PDF417 compaction");
        double aspectRatio = input.readDouble();
        String pdfFileId = input.readString();
        int pdfSegment = input.readInt(), pdfCount = input.readInt();
        int count = input.readInt();
        if (count < 0 || count > 8192) { throw WorkerCommandCodec.rejected("The Worker barcode symbol count is invalid."); }
        input.accountCollectionEntries(count);
        int[] words = new int[count];
        for (int index = 0; index < count; index++) { words[index] = input.readInt(); }
        boolean dmRaw = compaction == Barcode2D.DataMatrixEncoding.RAW, pdfRaw = pdfCompaction == Barcode2D.Pdf417Encoding.RAW;
        if (dmRaw && (mode != Barcode2D.Mode.DATA_MATRIX || !content.isEmpty()) || pdfRaw && (mode != Barcode2D.Mode.PDF417 || !content.isEmpty())
                || !dmRaw && !pdfRaw && count != 0) { throw WorkerCommandCodec.rejected("The Worker barcode declaration is inconsistent."); }
        Barcode2D.Builder builder = dmRaw ? Barcode2D.rawDataMatrix(words) : pdfRaw ? Barcode2D.rawPdf417(words) : Barcode2D.builder(mode, content);
        Barcode2D barcode = builder.encoding(encoding).qrErrorCorrection(correction).qrVersion(version).dataMatrixSize(width, height)
                .dataMatrixMacro(macro).dataMatrixFnc1(fnc1).dataMatrixReaderProgramming(reader).dataMatrixStructuredAppend(position,total,fileId)
                .pdf417Columns(columns).pdf417Rows(rows).pdf417ErrorCorrection(pdfEcc).pdf417Encoding(pdfCompaction).pdf417AspectRatio(aspectRatio)
                .pdf417Macro(pdfFileId,pdfSegment,pdfCount)
                .dataMatrixEncoding(compaction).moduleWidth(input.readDouble()).moduleHeight(input.readDouble()).quietZone(input.readDouble())
                .foregroundRgb(input.readDouble(),input.readDouble(),input.readDouble()).build();
        PdfBoxBarcode2DOperations.admit(barcode);
        return barcode;
    }
}
