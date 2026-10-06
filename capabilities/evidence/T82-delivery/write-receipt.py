"""Render the #82 receipt from audited observations and retained final gates."""
import json
from pathlib import Path
import sys

delivery = Path(__file__).resolve().parent
root = delivery.parents[2]
audit = json.loads((delivery / 'final-audit.json').read_text())
summary = json.loads((delivery / 'validation/full-ubuntu-verify-final-r1-reports/summary.json').read_text())
assert summary['totals']['failures'] == summary['totals']['errors'] == 0
review_complete = '--review-complete' in sys.argv[1:]
if review_complete:
    for axis in ('standards', 'spec'):
        assert (delivery / 'reviews' / (axis + '-final.md')).is_file()

def link(label, path):
    return '[' + label + '](../../../' + path + ')'

def local(label, path):
    return '[' + label + '](' + path + ')'

worker = '../foundation/T82-final/worker/'
coverage = local('frozen coverage', '../../profiles/T21-hardened-worker/coverage.json')
names = local('137 named cases', '../../profiles/T21-hardened-worker/mandatory-tests.txt')
chains = local('current Worker observations', worker)
readiness = local('live readiness', 'validation/inventory-readiness-final-r1.txt')
collector = local('116 collector tests', 'validation/collector-suite-final-r2.txt')
criteria = [
 ('C1', 'PASS', 'The 41 shared public cases execute in each actual mode on every JDK; all four original and fresh 137-case transcripts pass. Public records retain reopen, ownership, progress, safe failures and actual ordered receipts.', names + '; ' + chains),
 ('C2', 'PASS', 'Exact named inventory; zero failures, ignored tests, assumptions or duplicates in every original and live replay.', names + '; ' + local('transcript audit', 'final-audit.json')),
 ('C3', 'PASS', 'Actual WorkerProtocolBoundaryTest framed experiments authenticate, reject hostile selectors/serialization/lengths and excess, and preserve sentinel products.', names + '; ' + chains),
 ('C4', 'PASS', 'Shared public contracts and ordered-prefix/publication records prove declaration order, barriers, prefix visibility, first failure and unconsumed later inputs through both existing transports.', coverage + '; ' + chains),
 ('C5', 'PASS', 'Actual separate child, effective roots, 0700 roots/0600 nonempty staged files, denied escape/link access/cross-transaction/descendant/INET and applicable Unix operations; paired permitted controls. Policy JVM and absent Java Unix API on 8/11 are explicitly qualified.', chains + '; ' + link('qualification', 'docs/t21-certification.md')),
 ('C6', 'PASS', 'Actual Java/runtime/vendor/build, prlimit hash/version, argv, cleared environment, installed Security Manager, closed production classpath/inventories, CPU/FD/JVM ceilings and actual elapsed termination; mandatory tests prove modeled aggregate admission.', chains + '; ' + link('resource scope', 'docs/hardened-worker.md')),
 ('C7', 'PASS', 'Cancellation/deadline/elapsed/crash/malformed records retain safe codes, actual ordered receipt names/status/partial flags, intact prepublication Targets, child termination before cleanup and no remaining owned child/data.', coverage + '; ' + chains),
 ('C8', 'PASS', 'Shared named cases exercise cleanup failure, checked-primary precedence, caller exception identity, committed receipts, caller/module ownership and Session-view lifetime.', names + '; ' + chains),
 ('C9', 'PASS', 'Actual unsupported-OS, missing-Java and disabled-prlimit controls require WORKER_UNAVAILABLE, actual unattempted receipts, unchanged Targets and empty owned storage; real prerequisites documented.', chains + '; ' + link('prerequisites', 'docs/hardened-worker.md')),
 ('C10', 'PASS', 'Actual public Native HARDENED_WORKER and existing public Facade IN_PROCESS observations retain matching document behavior/reopen/ownership/receipts; Worker controls remain justified Native-only.', coverage + '; ' + link('Facade authority', 'capabilities/facade-surface.yaml')),
 ('C11', 'PASS', 'Exactly four Worker certifications, each with five separate passing qualified producer-bound chains, original raw findings and detected negatives. Independent qpdf/pdfcpu/Arlington/PDFium/ImageMagick profiles remain distinct.', chains + '; ' + local('chain audit', 'final-audit.json')),
 ('C12', 'PASS', 'Frozen source/contracts/staged artifacts/harness and all observed environment/configuration/tool/input/transitive records retain their actual identities; live comparison and adversarial guards reject alteration/relabeling.', local('freeze', 'source-freeze-r3.json') + '; ' + collector + '; ' + local('staged closure', 'validation/stage-final-r2-closure.json')),
 ('C13', 'PASS', '96 current certifications and 392 chain records; all thirteen selected/predecessor obligations SATISFIED; no global candidate/contract/evidence blocker. Remaining release blockers are unrelated.', readiness + '; ' + link('current index', 'capabilities/foundation-evidence.yaml')),
 ('C14', 'PASS', 'Only completed Worker capability/profile promoted to compatible; exactly the four actual Linux environments. Recovery/scale remains experimental; Windows/macOS remain uncertified.', link('Capability authority', 'capabilities/capability-matrix.yaml') + '; ' + link('Foundation authority', 'capabilities/foundation-release.yaml')),
 ('C15', 'PASS', 'Capability/Facade authorities, T21 and five-chain narratives, generated reports, English Worker/README, Chinese usage and provenance coordinated; exact 754/25 class inventories and six runtime dependencies documented.', link('Worker certification guide', 'docs/t21-certification.md') + '; ' + local('source ownership', 'changed-file-ownership.json')),
 ('C16', 'PASS', '116 meaningful collector tests pass; mandatory coverage/skip/duplicate, altered findings/controls/products, producer and candidate/environment/configuration guards; final actual CLI collection passes.', collector + '; ' + local('final CLI', 'validation/t21-cli-collect-final-r1.txt')),
 ('C17', 'PASS', 'Focused public/real-process Worker, runner, T20/T21 collector, actual environment-observer and inventory witness/control checks retained, including diagnosed failed attempts.', local('validation records', 'validation/') + '; ' + local('failed-attempt dispositions', 'failed-attempts.md')),
 ('C18', 'PASS', 'Final full Ubuntu verify and inventory validate/generate/check pass. Live readiness exit 1 is exclusively unrelated obligations. Conditional JDK build matrix not triggered: shipping source and POM/build compatibility unchanged. All four actual T21 JDK tuples pass independently.', local('gate audit', 'final-audit.json') + '; ' + readiness),
 ('C19', 'PASS' if review_complete else 'PENDING', 'Baseline-relative Standards and Spec reviews with resolved earlier applicable findings.' if review_complete else 'Final evidence/receipt reviews pending; reviewed frozen source has no unresolved applicable findings.', local('Standards', 'reviews/standards-final.md') + '; ' + local('Spec', 'reviews/spec-final.md') + '; ' + local('source dispositions', 'reviews/source-dispositions.md')),
 ('C20', 'PASS' if review_complete else 'PENDING', 'This receipt, exact changed-file ownership manifest, entry/final audit, identities, commands/results, reviews, original failed attempts and unrelated blockers form the delivery.' if review_complete else 'Receipt prepared for final review; closing ownership audit remains pending.', local('final audit', 'final-audit.json') + '; ' + local('ownership manifest', 'changed-file-ownership.tsv.gz')),
]
assert {row[0] for row in criteria} == {'C' + str(i) for i in range(1, 21)}
parts = [
 '# Issue #82 execution receipt\n',
 ('Completed local delivery for parent review.' if review_complete else 'Validated local delivery; final evidence review and closing ownership audit pending.') +
 ' Source: ' + local('authoritative execution contract', 'execution-contract.md') +
 '. Issue/spec snapshots and closed #81 dependency observation are retained in this directory.\n',
 'Baseline and final HEAD: `' + audit['baseline'] + '`. Branch: `main`. Entry was clean. No commit, push, PR, tracker change, release, production signing, deployment or publication-credential access. Final worktree contains only ticket changes; ignored caches and historical evidence are preserved.\n',
 'Candidate identity: `' + audit['candidate-identity'] + '`.\n\nContract identity: `' + audit['contract-identity'] + '`.\n',
 'Frozen unsigned candidate: 2,868 source inputs, 32 contract inputs, 23 staged artifacts and 645 staged harness inputs. ' + local('Current archive', 'candidate-final-r2/README.md') +
 ' retains source and artifact/contract/harness ZIP entries with SHA-256 manifests and ordered parts of at most 48 MiB. Concatenate each manifest’s parts in order, verify the whole-archive hash, then extract separately. All previous candidate attempts remain retained.\n',
 'Main changes: repository-only T21 recorder, exact named-case runner and real-process observations; closed five-chain collector and live replay; four-tuple Foundation routing; meaningful adversarial collector and inventory-witness validation; test-only staged-JAR isolation helper; coordinated inventory/docs/provenance. Shipping Worker code, public APIs, runtime closure, first-party inventory hashes and shipping POMs are unchanged. ' + local('Changed-file ownership', 'changed-file-ownership.json') + ' binds every reviewable ticket file; its ' + local('compressed TSV', 'changed-file-ownership.tsv.gz') + ' records exact path, SHA-256, length and ownership.\n',
 '| Criterion | Result | Observed behavior and evidence |\n| --- | --- | --- |'
]
parts += ['| ' + code + ' | ' + status + ' | ' + behavior + ' ' + evidence + ' |'
          for code, status, behavior, evidence in criteria]
