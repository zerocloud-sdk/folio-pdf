# T70 comparator dependency qualification

`missing-runtime.txt` records `ldd` in the actual pinned JDK 8 Ubuntu image before
the private dependencies were provisioned. The first candidate attempt at
`../foundation/T70-20260908-candidate-01/` retained visual INDETERMINATE and was not
published to the Foundation evidence authority.

`four-image-versions.txt` records the exact commands, exits and successful pinned
ImageMagick version output from all four immutable Ubuntu/JDK images after
provisioning. `packages.txt` and `notices/` preserve the actual Ubuntu package
versions and original copyright notices. The unmodified shared libraries remain
only in ignored acceptance cache storage. Their pins and provisioning commands
are documented in `docs/third-party/t03-standards-tools.md`.

`pipeline-observations/` retains the focused JDK 8 runtime rerun: both products
passed all four chains and all four negative-control categories were detected.
It used the existing compiled Java harness to test the dependency repair; it is
not a new source-bound candidate certificate. A fresh build and all eight final
tuples remain required.

These observations qualify the checker runtime. The current candidate's visual
findings, original golden comparison and actual negative controls remain separate
and are referenced by `capabilities/foundation-evidence.yaml` only after passing.
