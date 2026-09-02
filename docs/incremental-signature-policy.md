# Incremental publication and Existing Signature policy

This guide is the authoritative English contract for T15 version 1. It defines how the Document Workflow interprets `SaveMode.INCREMENTAL`, recognizes Existing Signatures, authorizes Document Commands, and fails safely. It does not create signatures or establish cryptographic validity.

## Publication contract

`INCREMENTAL` requires a declared existing primary Source. A create request, or any other request without such a Source, fails before caller work and publication. A successful incremental publication writes the complete Source bytes unchanged as the output prefix and appends a non-empty revision. The staged result is reopened before any Target is attempted, then follows the same declaration-ordered Path and stream publication and Publication Receipt rules as `REWRITE`.

The Source may be a Path, stream, channel, or bounded byte source. Folio PDF continues to own only resources it opens: caller streams and channels remain open. A split product is a new independent PDF rather than a revision of the primary Source, so `SplitDocument` is rejected in `INCREMENTAL` mode.

## Unsigned command matrix

For a Source with no Existing Signature, version 1 classifies every current Document Command family as follows.

| Command family | Incremental status | Reason |
| --- | --- | --- |
| `AddBlankPage` | supported | Updates the primary document and can be represented in an appended revision. |
| `InsertBlankPage`, `RemovePages`, `MovePages`, `CopyPages`, `MergeDocuments` | supported | Page-tree changes remain changes to the primary document; imported objects are appended. |
| `SplitDocument` | rejected | Its Targets are independent products and cannot retain the primary Source as their revision prefix. |
| `UpdateDocumentInfo`, `SetXmpMetadata`, `ReplaceOutlineTree`, `SetNamedDestinations`, `EmbedFile` | supported | The changed metadata objects and new dependent objects are appended. |
| `UpdateAnnotations`, `UpdateActions`, `FlattenAnnotations` | supported | The changed annotation, Action, page, and appearance objects are appended. |
| `DocumentPatch` | supported | Validated low-level changes use the same backend-neutral patch and preservation rules before their changed objects are appended. |
| Any caller-defined or future unclassified command | rejected | The interface is not an extension point and version 1 never infers incremental safety. |

An incrementally rejected command fails before staging or publication with `INCREMENTAL_COMMAND_REJECTED` and T15's capability identity. Its diagnostic is fixed and contains no command payload or document data.

## Existing Signature recognition

Folio PDF recognizes populated signature fields and the standard permission signatures reachable through the document catalogue. A populated signature field has inherited effective field type `/Sig` and an inherited `/V` signature dictionary. A structurally valid signature dictionary has a direct string `/Contents` and a direct, even, ordered, non-negative `/ByteRange` of direct integer offset/length pairs contained in the available Source revision. When `ByteRange` is present, every signature-dictionary value and every nested DocMDP value used by the policy must also have the required direct representation; unexpected indirection is invalid rather than a permission grant. A range beginning at byte zero can participate in the supported permission proof; a structurally valid nonzero first offset remains protected but grants no mutation. `/Type` is optional, but when present it must be `/Sig`; Folio PDF never relies on that optional entry or on `SigFlags` as its detector. Empty signature fields are not Existing Signatures.

A certification policy is proven only when the catalogue permissions dictionary's indirect `/DocMDP` entry and a populated signature field select the same signature dictionary, and that dictionary contains exactly one coherent `/DocMDP` signature reference with a transform-parameters dictionary. The optional DocMDP transform-parameter `/P` and `/V` entries use the ISO 32000-1 defaults of `2` and `/1.2`; explicit values must be supported. An absent or wrongly typed transform-parameters dictionary grants no permission. At most one DocMDP certification signature is accepted.

Approval signatures without a proven DocMDP policy, usage-rights signatures, FieldMDP restrictions, unknown permission handlers, and unknown transform methods are still protected Existing Signatures but grant no Signature Permission. Every populated signature and permission handler is considered; permissions combine by intersection, never by choosing the least restrictive signature.