parts += ['\n| Issue criterion | Contract mapping | Result |\n| --- | --- | --- |']
for ac, codes in [(1, 'C1–C2'), (2, 'C3–C6'), (3, 'C7–C8'), (4, 'C9'),
                  (5, 'C11–C14'), (6, 'C10'), (7, 'C15'), (8, 'C16–C19'), (9, 'C5–C6, C8–C10, C14–C15')]:
    status = 'PENDING final review' if ac == 8 and not review_complete else 'PASS'
    parts.append('| AC' + str(ac) + ' | ' + codes + ' | ' + status + ' |')
parts += ['\nAll previously satisfied baseline obligations, including aggregate security, remain SATISFIED: `' + ', '.join(audit['previously-satisfied-obligations-preserved']) + '`.\n',
          '\nActual Worker scope is Ubuntu 24.04 / Linux x86-64. Each row has original and fresh named-case execution plus separately passing syntax, standards, semantic, visual and contract records.\n',
          '| JDK / build | Actual runtime vendor | Immutable image | Java SHA-256 | prlimit SHA-256 | Evidence |\n| --- | --- | --- | --- | --- | --- |']
for item in audit['worker-certifications']:
    env = item['environment']
    identity = env['identity']
    scope = item['scope']
    parts.append('| ' + str(identity['jdk-major']) + ' / ' + identity['jdk-build'] + ' | ' + env['java-runtime']['vendor'] +
                 ' | `' + identity['image'] + '` | `' + identity['java-sha256'] + '` | `' + env['worker-launcher']['sha256'] +
                 '` | ' + link('actual launcher', scope + '/boundary-observations/launcher/actual.properties') + '; ' + link('five chains', scope) + ' |')
