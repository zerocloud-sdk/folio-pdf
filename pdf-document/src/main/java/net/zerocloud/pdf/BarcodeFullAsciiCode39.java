package net.zerocloud.pdf;

import uk.org.okapibarcode.backend.Code3Of9Extended;

/** Full ASCII data bypasses the base Symbol's unrelated Code128 escape recognition. */
final class BarcodeFullAsciiCode39 extends Code3Of9Extended {
    @Override
    public void setContent(String data) {
        content = data;
        encode();
        plotSymbol();
    }
}
