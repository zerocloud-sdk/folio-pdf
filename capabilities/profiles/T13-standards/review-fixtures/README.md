# Independent CMap review regression fixtures

These ten original project-owned PDFs were authored by the independent
Spec and Standards reviewers for T75 from the frozen `embedded-font-kinds.pdf` source. Their purpose
is to retain independently discovered failures of the acceptance checker. They
are Apache-2.0 project fixtures, separate from the authoring-command corpus.
`sha256.json` records the exact original review probe bytes. The original
observations and review closure are retained with T75 delivery evidence.

References: ISO 32000-1:2008 sections 7.3.10, 9.7.5.4 and 9.10.3;
Adobe CMap and CIDFont Files Specification 5014 sections 5.1 and 7.3;
PostScript Language Reference, third edition, section 3.2.
No iText implementation or resources were used.

Fourteen additional binary-font review PDFs are bound by `font-sha256.json`.
Their original review paths distinguish the independent Spec and Standards
probes. The cases exercise compatible CFF minor versions, unknown charset
formats, reserved Top DICT operators, duplicate TrueType table tags, absent
CharStrings and a 4,097-entry CharStrings INDEX with a predefined charset.
They are original Apache-2.0 project data. Sources are Adobe CFF Technical Note
5176 sections 4, 6, 13 and 14, and OpenType Font File Organization. The first
glyph-count probe uses an independent minimal PDF rather than the 24-object
T13 source; its font stream must not be addressed as source object 22.

`ttf-conflicting-postscript-names.pdf` changes only the Windows name ID 6,
preserving the Macintosh name and recalculating table and whole-font checksums.
The independent mutator and original/closure CLI reports are retained in
`T75-delivery/standards-r7`. Apple's TrueType name table requires consistent
PostScript names; conflicting platform names prevent qualification of a unique
embedded program name. This is original Apache-2.0 project data.

Five further probes cover an indirect FontBBox coordinate, missing CID FDSelect,
two exact decimal Top DICT matrices, and an FD-only CID matrix with an undersized
PDF bound. The original independent authoring scripts, identities and CLI
observations are in `T75-delivery/standards-r8`. Sources are ISO 32000-1
§§7.3.10, 7.9.5 and 9.8.1, Adobe CFF 5176 §18, and PLRM third edition §5.11.3.
Explicit CFF matrices now remain unqualified; these probes do not assert support
for their matrix semantics. All original required corpus matrices are implicit.