parts += ['\nRelease IMPLEMENTOR remains Eclipse Adoptium; JDK 8 actually reports java.vendor=Temurin. Both observations are preserved without relabeling. Actual Java path/argv, installed policy, prlimit version, cleared environment, dependency hashes, modes and effective limits are in each launcher record. Document inventory is 754 / `99cba401304fe7d1bbc279f8afd1cbac30bf6dc0609e2747966c276b51933297`; Provider inventory is 25 / `56340dc06714414d32db2af86d87db696cbe05de93b4ece4571bb3b412a76f16`. Closed runtime dependencies include PDFBox, pdfbox-io, FontBox, commons-logging, ICU4J and OkapiBarcode, with the existing separately qualified optional TIFF closure.\n',
          '| Qualified external producer | Observed version | Observed SHA-256 |\n| --- | --- | --- |']
env = audit['worker-certifications'][0]['environment']
for tool in env['tools']:
    if tool['id'] in ('qpdf', 'pdfcpu', 'arlington', 'pdfium-cli', 'imagemagick'):
        parts.append('| ' + tool['id'] + ' | ' + tool['version'] + ' | `' + tool['sha256'] + '` |')
parts += ['\nEvery environment record retains the full observed tool catalog, corpus/configuration and before/after raw identities. Seven actual public one-page PDF outcomes retain the existing qualified independent profiles and detected invalid-PDF/two-page/pixel controls. These certify the boundary slice’s document predicates, not unrelated capabilities.\n',
          'Required predecessor refresh order completed: transactions → values → pages → metadata → annotations → text → images → incremental → password-baseline → password-clear-metadata → password-attachments → limits → worker. ' + local('Original refresh ledger', 'refresh-final-r1/results.json') + ' and ' + local('continuation ledger', 'refresh-final-r2/results.json') + ' retain commands, timestamps, prior-index hashes and each merge result. The first attachment gate remains failed because it exceeded the delivery wrapper\'s three-hour budget. Its ' + local('diagnosis', 'validation/refresh-final-r1-password-attachments-timeout.json') + ' and ' + link('strict reuse receipt', 'capabilities/evidence/foundation/T82-final-r2/password-attachments/reuse-receipt.json') + ' distinguish six complete unchanged JDK8/11/17 tuples from two fresh JDK21 tuples. The continuation uses a six-hour delivery budget; Worker limits and the frozen staged candidate did not change. Seven missing/duplicate/identity mutation controls are rejected. Final index: 96 certifications / 392 chain records; all thirteen selected and previously certified obligations are SATISFIED.\n',
          '| Final gate | Result | Exact command / evidence |\n| --- | --- | --- |']
