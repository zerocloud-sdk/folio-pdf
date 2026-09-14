# Separate simple and CID TrueType font programs

The former source shared a TrueType font containing `cmap` between a simple
TrueType font and CIDFontType2. ISO 32000-1:2008 §9.9 explicitly requires the
simple-font mapping table and excludes it from the CIDFont program. Independent
Spec review found the same wording indexed for Adobe's 2017 PDF 2.0 draft
§9.9.1, but could not directly read the final 2020 clause. The current errata
does not settle that question. This conservative source revision satisfies the
directly checked rule; it does not label the former PDF 2.0 source conclusively
nonconforming to an unread final clause.

`FolioT13RectangleCID.ttf` omits `cmap`, preserving GID 1, the original rectangle
and its 500-unit advance. The three prior font files remain byte-identical. The
CID program is 1,144 bytes with SHA-256
`bc846fd8f382e7bd59f2742e81fe33ce5905f7f365026abfce261cd12f548651`.
OS/2 character-range fields follow the removed mapping; its height metrics are
unchanged. The first font-authoring test incorrectly required the entire OS/2
table to be identical. Its failed attempt is retained; the corrected test
checks the unchanged metrics and all other relevant table bytes.

The corpus now embeds the CID program as object 25 and keeps the simple program
at object 23. All original extraction and raster expectations remain unchanged.
Fresh pinned PDFium rendering of the updated original reference PDF produced
the exact previous PNG bytes. The new direct source/reference hash is
`e016ccb3e411f70ee07b9e583ab9791d9664bbb1f0c8c5efa7874cb89cff2ada`;
the inherited source hash is
`d44fafaa0000b999ac41059e6d952bfc0c60ac2118d96f889bc247ff07fedcd8`.

`before-cid-program-split.tar.gz` retains all 369 previous corpus, standard-control
and visual-reference files. Every member was read back and byte-verified;
`archive-identities.json` binds them. `canonical-updates.json` records every old
and new file identity. The 334 declaration-rule assignments are unchanged;
their source/control identities are refreshed, together with the Java corpus
and Python semantic-observer pins.

The public authoring changes had actual RED before implementation. A standards
authoring comparison initially failed while the checked source was still the
prior corpus; the synchronized suite subsequently passed. Log names containing
`green` do not override their actual results. The combined Python suite passed
69 tests in 54.909 seconds with no skips. This remains development validation,
not final staged-candidate certification.

The refreshed actual Maven acceptance and independent declaration qualification
passed 14 tests with no failures, errors or skips in 2:08. Its retained log is
`t75-cid-split-acceptance-r1.log`. The revised Sources pass public Native/Facade
extraction, semantic/visual checks and the complete 334 declaration-rule union.
