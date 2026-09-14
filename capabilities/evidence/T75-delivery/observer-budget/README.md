# T75 existing observation-budget revalidation

The first final `./mvnw -B -ntp verify` ran with the explicit pinned HarfBuzz
helper and failed after 15:14 minutes. Nine existing consumer suites had four
assertion failures and 236 extraction-limit errors. Their observation helpers
still allowed only 64, 512, 4096 or 10000 font-data entries. The original full
log, Surefire reports and source identities are retained in the first archive.
The four shaping assertion failures occurred when a successful composition
was followed by an extraction failure inside the same test's failure handler;
the original shaping boundary assertions remain intact.

T75's approved accounting requires 65,538 entries for each selected Identity-H
font construction: its node, codespace declaration and 65,536 mapped codes,
plus other font costs. The existing observation allowances now add those
mapped-code domains while retaining their original remaining allowance.
The change affects extraction observation budgets in existing tests and
acceptance tools. Product extraction enforcement, FontLimits,
CompositionLimits, expected text/geometry, failure assertions and the fourteen
other extraction dimensions remain unchanged.

The public diagnostic probe reuses the original project-produced T19 PDF.
Both actual Native modes reject the old 64-entry budget and return the expected
detached `AΩBAB` after changing only the font-data allowance to
`2 * 65536 + 64`. Source bytes remain intact in all six cases. Separate zero
ToUnicode-mapping and decoded-byte controls still reject extraction. The log
named `unicode-limit-control.log` sets `maximumToUnicodeMappings`, not the
Unicode-output limit. Probe source, PDF, commands and original outputs are
retained; generated class bytes are excluded with their identities recorded.
The original receipt's unqualified `current-native-source-sha256` field hashes
`PdfBoxCMapPreflight.java`. The separate
[identity clarification](public-probe-source-identity-clarification.json) names
that path and the independently retained main extractor identity. Neither
field is a complete built-candidate identity; the original receipt is unchanged.

An independent read-only Spec assessment checked actual font-resource reuse.
The font and layout observations need two domains, including retained/new
subsets across an incremental publication. Shaping acceptance uses eight:
four commands each publish Sans and a script font, with command-local shaping
caches. Barcode labels share one Session font across all 115 labelled pages.
T28 acceptance already allows 500000 entries for its six-font document and
was left unchanged. The inspection report and 43 source identities are retained.

The FontLoading observer's minimal adjustment passed all 22 existing tests.
Seven existing acceptance checks then supplied a separate actual RED: all
failed before their allowance changes. After the changes, all seven passed
with no skips. T24–T27 and T29 retained their changed-PDF negative controls;
the two selected T30 methods covered Native products in both modes and the
independent reference positive. This does not claim a separate rerun of T30's
negative-control method. The minimal passing sources are frozen separately;
later source changes add explanatory comments only.

`closure-review-input.tar.xz` holds the sixteen-file final increment and original
logs. The complete affected consumer run passed all 394 cases with zero failures,
errors or skips in 12:10 minutes. Its original log is retained separately.
Independent Standards and Spec closures both confirmed the sixteen source
identities and found no remaining actionable issues. Their original reports,
source snapshots and complete logs are retained in the final closure archives.
Full Maven/JDK validation, actual candidate certification and delivery remain
required. These retained RED/GREEN observations do not provide environment
certification or global Foundation readiness.

Every archive member was compared byte-for-byte with its original. The archive
and standalone log identity files retain original paths and SHA-256 digests.