for name, gate in audit['gates'].items():
    command = ' '.join(gate['repository-command'])
    result = 'PASS' if gate['exit-code'] == 0 else 'NOT READY: unrelated obligations only (exit 1)'
    parts.append('| ' + name + ' | ' + result + ' | `' + command + '`; ' + link('retained log', gate['log']['path']) + ' |')
parts += ['\nFinal full verification totals: `' + json.dumps(summary['totals'], sort_keys=True) +
          '`. The four baseline optional skips are three #83 recovery/scale cases and one deferred #94 barcode raster case; mandatory T21 originals and live replays have zero skips. ' + local('All full-build reports', 'validation/full-ubuntu-verify-final-r1-reports/summary.json') + ' retain exact XML/TXT hashes. The earlier full pass and all 219 exact reports are also preserved in reviewable paths.\n',
          'Conditional `./scripts/verify-jdk-matrix.sh` was not triggered because no shipping source, shipping POM or build-compatibility change occurred. Four immutable-image T21 JDK runs remain mandatory and passed. Focused checks and the 116-test collector suite are recorded independently of the full build.\n',
          'Standards: ' + ('final review retained with no unresolved applicable finding.' if review_complete else 'final evidence/receipt review pending; frozen source review passed.') +
          ' Spec: ' + ('final review retained with no unresolved applicable finding.' if review_complete else 'final evidence/receipt review pending; frozen source review passed.') +
          ' Earlier permission/link and actual Native/Facade receipt findings were corrected before staging; the bounded pin-helper duplication judgment was explicitly retained. The delivery-only timeout continuation also passed ' + local('Standards recovery review', 'reviews/standards-recovery.md') + ' and ' + local('Spec recovery review', 'reviews/spec-recovery.md') + '; its bounded observation-sequence duplication preserves the frozen runner and completed records. ' + local('Dispositions', 'reviews/source-dispositions.md') + '.\n',
          'Failed attempts are preserved as failures with their original transcripts/raw observations and candidate archives: runtime placement, concurrent build outputs, acceptance compile/setup issues, inventory proposal/test-expectation errors, staged-JAR helper and socket-path qualification, distinct vendor labels and empty JVM settings parsing. ' + local('Diagnoses and correction evidence', 'failed-attempts.md') + '. None demonstrated a required shipping Worker behavior gap; no product policy, runtime inventory or classpath authority was relaxed.\n',
          'Remaining release blockers are outside #82:\n']
for item in audit['remaining-unrelated-blockers']:
    parts.append('- `' + item['obligation'] + '` (#' + str(item['issue']) + '): ' + item['reason'])
parts += ['\nScope and residual limits: Windows/macOS are explicitly uncertified; #83 recovery/scale remains experimental. Java Unix-domain APIs are absent on 8/11, and the policy qualification JVM is distinct from the production Worker. Modeled Folio memory/storage accounting, JVM heap/direct ceilings, CPU 300 / descriptors 64, monotonic elapsed termination and ownership are separate guarantees. No full RSS/native-memory, kernel/container isolation, arbitrary-bytecode or physical secure-erasure certification is claimed. Parent review/publication selection remains the only handoff action; no planning decision is needed for completed #82.\n',
          'The ownership manifest/summary exclude themselves to avoid circular hashes. All indexed reports and transitive references, original historical #81 evidence, ticket failed attempts and ignored caches remain retained. Publication and any later authorized DCO commit belong to the parent orchestrator.\n']
(delivery / 'receipt.md').write_text('\n'.join(parts) + '\n')
print('Rendered #82 receipt; final review complete=' + str(review_complete))
