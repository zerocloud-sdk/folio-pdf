# Extraction syntax and qpdf identity qualification

The public syntax command observes exact copied inputs using pinned qpdf.
Actual RED established the absent command before implementation. All five
original Sources then passed and an actually truncated PDF failed. A second
RED showed a substitute wrapper could falsely pass by printing the correct
version; before/after wrapper, binary and pin checks now prevent that result.
Both initial focused tests passed with no skips. The exact pre-review Java
sources and their original RED/GREEN logs remain unchanged here.

Independent review then found pin assignment redirection, shared-library
substitution, report disagreement after input/tool identity changes, and cache
selection differences for empty values, relative paths and wrapper links.
The three qpdf observers now qualify the whole pin, the pin actually sourced
by the wrapper, original wrapper/binary bytes and eleven runtime library paths
before and after observation. Cache selection follows the wrapper's default
and working-directory semantics. The semantic observer preserves the wrapper
invocation path. Final syntax reports consistently include post-observation
identity failures while retaining the original qpdf stdout/stderr and result
in separate qpdf-syntax.md and qpdf-syntax.txt files.

Both review axes independently closed all findings. Nine archives retain exact
PDFs, commands, probes, reports and identities. Every archived member was read
back and byte-compared; review-archive-identities.json binds every member.
External ELF binaries are not redistributed in those archives. Their exact
SHA-256, original official ZIP member and byte-offset edits are retained in
that manifest; every such reconstruction was byte-verified. Original symlink
targets are recorded separately instead of installing absolute cache links
when an archive is extracted. The original task directories remain intact.
qpdf-runtime-origin.json binds every runtime path to the already pinned
official qpdf 12.4.0 archive. No tool version or product dependency changed.

The final focused syntax suite passes six tests with no skips. The public
Python identity/semantic/content suite passes seven tests with no skips.
qualified-qpdf-snapshot preserves the exact four reviewed sources. Earlier
82-test Python and 19-test Maven qualification logs retain their earlier
identities; they are not final-candidate certification.
After the final identity closure, the complete Maven acceptance/qualification
suite passes 20 tests in 2:11 with no failures, errors or skips; its r2 log is
retained here, including all 334 declaration predicates and public products.

The initial Python runtime negative used an ELF trailer byte that was still
loaded and failed to preserve the intended positive observation. Its first
RED and attempted GREEN logs are retained as failed fixture attempts. That
runtime implementation was reverted, the control was corrected to GNU build-id
metadata, and r2 records actual false-PASS RED before the minimal fix and GREEN.
The first Java directory-link test expected INDETERMINATE although genuine
qpdf correctly returned FAIL; r2 corrects that expectation and separately
reproduces actual loaded-pin redirection before its fix. These histories have
not been relabeled as successful RED/GREEN cycles.

This syntax-only command is not the combined Foundation recorder, a final
certification record or a completion gate.
