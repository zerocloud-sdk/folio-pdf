"""Collect only intact T78 observations that survive an independent tool replay."""
import importlib.util
import json
from pathlib import Path

from t13_foundation_reports import RetainedFiles

SPEC = importlib.util.spec_from_file_location('t78_certification', Path(__file__).with_name('t78-certification.py'))
CERTIFICATION = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CERTIFICATION)
PIN_SHA256 = '8a9d6255a312f44d28aa91c6614fa2218948e16e42e8059173f9d30dd3b0b17c'


def collect_reports(root, run, execution):
    require = CERTIFICATION.require
    require(execution in ('IN_PROCESS', 'HARDENED_WORKER'), 'The actual Native execution mode is required')
    files = RetainedFiles(run)
    pin = root / 'scripts/t78-evidence-pin.properties'
    require(files.authorities.digest(pin) == PIN_SHA256, 'Frozen T78 authority identities changed')
    require(files.result == {'profile': CERTIFICATION.OBSERVER.PROFILE, 'phase': 'certification',
        'native-execution-profile': execution, 'facade-execution-profile': 'IN_PROCESS',
        'retained-files-sha256': files.authorities.digest(run / 'retained-files.sha256'),
        **dict.fromkeys(CERTIFICATION.CHAINS, 'pass')}, 'Wrong or incomplete baseline evidence declaration')
    # Resealing a changed report does not establish truth. Replay the pinned
    # external predicates against the retained original randomized products.
    replay = CERTIFICATION.observe(root, run, execution, replay=True)
    require(all(files.expected[run / name] == identity for name, identity in replay.files.items()),
            'An observation changed while the collector was replaying it')
    corpus = json.loads(files.authorities.read(root / 'capabilities/profiles/T78-password/products.json'))
    reports = {}
    for chain in CERTIFICATION.CHAINS:
        findings, products, controls = [], [], []
        for api in ('native', 'facade'):
            for name in corpus['products']:
                directory = run / (api + '-' + name)
                products.append(files.reference(root, directory / 'product.pdf'))
                findings.extend(files.reference(root, path) for path in files.under(directory / chain))
                if chain == 'semantic':
                    findings.extend(files.reference(root, directory / name) for name in ('publication.properties', 'reopened.properties'))
        for path in files.under(run / 'inputs'):
            if chain in path.relative_to(run / 'inputs').parts:
                findings.append(files.reference(root, path))
        controls.extend(files.reference(root, path) for path in files.under(run / 'negative' / chain))
        require(controls and findings and products, 'A baseline chain is missing its original observations or controls')
        findings.extend(files.reference(root, run / name) for name in (
            'identities-before.json', 'identities-after.json', 'result.properties', 'retained-files.sha256',
            'products.properties', 'coverage.json'))
        reports[chain] = {'chain': chain, 'result': 'pass', 'products': products,
                          'findings': findings, 'negative-controls': controls}
    files.verify()
    return reports
