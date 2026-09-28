# Original T14 image corpus

These are acceptance-only assets authored for issue #76 under the project's
Apache-2.0 license. `scripts/generate-t14-corpus.py` writes the five Sources,
literal complete inventory expectations, original 144-DPI pixel grids and the
changed-sample negative control. It imports no product, parser or renderer.
`corpus.json` is the machine-readable profile; `corpus.properties` is the same
literal expectation serialized for the public Java producer.

The [certification contract](../../../docs/t14-certification.md) defines the
required chains per product. `classifications.pdf` intentionally includes
nonconforming/unsupported metadata and requires syntax/semantic observations,
not a standards pass or visual interpretation.

The embedded TrueType font is the original project-owned
`T13-fonts/FolioT13Rectangle.ttf`; the Type3 rectangle is authored here. They
supply font metadata, not a text layout or shaping oracle. The unchanged ICC
profile has its own [origin and license](color/README.md).

## Encoded payload origins

`scripts/generate-t14-codecs.py` reproduces `codecs/payloads.json` and every
listed payload. The constant inputs are an 8×8 grayscale-128 PGM and 16×16
black/white PGMs. Pinned ImageMagick 7.1.2-30, invoked through the repository
wrapper, encodes the JPEG and JPX at quality 100 and writes Group 4 TIFFs.
An original small TIFF-directory reader selects the sole encoded strip; no
third-party image, PDF fixture or backend output was copied.

JBIG2 is an original embedded sequential stream assembled from public T.88
segment syntax: page-information segment 0, immediate lossless generic MMR
region segment 1 and end-of-page segment 2. All target page 1. The width and
height are 16, positions/resolutions are zero, and generic-region MMR is enabled.
The white TIFF MMR run gives black JBIG2 samples with the declared polarity.
LZW is authored directly from the clear code, 64 literal `128` samples and EOD,
all nine bits wide with zero end padding. No width transition occurs.

ImageMagick is a validation-only tool under its existing repository license
treatment. Its independently encoded bytes are original asset content; no
ImageMagick code is redistributed. Payload SHA-256 values are original inputs
to extraction certification. Expected extraction never comes from Folio output.
