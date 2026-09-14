# Initial font-program qualification and incremental review closure

The public program/authoring suite passed 48 tests in 31.867 seconds, with no
skips. This increment adds CFF/TrueType header and readable-data checks plus
CFF/CID-CFF declared-kind agreement. The existing pinned fontTools 4.59.2 wheel
is an acceptance reader; the product runtime has no new dependency.

Independent Standards review identified optimized-Python checksum bypass,
missing CharStrings without a result record and a glyph-count bound bypass.
Independent Spec review identified a false failure on compatible CFF minor
versions, unimplemented charset exceptions without records, duplicate TrueType
tags hiding an unchecked checksum and silently ignored reserved DICT operators.
Each was repaired through a real public CLI RED then GREEN. The additional
charset/INDEX count check has independent 229-glyph PASS and 230-glyph FAIL
observations. The first charset-count test addressed the wrong object in the
reviewer's minimal PDF; `r2-red` is the corrected actual false-PASS regression.
The invalid test attempt is retained, not counted as behavioral RED evidence.

Both independent axes closed their findings. The Spec closure additionally
checked both declared CFF kind substitutions. `observer.py` preserves the exact
checker source at closure. Four raw report archives retain original probes,
CLI outputs and identity records. `archive-identities.json` binds every archive
and file; all members were read back and byte-compared with their originals.

This is an incremental development qualification. Complete font table layout,
outline execution, font metrics and owner relations, content/structure checks,
the combined text recorder, final environment certification and final independent
review are still required. No completion criterion or certification is promoted.
