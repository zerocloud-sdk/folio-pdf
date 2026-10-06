# Spec source review r5 — issue #81

Baseline and observed HEAD: `05e7f546f5885680e333ad8d3dea3645b25d26b7`.
Read-only pre-final-stage review against `/workspace/contracts/issue-81-contract.md`, including actual promotion, source fixes, pins and record links. This report does not certify the final candidate.

No remaining Spec source findings after the promotion metadata corrections:

- For “Mark document.hostile-input-limits compatible only after complete evidence and satisfied dependencies,” the promotion follows the accepted initial four limits scopes and 88 predecessors. `inventory-readiness-initial-complete.txt` retains their dependency evaluation. The Capability Matrix changes only T20 status/profile/platforms, its satisfied promotion gate and supporting evidence. Five T81 chain summaries explicitly label their linked records as initial qualification and defer current certification to the Foundation index; they retain four actual Ubuntu/JDK IN_PROCESS links each. Release-train metadata agrees with the catalogue. Duplicate Producer labels are removed and the former T06 gate is accurately described as satisfied.
- For “Collection validates actual retained artifacts and observations,” `scripts/t03-foundation.py:18` appends hashed raw-observer references; `:1327` preserves the original group after T20 adds both live groups at `scripts/t20_foundation_reports.py:244`. Existing path/hash checks remain before the narrowly scoped raw-payload traversal exception. `scripts/tests/test_t20_foundation.py:242` rejects changed/missing payloads and wrong reference roles.
- JSON-first parsing at `scripts/t03-foundation.py:850` preserves hash, traversal, identity, chain and producer checks with the original YAML fallback. `test_t20_foundation.py:209` exercises successful retention and missing-contract/changed-producer rejection in both formats. The retained focused log reports ten passing tests.
- The initial 2,853-input source archive hash matches its receipt. Only the driver, T20 collector, T20 pin file and focused collector tests differ among those archived inputs. Exactly two pin entries changed; all 79 current pin hashes match. The accepted 75-case Java coverage is unchanged. Baseline-relative product/runtime/POM diffs remain empty.
- Native-only policy controls, actual existing Facade observations, the cooperative guarantee, excluded Worker/platform/downstream scope and existing encryption remain preserved.

No tests executed by this reviewer. Inventory validation r3 passed; check r3 still reports stale generated foundation-readiness documentation. That gate, final freeze/stage, complete ordered recertification, full validation and final delivery review remain pending.
