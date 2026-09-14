# Hierarchy, ParentTree and namespace qualification

This increment qualifies independent hierarchy, ParentTree and namespace
predicates through the public Python observer CLI. It is not final text
certification, final clean review, or a completion receipt for #75. No task
completion checkbox has been marked.

The fixed overall review baseline remains
`5b1603c435f11c40368f75b7b9a2777c9c5e9761`. The first reviewed hierarchy and
ParentTree snapshot is `observer-hierarchy-parent-tree.py`, SHA256
`d7225e23efc634fe5b71edb1713ba6e4d24e91d29e33cc5e9ca959af8938bf81`.

The independent Standards review performed 25 actual CLI observations and
reported stream objects accepted as dictionary positions and missing indirect
parent identity. Spec review performed 64 actual observations and also found
empty element K arrays, optional NextKey type skipped without a ParentTree,
and PDF 2.0 direct roots. The original reviews remain intact in
`standards-original.tar.xz` (232 files) and `spec-original.tar.xz` (585 files).
`original-review-archives.json` binds every member and archive identity;
`retain-original-reviews.py` read each member back and compared exact bytes
with its original before retaining the manifest.

Each correction has public RED and GREEN logs, without weakening the tested
outcomes. The combined 10-test structure run passed in 27.617 seconds after
the first four correction slices. A subsequent 10000/10001 root RoleMap
boundary exposed one additional false PASS and was corrected; 1000/1001
namespace dictionary controls also passed. These are bounded qualification
limits, not claims about universal PDF validity. The reviewed next snapshot
is `observer-hierarchy-parent-tree-namespaces.py`, SHA256
`8a523b6c1e7545e09e826b8fae4dedf4624d6477a8d8c3961a5f8f079b900a8b`.
Two independent incremental closure reviews of that exact snapshot completed:
Standards performed 53 actual CLI observations and Spec performed 116, with
no remaining finding in this increment. `standards-closure.tar.xz` retains
487 files and `spec-closure.tar.xz` retains 1055 files; every archive member
was read back and byte-compared with its original. The closure manifests and
retention recipe are checked in here. No closure conclusion is implied for
later code; content-reference qualification continues in `standards-r12`.

Optional dictionary K null remains equivalent to omission. Root empty K arrays
are legal; present element K arrays need at least one member. Actual null array
children fail. `null-interpretation.md`, the independent assessment, raw qpdf
null probe and initial malformed boundary fixtures preserve the earlier
interpretation and fixture corrections. Initial failing fixture construction
attempts are not counted as behavioral RED evidence.

Actual MCID definitions, content ownership and matching StructParent(s)
relationships are still being implemented and qualified. Complete Tagged PDF
role resolution is outside the namespace declaration scope. Product extraction,
standards qualification and full Foundation readiness are distinct claims.
