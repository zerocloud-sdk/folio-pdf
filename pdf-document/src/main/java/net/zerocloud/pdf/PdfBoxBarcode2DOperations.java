package net.zerocloud.pdf;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.zerocloud.pdf.composition.Barcode2D;
import net.zerocloud.pdf.composition.Barcode2DSize;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.composition.command.DrawBarcode2D;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import uk.org.okapibarcode.backend.OkapiInputException;
import uk.org.okapibarcode.backend.Symbol;
import uk.org.okapibarcode.graphics.Rectangle;

/** Bounded symbol encoding and project-owned reusable PDF vector content. */
final class PdfBoxBarcode2DOperations {
    static final String CAPABILITY_ID = "composition.barcodes.two-dimensional";
    private final PDDocument document;
    private final WorkflowResourceContext resources;
    private final Map<String, COSObject> forms = new LinkedHashMap<String, COSObject>();

    PdfBoxBarcode2DOperations(PDDocument document, WorkflowResourceContext resources) {
        this.document = document; this.resources = resources;
    }

    Barcode2DSize measure(Barcode2D declaration) throws DocumentFailure {
        validate(declaration, CanvasMatrix.IDENTITY);
        try (WorkflowResourceContext.MemoryReservation ignored = resources.reserveOwnedMemory(8L * 1024 * 1024)) {
            return size(declaration, encode(declaration));
        } catch (OkapiInputException failure) { throw inputFailure(); }
        catch (RuntimeException failure) { resources.rethrowResourceOrTerminalFailure(failure); throw writeFailure(); }
    }

    private Symbol encode(Barcode2D declaration) throws DocumentFailure {
        if (declaration.getMode() == Barcode2D.Mode.PDF417 && (declaration.getPdf417Encoding() == Barcode2D.Pdf417Encoding.RAW
                || declaration.getPdf417MacroCount() != 0)) {
            return new BarcodePdf417Grid(declaration);
        }
        if (declaration.getMode() == Barcode2D.Mode.PDF417) { return BarcodePdf417Encoder.create(declaration,resources); }
        return declaration.getMode() == Barcode2D.Mode.QR ? new BarcodeQrEncoder(declaration) : BarcodeDataMatrixEncoder.create(declaration);
    }
    private static Barcode2DSize size(Barcode2D declaration, Symbol symbol) throws DocumentFailure {
        double width = 2 * declaration.getQuietZone() + symbol.getWidth() * declaration.getModuleWidth();
        double height = 2 * declaration.getQuietZone() + symbol.getHeight() * declaration.getModuleHeight();
        if (!positive(width) || !positive(height)) { throw geometryFailure(); }
        return Barcode2DSize.version1(symbol.getWidth(),symbol.getHeight(),width,height);
    }