Malformed types, broken or repeated field trees, cycles, contradictory catalogue and signature references, multiple DocMDP certification signatures, invalid byte ranges, and unsupported DocMDP values fail before caller mutation or publication with `SIGNATURE_STRUCTURE_INVALID`. Structurally well-formed but unsupported transforms, permission handlers, and nonzero-first-offset byte ranges remain protected and grant no mutation. The diagnostic does not reveal field names, signature contents, metadata, paths, credentials, or backend exceptions.

Recognition is bounded to the parsed COS graph in T15. Policy inspection admits at most 4,096 queued field nodes at depth 64, 16 catalogue permission entries, 64 entries per signature dictionary, 256 `ByteRange` entries, 64 signature references, and one indirect-reference resolution at a time; nested indirect references are invalid. A larger structure fails closed before the policy allocates or traverses beyond that boundary. PDFBox does not retain whether a parsed string used hexadecimal or literal delimiters and normalizes duplicate dictionary keys and some repaired syntax; T15 therefore does not claim comprehensive raw-token detection for those cases. They remain within the T20 hostile-input boundary. Observable wrong types, ranges, graph cycles, parent contradictions, permission contradictions, and local policy-limit exhaustion are rejected fail-closed.

Recognition and structural validation are protection mechanisms, not cryptographic signature validation. Folio PDF does not verify a digest, certificate, signer identity, trust chain, revocation state, or timestamp in T15. Those remain Trust work beginning with T38.

## Signed Source policy

A structurally recognized signed Source remains available to a target-free, read-only Query workflow. Every command in a target-free signed `REWRITE` workflow is rejected at the command boundary with `SIGNATURE_POLICY_REJECTED`; P=3 authority applies only to explicit `INCREMENTAL` workflows. A signed Source with any publication Target cannot use `REWRITE`, even when caller work issues no command; it fails before caller work with `SIGNED_REWRITE_REJECTED`.

For signed `INCREMENTAL` workflows, version 1 applies this matrix.

| Applicable restriction | Current command authority |
| --- | --- |
| Ordinary approval signature without proven DocMDP permission | no command is permitted |
| DocMDP P=1 | no command is permitted |
| DocMDP P=2, including the defaulted value | no current command is permitted; Forms, template instantiation, and signing are downstream work |
| Sole, coherent DocMDP P=3 certification signature | `UpdateAnnotations` may create, replace, move, or remove supported non-Widget annotations; every other command is rejected |
| Multiple signatures or permission handlers | only the intersection is permitted; any ordinary, unsupported, P=1, or P=2 restriction therefore denies every current command |

P=3 does not authorize `FlattenAnnotations`, because flattening changes page content in addition to deleting an annotation. It does not authorize `UpdateActions`, because that command can change catalogue and page Actions. Widget changes are excluded so T15 does not implement Forms or signature-field mutation.

A signature-policy refusal uses `SIGNATURE_POLICY_REJECTED`, the T15 capability identity, and the fixed diagnostic `The Existing Signature policy does not permit this workflow.` It occurs before the rejected command changes the live document. A signed incremental workflow that lacks both proven permission and at least one admitted mutation also fails before staging; a no-command signed publication is not used to manufacture an empty revision. Every such pre-publication failure leaves Path Targets unchanged and reports every declared Target as `NOT_ATTEMPTED`.

## Standards boundary

The policy is derived from ISO 32000-1:2008 clauses 7.5.6 (incremental updates), 12.8.1 (signature types and byte ranges), 12.8.2.2 and Table 254 (DocMDP), and 12.8.4 and Table 258 (permissions). Clause 7.5.6 requires an incremental update to append changes while leaving original contents intact. Table 254 defines P=1, P=2, and P=3 and their defaults. The implementation also relies on the public Apache PDFBox 3.0.8 `PDDocument.saveIncremental(OutputStream)` contract and update tracking, while keeping all PDFBox types out of public and protected signatures.

The detailed primary-source citations and clean-room research notes are recorded in the T15 research note and `PROVENANCE.md`. Project-owned structural signature fixtures contain no third-party signed bytes and are not evidence of cryptographic validity.
