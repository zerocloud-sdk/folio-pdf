# Optional K null and null array members

The first two K-null FAIL expectations were incorrect as reliable negative
controls. Independent Spec review reconciled the general dictionary-null
omission rules in ISO 32000-1/2 sections 7.3.7 and 7.3.9 with optional K in
Tables 354/355. The approved correction arose from PDF Association issue 308,
which concerns array members; it does not explicitly override dictionary
null equivalence. The exact review and sources are retained beside this note.

Direct optional K null is now retained as a positive absence case; root and
element K [null] remain illegal child controls and fail. Required dictionary
fields with null values still fail as missing required fields. PASS here
qualifies the hierarchy predicates only. No raw-byte parser or new dependency
was introduced to override the standard dictionary interpretation.

The original 19-failure missing-scope RED and failed first GREEN attempt remain
unaltered; the corrected 21-observation hierarchy test passes in r2.
