# T71 independent review correction

These are development observations after independent whole-diff Spec review,
not current-candidate certification. The review found that Page Contents with a
scalar or non-stream array member and a Page with no effective Resources could
be committed. The original probe products and the interrupted candidate receipt
remain in [the first review attempt](../T71-review-attempt-1/).

| Observation | Retained result |
| --- | --- |
| [Native RED](native-core-structure-red.txt) | Four public assertions fail: Contents and effective Resources, in both Native execution modes. Zero test errors or skips. |
| [Facade RED](facade-core-structure-red.txt) | The public call returns without the required Native failure. |
| [Minimal GREEN](core-structure-green.txt) | Four Native cases and one case in each Facade edition pass. |
| [Refactor GREEN](core-structure-refactor-green.txt) | Native 100, Stable Facade 36 and Preview Facade 36 pass; zero failures, errors or skips. |
| [Plan count RED](plan-83-tests-red.txt) | The public plan still required 82 instead of the expanded 83 cases. |
| [Plan count GREEN](plan-83-tests-green.txt) | All 17 repository runner tests pass with the updated complete suite. |

The production correction validates Page Contents types and inherited effective
Resources inside the existing Patch rollback boundary. Public tests verify
immediate safe rejection, rollback of preceding operations, retained stream data
and references, valid stream arrays and Resources inheritance, successful later
changes, publication receipts, Source preservation and both Native save modes.
The final required tuple contains 50 Native cases, 31 Facade cases and two
actual-jar cases.

Two earlier Native runs exposed assumptions in the test about direct versus
indirect stream/resource inspection, before the intended defect was reached.
They are retained separately in ignored development logs and are not RED
evidence. No production correction preceded the four actual failing assertions
and the independent Facade failure retained above. Final eight-scope values and
transactions certification and final independent signoff are separate gates.
