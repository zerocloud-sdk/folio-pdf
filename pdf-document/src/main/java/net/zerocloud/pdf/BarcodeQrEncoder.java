package net.zerocloud.pdf;

import uk.org.okapibarcode.backend.QrCode;
import net.zerocloud.pdf.composition.Barcode2D;

/** Pins exact ECC through Okapi's documented protected extension point. */
final class BarcodeQrEncoder extends QrCode {
    BarcodeQrEncoder(Barcode2D declaration) throws DocumentFailure {
        improveEccLevelIfPossible = false;
        preferredEccLevel = EccLevel.valueOf(declaration.getQrErrorCorrection().name());
        if (declaration.getQrVersion() != 0) { setPreferredVersion(declaration.getQrVersion()); }
        Barcode2DEncoding encoding = Barcode2DEncoding.of(declaration.getEncoding());
        content = declaration.getContent();
        inputData = encoding.encode(content);
        eciMode = encoding.eci;
        // Work with literal bytes: Symbol.setContent interprets backslash FNC markers.
        encode();
        plotSymbol();
    }
    @Override protected void eciProcess() { /* Explicit, strict bytes already supplied. */ }
}
