# Use a Java 8 foundation stack

The Foundation Release will use PDFBox 3.0.8 as its default low-level backend, ICU4J 77.1 for Unicode processing, OkapiBarcode 0.5.6 for barcode encoding, and TwelveMonkeys only where additional image decoding is needed. These dependencies remain implementation details; high-level layout, pagination, font fallback, shaping orchestration, and PDF drawing stay owned by project modules, with HarfBuzz available through an optional native adapter. ICU4J is pinned to 77.1 because 78.x requires Java 11.
