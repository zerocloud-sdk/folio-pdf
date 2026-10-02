"""Collect only intact T80 observations that survive an independent tool replay."""
import importlib.util
import json
from pathlib import Path

from t13_foundation_reports import RetainedFiles

SPEC = importlib.util.spec_from_file_location('t80_certification', Path(__file__).with_name('t80-certification.py'))
CERTIFICATION = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CERTIFICATION)
PIN_SHA256 = '7c7ed55bbb3b1a1f898e50e381bb193e1df796b92a6a20677ced978e20df170a'


def collect_reports(root, run, execution):
    require = CERTIFICATION.require
    require(execution in ('IN_PROCESS', 'HARDENED_WORKER'), 'The actual Native execution mode is required')
    files = RetainedFiles(run)
    pin = root / 'scripts/t80-evidence-pin.properties'
    require(files.authorities.digest(pin) == PIN_SHA256, 'Frozen T80 authority identities changed')
    require(files.result == {'profile': CERTIFICATION.OBSERVER.PROFILE, 'phase': 'certification',
        'native-execution-profile': execution, 'facade-execution-profile': 'IN_PROCESS',
        'retained-files-sha256': files.authorities.digest(run / 'retained-files.sha256'),
        **dict.fromkeys(CERTIFICATION.CHAINS, 'pass')}, 'Wrong or incomplete embedded-files-only evidence declaration')
    # Resealing a changed report does not establish truth. Replay the pinned
    # external predicates against the retained original randomized products.
    replay = CERTIFICATION.observe(root, run, execution, replay=True)
    require(all(files.expected[run / name] == identity for name, identity in replay.files.items()),
            'An observation changed while the collector was replaying it')
    corpus = json.loads(files.authorities.read(root / 'capabilities/profiles/T80-embedded-files-only/products.json'))
    reports = {}
    for chain in CERTIFICATION.CHAINS:
        findings, products, controls = [], [], []
        for api in ('native', 'facade'):
            for name, case in corpus['products'].items():
                if case.get('apis', 'native,facade') == 'native' and api == 'facade': continue
                directory = run / (api + '-' + name)
                products.append(files.reference(root, directory / 'product.pdf'))
                findings.extend(files.reference(root, path) for path in files.under(directory / chain))
                if chain == 'semantic':
                    findings.extend(files.reference(root, directory / name) for name in ('publication.properties', 'reopened.properties', 'clear-observation.properties'))
        for path in files.under(run / 'inputs'):
            if chain in path.relative_to(run / 'inputs').parts:
                findings.append(files.reference(root, path))
        controls.extend(files.reference(root, path) for path in files.under(run / 'negative' / chain))
        require(controls and findings and products, 'A embedded-files-only chain is missing its original observations or controls')
        findings.extend(files.reference(root, run / name) for name in (
            'identities-before.json', 'identities-after.json', 'result.properties', 'retained-files.sha256',
            'products.properties', 'coverage.json'))
        reports[chain] = {'chain': chain, 'result': 'pass', 'products': products,
                          'findings': findings, 'negative-controls': controls}
    files.verify()
    return reports
