# Font widths and exact numeric qualification

The public program/authoring/Foundation suites pass 75 tests in 129.167 seconds
with no skips. Nine original metric controls independently change simple
Widths or CID array/range/default widths. Before review, all five Sources
passed and all nine controls failed the intended font-widths predicate.
Those exact observations and their earlier observer are preserved separately.

Both independent review axes found TrueType fractional widths falsely rejected
after float scaling. Integer ratios now remain exact through both width and
polygon-bound calculations. The exact 402.4/200.4 widths and 320.8 FontBBox pass;
adjacent incorrect declarations fail. The shared descriptor binding was
extracted during review. Subsequent independent CFF probes showed that DICT
real widths had already lost source precision in fontTools. Such widths now
remain INDETERMINATE in font-metrics, while the separate fonts scope is
unaffected. Curved TrueType glyphs remain outside exact polygon-bounds
qualification after their coordinate/header bounds are checked. All required
original corpus fonts satisfy these explicit acceptance boundaries.

Both axes independently closed their findings. Their exact original and
closure PDFs, commands, raw reports and tool identities are retained in eight
archives; every member was read back and byte-compared with its original.
archive-identities.json records archive and member identities. observer.py is
the exact source at closure; observer-before-width-review.py preserves the
initial numeric defects. All retained PDFs and mutators are project-owned
Apache-2.0 acceptance material, not product runtime resources.

Every new behavior has actual RED before GREEN. The first CID DW RED attempt
used incorrect padding; the corrected r2 and r3 logs establish actual false
PASS failures before implementation. The full-suite GREEN includes the final
curve-boundary change. Historical results retain their original interpretation.

This is incremental development qualification. Content/structure qualification,
complete recording and collection, authority promotion, final environment
certification and prior-obligation refresh, full validation and final clean
reviews remain outstanding. No completion checkbox is marked.
