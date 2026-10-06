"""Retain completed Maven suite reports before a subsequent clean build."""
import hashlib
import json
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[3]
destination = Path(__file__).resolve().parent / 'validation' / (sys.argv[1] + '-reports')
destination.mkdir()
modules = ('pdf-provider-contract', 'pdf-conversion', 'pdf-document', 'pdf-migration-itext7',
           'pdf-migration-itext7-preview', 'pdf-acceptance', 'build-tools/inventory', 'build-tools/release')
references = []
suites = []
for module in modules:
    for kind in ('surefire-reports', 'failsafe-reports'):
        source = root / module / 'target' / kind
        if not source.exists():
            continue
        for path in sorted(source.iterdir()):
            if not path.is_file() or path.suffix not in ('.xml', '.txt'):
                continue
            # Evidence paths omit Maven's ignored target/ component so the
            # parent can review and select all retained reports for publication.
            relative = Path(module) / kind / path.name
            target = destination / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(path, target)
            references.append({'path': target.relative_to(root).as_posix(),
                               'sha256': hashlib.sha256(target.read_bytes()).hexdigest()})
            if path.name.startswith('TEST-') and path.suffix == '.xml':
                element = ET.parse(path).getroot()
                suites.append({'module': module, 'kind': kind, 'name': element.attrib['name'],
                               **{key: int(element.attrib.get(key, '0')) for key in ('tests', 'failures', 'errors', 'skipped')}})
summary = {'suites': suites, 'totals': {key: sum(item[key] for item in suites)
                                      for key in ('tests', 'failures', 'errors', 'skipped')},
           'retained-files': references}
(destination / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
print('Retained Maven suite reports: ' + json.dumps(summary['totals']), flush=True)