    void execute(DrawBarcode2D command) throws DocumentFailure {
        if (command.getPageNumber() < 1 || command.getPageNumber() > document.getNumberOfPages()) {
            throw new DocumentFailure(DocumentFailureCode.PAGE_RANGE_INVALID, CAPABILITY_ID, "The barcode page selection is invalid.");
        }
        validate(command.getBarcode(), command.getPlacement());
        PDPage page = document.getPage(command.getPageNumber() - 1);
        PdfBoxPageContentSupport.ExistingContents existing = PdfBoxPageContentSupport.prepareExistingContents(
                page.getCOSObject(), PdfBoxCanvasOperations::preservationUnsupported, resources);
        COSDictionary effective = PdfBoxPageContentSupport.effectiveResources(
                page.getCOSObject(), PdfBoxCanvasOperations::preservationUnsupported, resources);
        COSDictionary updated = effective == null ? new COSDictionary() : new COSDictionary(effective);
        COSBase oldObjects = PdfBoxPageContentSupport.dereference(updated.getItem(COSName.XOBJECT), resources);
        if (oldObjects != null && !(oldObjects instanceof COSDictionary)) { throw PdfBoxCanvasOperations.preservationUnsupported(); }
        COSDictionary objects = oldObjects == null ? new COSDictionary() : new COSDictionary((COSDictionary) oldObjects);
        try (WorkflowResourceContext.MemoryReservation ignored = resources.reserveOwnedMemory(8L * 1024 * 1024)) {
            Barcode2D declaration = command.getBarcode();
            Symbol symbol = encode(declaration);
            double unitX = declaration.getModuleWidth(), unitY = declaration.getModuleHeight(), quiet = declaration.getQuietZone();
            Barcode2DSize dimensions = size(declaration,symbol);
            double width = dimensions.getWidthPoints(), height = dimensions.getHeightPoints();
            CanvasMatrix matrix = command.getPlacement();
            for (double x : new double[] {0, width}) {
                for (double y : new double[] {0, height}) {
                    PdfBoxPageContentSupport.requireNumber(matrix.getA() * x + matrix.getC() * y + matrix.getE(), PdfBoxBarcode2DOperations::geometryFailure);
                    PdfBoxPageContentSupport.requireNumber(matrix.getB() * x + matrix.getD() * y + matrix.getF(), PdfBoxBarcode2DOperations::geometryFailure);
                }
            }
            try (WorkflowAsciiOutput vectors = new WorkflowAsciiOutput(resources, 4 * 1024 * 1024, PdfBoxBarcode2DOperations::limitFailure)) {
                double[] color = declaration.getForegroundRgb();
                if (color[0] == 0 && color[1] == 0 && color[2] == 0) { vectors.append("0 g\n"); }
                else {
                    PdfBoxPageContentSupport.appendNumbers(vectors,color,PdfBoxBarcode2DOperations::inputFailure);
                    vectors.append(" rg\n");
                }
                for (Rectangle rectangle : symbol.getRectangles()) {
                    resources.checkpoint();
                    PdfBoxPageContentSupport.appendNumbers(vectors, new double[] {
                        quiet + rectangle.x * unitX, quiet + (symbol.getHeight() - rectangle.y - rectangle.height) * unitY,
                        rectangle.width * unitX, rectangle.height * unitY}, PdfBoxBarcode2DOperations::geometryFailure);
                    vectors.append(" re f\n");
                }
                try (WorkflowResourceContext.OwnedBytes bytes = vectors.finishWorking()) {
                    String key = digest(bytes.getBytes()) + ":" + width + ":" + height;
                    COSObject form = forms.get(key);
                    if (form == null) {
                        form = form(bytes.getBytes(), width, height);
                        resources.retainOwnedMemory(256);
                        forms.put(key, form);
                    }
                    int suffix = 1;
                    COSName name = null;
                    for (COSName candidate : objects.keySet()) {
                        if (objects.getItem(candidate) == form) { name = candidate; break; }
                    }
                    if (name == null) {
                        do { name = COSName.getPDFName("FolioBarcode" + suffix++); } while (objects.containsKey(name));
                    }
                    objects.setItem(name, form);
                    updated.setItem(COSName.XOBJECT, objects);
                    try (WorkflowAsciiOutput placement = new WorkflowAsciiOutput(resources, 512, PdfBoxBarcode2DOperations::limitFailure)) {
                        placement.append("q\n");
                        PdfBoxPageContentSupport.appendMatrix(placement, command.getPlacement(), PdfBoxBarcode2DOperations::geometryFailure);
                        placement.append(" cm\n/"); placement.append(name.getName()); placement.append(" Do\nQ\n");
                        try (WorkflowResourceContext.OwnedBytes program = placement.finishWorking()) {
                            PdfBoxPageContentSupport.apply(document, page, existing, program.getBytes(), updated, true,
                                    resources, PdfBoxBarcode2DOperations::writeFailure);
                        }
                    }
                }
            }
        } catch (OkapiInputException failure) {
            throw inputFailure();
        } catch (RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw writeFailure();
        }
    }

