# T71 standards-tool qualification observations

These 62 observed invocations qualify the new T09 rule controls and retain known
checker gaps. They do not certify a delivery candidate or promote a capability.
[observations.json](observations.json) binds every actual command, executable,
input PDF, process exit and untouched raw output file by SHA-256.

The inputs are 29 project-authored illegal PDFs, one valid null DecodeParms
fixture, and the canonical nine-value Source. Each is checked by both pinned
pdfcpu 0.15.0 strict/offline and Arlington TestGrammar 0.81 with its pinned model.
The [frozen 51-rule profile](../../profiles/T09-standards/README.md) explains the
selection of each checker and the narrower meaning of a qualified finding.
The remaining 22 rules reuse T03's authored controls and are executed again by
the actual recorder for every final T09 product.

No output here is reference-suite output. No fixture or implementation was
copied from iText. Standards tools, models and fixtures remain outside runtime
artifacts. Candidate-specific observations must still bind all required inputs,
products, contracts, execution configurations and actual environments.
