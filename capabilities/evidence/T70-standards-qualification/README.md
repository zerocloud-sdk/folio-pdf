# T70 standards tool qualification

These are original observations from 2026-09-08 on Ubuntu 24.04, Linux
6.8.0-136-generic x86-64. They qualify the rule assignments in the
[T03 standards profile](../../profiles/T03-standards/README.md), not a Release
Train candidate or any JDK/execution-profile tuple. Final certification must
record fresh invocations on the actual product in every required environment.

The corpus has one valid three-object PDF and 22 known-invalid controls. It is
project-authored Apache-2.0 data. The reports retain the paths used during the
qualification experiment, before copying those exact fixture bytes into the
repository. `results.json` indexes all 46 invocations; the adjacent text files
retain each tool's original combined output. Each invocation had an external
20-second observation bound during qualification.

The exact invocations were:

```text
pdfcpu validate --mode strict --conf disable --offline INPUT.pdf
TestGrammar --tsvdir PINNED_ARLINGTON_MODEL --pdf INPUT.pdf --force 1.7 --no-color
```

pdfcpu 0.15.0 detects 19 of the 22 controls. It accepts missing Resources and
missing or incorrect root Pages Type, so it is not qualified for those three
rules. Arlington 0.81 detects those three. Arlington can return exit zero while
reporting Errors, and its PDFium parser can repair Count problems, so an exit
code is insufficient and it is not assigned the Count/parent-link rules.
Arlington warnings without a definite Error remain INDETERMINATE. Positive
qualification had no Arlington Error or Warning.

The [tool pins](../../../scripts/pdfcpu-pin.properties) and
[Arlington pin](../../../scripts/arlington-pin.properties) contain executable,
version, distribution/source and model hashes. The model hash is SHA-256 over
sorted UTF-8 `FILENAME SHA256\n` lines for all immediate `.tsv` files. It is
recomputed from actual model files, not trusted from an installation marker.
Arlington was built from the pinned source using CMake 3.28.3, GCC
13.3.0 (Ubuntu 13.3.0-6ubuntu2~24.04.1), `PDFSDK_PDFIUM=ON`, `Release`, and
four build jobs. The build fixes `__DATE__` / `__TIME__` to
`Sep 8 2026 00:00:00` and maps the source path to `/arlington`. All 46
observations were rerun after this canonical build; no prior observation was
relabelled with the new executable hash.

All tools are acceptance-only local installations and are unbundled. See the
[installation and provenance record](../../../docs/third-party/t03-standards-tools.md).