    static void admit(Barcode2D declaration) throws DocumentFailure {
        if (declaration.getContent().length() > 8192 || declaration.getEncoding().length() > 32
                || declaration.getRawCodewordCount() > 8192 || declaration.getPdf417MacroFileId().length() > 2700) { throw limitFailure(); }
        boolean pdfMacro = !declaration.getPdf417MacroFileId().isEmpty() || declaration.getPdf417MacroSegment() != 0
                || declaration.getPdf417MacroCount() != 0;
        if (pdfMacro) {
            int count = declaration.getPdf417MacroCount(), segment = declaration.getPdf417MacroSegment();
            if (declaration.getMode() != Barcode2D.Mode.PDF417 || count < 1 || count > 99999 || segment < 0 || segment >= count) {
                throw modeFailure();
            }
            if (!declaration.getPdf417MacroFileId().matches("(?:[0-8][0-9][0-9])+")) { throw inputFailure(); }
        }
        boolean dmRaw = declaration.getDataMatrixEncoding() == Barcode2D.DataMatrixEncoding.RAW;
        boolean pdfRaw = declaration.getPdf417Encoding() == Barcode2D.Pdf417Encoding.RAW;
        boolean raw = dmRaw || pdfRaw;
        if (dmRaw && declaration.getMode() != Barcode2D.Mode.DATA_MATRIX || pdfRaw && declaration.getMode() != Barcode2D.Mode.PDF417
                || raw && (!declaration.getContent().isEmpty()
                || !declaration.getEncoding().equals("ISO-8859-1")) || !raw && declaration.getRawCodewordCount() != 0) {
            throw modeFailure();
        }
        int position = declaration.getDataMatrixSequencePosition(), total = declaration.getDataMatrixSequenceTotal();
        int fileId = declaration.getDataMatrixFileId();
        boolean sequence = position != 0 || total != 0 || fileId != 0;
        boolean macro = declaration.getDataMatrixMacro() != Barcode2D.DataMatrixMacro.NONE;
        boolean fnc1 = declaration.isDataMatrixFnc1(), reader = declaration.isDataMatrixReaderProgramming();
        if (sequence && (total < 2 || total > 16 || position < 1 || position > total || fileId < 1 || fileId > 64516)) {
            throw modeFailure();
        }
        if ((sequence || macro || fnc1 || reader) && (raw || declaration.getMode() != Barcode2D.Mode.DATA_MATRIX)
                || reader && fnc1 || macro && (sequence || reader)) { throw modeFailure(); }
    }

