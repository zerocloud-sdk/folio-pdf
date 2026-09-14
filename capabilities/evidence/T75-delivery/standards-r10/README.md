# Content and Type3 program qualification

The separate content scope qualifies PDF lexical objects, operator operands,
text/graphics/marked state, active named resources, selected font state,
marked properties and original Type3 rectangle bodies. Its final pre-structure
observer is `observer.py` (SHA-256
`e8e63757169a2ea91edd660a3fb867ead726c6605c26f203b5acb4484d5abec5`).
The prior observer remains in `observer-before-review.py`.

The 55 negative controls and all five original Sources have actual independent
CLI observations: every Source passed and every control failed its intended
predicate. Both the earlier 49-control catalog/54 observations and initial
six-operand catalog/11 observations remain intact. All 91 public Python
program, authoring, identity, semantic and Foundation tests passed in 186.124
seconds with no skips. This is development qualification, not certification
of the final candidate or an environment.

Independent review found byte-identity errors across qpdf JSON name encodings,
PDF braces treated as PostScript delimiters, crossing q/Q and marked pairs,
and incorrect Form named-resource fallback. Every behavioral correction has
actual RED before GREEN. Unicode and binary qpdf names now recover their
original bytes; different bytes cannot select the same resource. In PDF 2.0,
directly named Form resources must be local. Earlier-version Form resource
inheritance remains INDETERMINATE, while inheriting the selected font without
a new named lookup remains legal. Effective version observes both header and
Catalog Version. The shared numeric predicate preserves exact Type3 rational
bounds and excludes booleans. General glyph graphics, multi-contour winding,
and the previously documented traversal/token bounds remain unqualified.

Standards closure independently passed 28 CLI probes, including the original
six name false failures and nine distinct-byte negative controls. It found no
remaining hard issue or actionable smell. Spec independently closed all four
findings with 55 actual CLI observations, including every original probe and
all Sources. Neither closure is the final clean review required by the goal.
Unified pair nesting is a PDF 2.0 publication qualification rule; rejecting a
legacy PDF 1.7 crossing under this scope does not establish its invalidity
under the older edition. Unescaped braces in resource dictionaries trigger
qpdf warnings and remain INDETERMINATE; escaped keys with raw braces in
content names pass independently.
The original Spec report's reversed d1 rectangle corner probes were explicitly
withdrawn in its assessment; any diagonal pair defines the rectangle. They
have not been reclassified as defects.

`archive-identities.json` binds every archive and member. All archive files
were read back and byte-compared with their original temporary directories.
These archives preserve project-owned PDF inputs, mutators, CLI commands,
raw findings and observer/tool identities. Their original temporary paths
remain contextual evidence and are not dependencies of checked-in controls.

Failed fixture attempts remain visible. The first name test's Properties
dictionary missed closing delimiters; r2 corrected that fixture before the
actual nine behavior failures and GREEN. The first two brace test attempts
were blocked by qpdf resource-dictionary warnings, so neither is a behavioral
RED/GREEN receipt. The corrected r3 uses legal content-stream names and
demonstrates two actual lexer false failures before the fix. Earlier failed
operand and named-property padding attempts are likewise retained; the
passing full suite includes the corrected original fixtures.

Complete structure qualification, combined recording and collection,
authority promotion, final candidate certification and previous-obligation
refresh, full gates, final clean Standards/Spec reviews and authorized
commit/push/issue closure remain pending. No completion checkbox is marked.