    private static void validate(Barcode2D declaration, CanvasMatrix matrix) throws DocumentFailure {
        admit(declaration);
        for (double component : declaration.getForegroundRgb()) {
            if (!Double.isFinite(component) || component < 0 || component > 1) { throw inputFailure(); }
        }
        if (declaration.getQrVersion() < 0 || declaration.getQrVersion() > 40) { throw geometryFailure(); }
        boolean qr = declaration.getMode() == Barcode2D.Mode.QR;
        if (declaration.getMode() != Barcode2D.Mode.DATA_MATRIX && (declaration.getDataMatrixWidth() != 0 || declaration.getDataMatrixHeight() != 0
                || declaration.getDataMatrixEncoding() != Barcode2D.DataMatrixEncoding.AUTO)) { throw modeFailure(); }
        if (!qr && (declaration.getQrVersion() != 0 || declaration.getQrErrorCorrection() != Barcode2D.QrErrorCorrection.L)) {
            throw modeFailure();
        }
        boolean pdf417 = declaration.getMode() == Barcode2D.Mode.PDF417;
        int columns = declaration.getPdf417Columns(), rows = declaration.getPdf417Rows(), correction = declaration.getPdf417ErrorCorrection();
        if (!pdf417 && (columns != 0 || rows != 0 || correction != -1 || declaration.getPdf417Encoding() != Barcode2D.Pdf417Encoding.AUTO
                || declaration.getPdf417AspectRatio() != 0)) {
            throw modeFailure();
        }
        if (pdf417) {
            if (declaration.getPdf417AspectRatio() != 0) {
                if (!positive(declaration.getPdf417AspectRatio())) { throw geometryFailure(); }
                if (columns != 0 || rows != 0) { throw modeFailure(); }
            }
            if (columns < 0 || columns > 30 || rows != 0 && (rows < 3 || rows > 90) || (long)columns * rows > 928
                    || declaration.getModuleHeight() < 3 * declaration.getModuleWidth()) { throw geometryFailure(); }
            if (correction < -1 || correction > 8) { throw modeFailure(); }
        }
        if (!positive(declaration.getModuleWidth()) || !positive(declaration.getModuleHeight())
                || !positive(declaration.getQuietZone()) || (qr && declaration.getModuleHeight() != declaration.getModuleWidth())
                || declaration.getQuietZone() < (declaration.getMode() == Barcode2D.Mode.PDF417 ? 2 * declaration.getModuleWidth()
                    : (qr ? 4 : 1) * Math.max(declaration.getModuleWidth(), declaration.getModuleHeight()))) {
            throw geometryFailure();
        }
        PdfBoxPageContentSupport.requireMatrix(matrix, PdfBoxBarcode2DOperations::geometryFailure);
        double determinant = matrix.getA() * matrix.getD() - matrix.getB() * matrix.getC();
        if (!Double.isFinite(determinant) || Math.abs(determinant) < 1e-12) { throw geometryFailure(); }
    }
    private static boolean positive(double value) { return Double.isFinite(value) && value > 0 && value <= 1000000; }

    private COSObject form(byte[] bytes, double width, double height) throws DocumentFailure {
        try {
            COSStream stream = document.getDocument().createCOSStream();
            try (OutputStream output = stream.createOutputStream(COSName.FLATE_DECODE)) {
                resources.writeBytesAsIOException(output, bytes);
            }
            stream.setItem(COSName.TYPE, COSName.XOBJECT);
            stream.setItem(COSName.SUBTYPE, COSName.FORM);
            stream.setInt(COSName.FORMTYPE, 1);
            COSArray box = new COSArray();
            for (double value : new double[] {0, 0, width, height}) { box.add(new COSFloat((float) value)); }
            stream.setItem(COSName.BBOX, box);
            stream.setItem(COSName.RESOURCES, new COSDictionary());
            return new COSObject(stream);
        } catch (IOException | RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw writeFailure();
        }
    }
    private static String digest(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder text = new StringBuilder(64);
            for (byte value : hash) { text.append(Character.forDigit((value & 255) >>> 4, 16)).append(Character.forDigit(value & 15, 16)); }
            return text.toString();
        } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    static DocumentFailure inputFailure() {
        return new DocumentFailure(DocumentFailureCode.BARCODE_INPUT_INVALID, CAPABILITY_ID, "The barcode input is invalid for the selected symbology.");
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
    static DocumentFailure geometryFailure() {
        return new DocumentFailure(DocumentFailureCode.BARCODE_GEOMETRY_INVALID, CAPABILITY_ID, "The barcode dimensions or placement are invalid.");
    }
    static DocumentFailure modeFailure() {
        return new DocumentFailure(DocumentFailureCode.BARCODE_MODE_INVALID, CAPABILITY_ID, "The barcode options are invalid for the selected mode.");
    }
    static DocumentFailure limitFailure() {
        return new DocumentFailure(DocumentFailureCode.BARCODE_LIMIT_EXCEEDED, CAPABILITY_ID, "The barcode operation limit was exceeded.");
    }
    private static DocumentFailure writeFailure() {
        return new DocumentFailure(DocumentFailureCode.DOCUMENT_WRITE_FAILED, CAPABILITY_ID, "The barcode could not be applied safely.");
    }
}
