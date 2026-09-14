"""Exercise the public Foundation CLI for the frozen extraction obligation."""
import json
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import unittest
import yaml
import zlib

ROOT = Path(__file__).resolve().parents[2]
PRODUCTS = ('nested-split-type3', 'marked-structure', 'embedded-font-kinds',
            'embedded-font-inheritance', 'uncertain-geometry')
CHAINS = ('syntax', 'standards', 'semantic', 'visual')
DECLARATIONS = ('pdfcpu', 'arlington-core', 'arlington-fonts', 'arlington-text',
                'arlington-cid', 'arlington-descriptors', 'arlington-cmaps')
PDFCPU_NOTICE = '''***************************** Disclaimer ****************************
* PDF 2.0 features are supported on a need basis.                   *
* (See ISO 32000:2 6.3.2 Conformance of PDF processors)             *
* At the moment pdfcpu ships with basic PDF 2.0 support.            *
* Please let us know which feature you would like to see supported, *
* provide a sample PDF file and create an issue:                    *
* https://github.com/pdfcpu/pdfcpu/issues/new/choose                *
* Thank you for using pdfcpu <3                                     *
*********************************************************************'''


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def props(path, values):
    write(path, ''.join(key + '=' + str(value) + '\n' for key, value in values.items()))


def read_props(path):
    return dict(line.split('=', 1) for line in path.read_text().splitlines()
                if line and not line.startswith('#') and '=' in line)


def seal(directory):
    """Publish a collector protocol receipt, not a tool qualification result."""
    manifest = directory / 'retained-files.sha256'
    result = directory / 'result.properties'
    excluded = {manifest, result}
    if read_props(result).get('product') in PRODUCTS:
        # The whole-run product producer owns these three files; the combined
        # chain receipt in the same directory owns only its own observations.
        excluded.update(directory / name for name in (
            'observation.properties', 'reopened.properties', 'publication.properties'))
    files = sorted(path for path in directory.rglob('*')
                   if path.is_file() and path not in excluded)
    write(manifest, ''.join(digest(path) + '  ' + path.relative_to(directory).as_posix() + '\n'
                           for path in files))
    lines = [line for line in result.read_text().splitlines()
             if not line.startswith('retained-files-sha256=')]
    result.write_text('\n'.join(lines) + '\nretained-files-sha256=' + digest(manifest) + '\n')


class T13FoundationTest(unittest.TestCase):
    def cli(self, root, *arguments):
        return subprocess.run(['/usr/bin/python3', str(ROOT / 'scripts/t03-foundation.py'),
                               *arguments, '--root', str(root), '--obligation', 'text'],
                              capture_output=True, text=True, timeout=30)

    def report_fixture(self, root):
        # Deliberately synthetic public collector protocol, never independent PDF evidence.
        run = root / 'run'
        for profile in ('T13-text', 'T13-fonts', 'T13-standards'):
            shutil.copytree(ROOT / 'capabilities/profiles' / profile,
                            root / 'capabilities/profiles' / profile)
        corpus = json.loads((root / 'capabilities/profiles/T13-text/corpus.json').read_text())
        flat = (root / 'capabilities/profiles/T13-text/corpus.properties').read_text().splitlines()
        for name in ('pdfcpu-pin.properties', 't13-arlington-pin.properties',
                     'qpdf-pin.properties', 't13-qpdf-runtime.sha256', 't13-semantics.py',
                     't13-program-standards.py', 'pdfium-pin.properties', 'imagemagick-pin.properties'):
            write(root / 'scripts' / name, (ROOT / 'scripts' / name).read_text())
        write(root / 'scripts/container-bin/qpdf', (ROOT / 'scripts/container-bin/qpdf').read_text())
        overall = dict.fromkeys(CHAINS, 'pass')
        overall.update({'profile': 'T13-text-logical-structure', 'phase': 'certification',
                        'native-execution-profile': 'HARDENED_WORKER',
                        'facade-execution-profile': 'IN_PROCESS'})
        for api in ('native', 'facade'):
            for index, product in enumerate(PRODUCTS):
                directory = run / (api + '-' + product)
                required = CHAINS if index < 3 else CHAINS[:3]
                pdf = directory / 'extraction.pdf'
                write(pdf, 'collector protocol ' + api + '-' + product)
                mode = 'HARDENED_WORKER' if api == 'native' else 'IN_PROCESS'
                source = corpus['sources'][product]
                props(directory / 'publication.properties', {
                    'execution-profile': mode, 'target-name': 'target', 'status': 'COMMITTED',
                    'partial-output-possible': 'false', 'source-preserved': 'pass', 'reopened': 'pass',
                    'source-sha256': source['sha256'], 'output-sha256': digest(pdf)})
                prefix = 'products.' + product + '.extraction.'
                observation = '\n'.join(line[len(prefix):] for line in flat if line.startswith(prefix)) + '\n'
                for name in ('observation.properties', 'reopened.properties'):
                    write(directory / name, observation)
                observed = {'profile': 'T13-text-logical-structure', 'product': product,
                            'input-sha256': digest(pdf), 'execution-profile': mode,
                            'required-chains': ','.join(sorted(required)),
                            'qualified-standard-rule-count': '376'}
                for chain in CHAINS:
                    verdict = 'pass' if chain in required else 'not-required'
                    observed[chain] = verdict
                    overall[api + '.' + product + '.' + chain] = verdict
                    if chain in required:
                        for folder in (('declarations', 'programs') if chain == 'standards' else (chain,)):
                            write(directory / folder / (chain + '.txt'), 'original protocol observation\n')
                props(directory / 'result.properties', observed)
                self.syntax_fixture(root, directory / 'syntax', pdf)
                self.declaration_fixture(root, directory / 'declarations', pdf)
                self.semantic_fixture(root, directory / 'semantic', pdf, source, mode, observation)
                self.program_fixture(root, directory / 'programs', pdf)
                if 'visual' in required:
                    self.visual_fixture(root, directory / 'visual', pdf, product, corpus['products'][product]['visual'][0])
                seal(directory)
        props(run / 'products.properties', {'phase': 'products-only',
              'native-execution-profile': 'HARDENED_WORKER', 'facade-execution-profile': 'IN_PROCESS'})
        props(run / 'negative/result.properties', dict(dict.fromkeys(CHAINS, 'fail'), **{
            'profile': 'T13-text-logical-structure', 'native-execution-profile': 'HARDENED_WORKER'}))
        for chain in CHAINS:
            write(run / 'negative' / chain / 'raw.txt', 'original detected protocol defect\n')
        truncated = root / 'protocol-inputs/truncated.pdf'
        write(truncated, '%PDF-2.0\n1 0 obj\n')
        self.syntax_fixture(root, run / 'negative/syntax', truncated, 'fail')
        malformed = root / 'protocol-inputs/catalog.pdf'
        source = root / 'capabilities/profiles/T13-text' / corpus['sources']['marked-structure']['path']
        malformed.write_bytes(source.read_bytes().replace(b'/Type /Catalog', b'/Type /Bogus  '))
        self.declaration_fixture(root, run / 'negative/standards', malformed, 'fail')
        self.semantic_controls_fixture(root, run, corpus, flat)
        self.visual_controls_fixture(root, run, corpus)
        seal(run / 'negative')
        props(run / 'result.properties', overall)
        seal(run)
        return run

    def syntax_fixture(self, root, directory, pdf, verdict='pass'):
        pin = read_props(root / 'scripts/qpdf-pin.properties')
        identity = {'qpdf-version': pin['QPDF_VERSION'],
                    'qpdf-binary-sha256': pin['QPDF_BINARY_SHA256'],
                    'qpdf-wrapper-sha256': digest(root / 'scripts/container-bin/qpdf'),
                    'qpdf-pin-sha256': digest(root / 'scripts/qpdf-pin.properties'),
                    'qpdf-runtime-sha256': digest(root / 'scripts/t13-qpdf-runtime.sha256')}
        identity.update({'qpdf-runtime.' + name: value for value, name in
                         (line.split('  ') for line in (root / 'scripts/t13-qpdf-runtime.sha256').read_text().splitlines())})
        shutil.copyfile(pdf, directory / 'extraction.pdf')
        props(directory / 'result.properties', dict(identity, **{
            'profile': 'T13-text-logical-structure', 'syntax': verdict, 'qpdf-result': verdict,
            'input-sha256': digest(pdf), 'input-after-sha256': digest(pdf),
            'retained-after-sha256': digest(pdf)}))
        common = 'Input exact SHA-256: `' + digest(pdf) + '`\n\nFinal determination: `' + verdict + '`\n\n'
        write(directory / 'qpdf-syntax.md', common +
              'Capability: `document.text-structure.extract`\n\nAcceptance Profile: `T13-text-logical-structure`\n\n'
              'Chain: `syntax`\n\nResult: `' + verdict + '`\n\nProducer kind: `external-tool`\n\n'
              'Producer: `qpdf`\n\nProducer version: `12.4.0`\n\nTool distribution SHA-256: `'
              + pin['QPDF_ARCHIVE_SHA256'] + '`\n')
        write(directory / 'qpdf-syntax.txt', common + 'Tool: `qpdf`\n\nTool version: `12.4.0`\n\n'
              'Distribution SHA-256: `' + pin['QPDF_ARCHIVE_SHA256'] + '`\n\n'
              'Invocation: `qpdf --check extraction.pdf`\n\n`extraction.pdf` exit code: `0`\n\n'
              '### Standard output\n\n```text\nchecking extraction.pdf\nPDF Version: 1.7\n'
              'File is not encrypted\nFile is not linearized\n'
              'No syntax or stream encoding errors found; the file may still contain\n'
              'errors that qpdf cannot detect\n```\n\n### Standard error\n\n```text\n\n```\n')
        if verdict == 'fail':
            write(directory / 'qpdf-syntax.txt', common + 'Tool: `qpdf`\n\nTool version: `12.4.0`\n\n'
                  'Distribution SHA-256: `' + pin['QPDF_ARCHIVE_SHA256'] + '`\n\n'
                  'Invocation: `qpdf --check extraction.pdf`\n\n`extraction.pdf` exit code: `2`\n\n'
                  '### Standard output\n\n```text\n\n```\n\n### Standard error\n\n```text\n'
                  'qpdf: extraction.pdf: unable to find trailer dictionary while recovering damaged file\n```\n')
        for name in ('syntax.md', 'syntax.txt'):
            write(directory / name, common + 'Raw qpdf result before final identity checks: `' + verdict + '`\n')
        seal(directory)

    def declaration_fixture(self, root, directory, pdf, verdict='pass'):
        authority = root / 'capabilities/profiles/T13-standards'
        identities = {'required-rules-sha256': digest(authority / 'required-rules.txt')}
        for group in DECLARATIONS:
            profile_path = authority / (group + '.properties')
            profile = read_props(profile_path)
            pin_name = 't13-arlington' if group.startswith('arlington') else 'pdfcpu'
            pin_path = root / 'scripts' / (pin_name + '-pin.properties')
            pin = read_props(pin_path)
            identities['profile.' + group + '.sha256'] = digest(profile_path)
            identities[pin_name + '.pin-sha256'] = digest(pin_path)
            identities[pin_name + '.executable-sha256'] = pin['sha256']
            if pin_name == 't13-arlington':
                for kind in ('model', 'patch'):
                    identities[pin_name + '.' + kind + '-sha256'] = pin[kind + '-sha256']
            props(directory / group / 'standards.properties', {
                'result': verdict, 'profile': 'T13-text-logical-structure',
                'covered-rules': profile['required-rules'] if verdict == 'pass' else '', 'input-sha256': digest(pdf),
                'executable-sha256': pin['sha256'], 'pin-sha256': digest(pin_path),
                'model-sha256': pin.get('model-sha256', 'none'),
                'patch-sha256': pin.get('patch-sha256', 'none'),
                'profile-sha256': digest(profile_path)})
            positive = ('validating(mode=strict) extraction.pdf ...\n\n' + PDFCPU_NOTICE + '\nvalidation ok'
                        if group == 'pdfcpu' else
                        'Processing extraction.pdf BEGIN - TestGrammar version\nInfo: checked\nEND\n\nDONE - 1 files processed')
            findings = ['Version exit: 0\nversion: ' + pin['version'] + '\nValidation exit: 0\n' + positive]
            if verdict == 'fail':
                failure = ('1\nvalidation error: document catalog: dict=rootDict entry=Type invalid dict entry: Bogus'
                           if group == 'pdfcpu' else
                           '0\nProcessing extraction.pdf BEGIN - TestGrammar version\nError: wrong value for possible values: '
                           'Type (Catalog) should be: name [Catalog] in PDF 2.0 and is name==Bogus\nEND\n\nDONE - 1 files processed')
                findings = ['Version exit: 0\nversion: ' + pin['version'] + '\nValidation exit: ' + failure]
            for rule in (profile['required-rules'].split(',') if verdict == 'pass' else []):
                key = 'negative.' + rule
                source = authority / profile[key + '.path']
                if not source.is_file():
                    source.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(ROOT / 'capabilities/profiles/T13-standards' / profile[key + '.path'], source)
                shutil.copyfile(source, directory / group / ('control-' + rule + '.pdf'))
                write(directory / group / ('negative-' + rule + '.txt'),
                      ('exit=1\nvalidation error: ' if group == 'pdfcpu' else 'exit=0\nError: ')
                      + profile[key + '.finding'] + '\n')
                findings.append('Negative control ' + rule + ' sha256=' + profile[key + '.sha256'] + ' detected=true')
            write(directory / group / 'findings.txt', '\n'.join(findings) + '\n')
        for name in ('identities-before.properties', 'identities-after.properties'):
            props(directory / name, identities)
        shutil.copyfile(pdf, directory / 'extraction.pdf')
        props(directory / 'result.properties', {
            'profile': 'T13-text-logical-structure', 'standards-scope': 'declarations-only',
            'input-sha256': digest(pdf), 'declarations': verdict, 'qualified-rule-count': '334' if verdict == 'pass' else '0',
            'covered-rules': ','.join(sorted((authority / 'required-rules.txt').read_text().splitlines())) if verdict == 'pass' else '',
            'required-rules-sha256': digest(authority / 'required-rules.txt'),
            'identities-before-sha256': digest(directory / 'identities-before.properties'),
            'identities-after-sha256': digest(directory / 'identities-after.properties')})
        seal(directory)

    def semantic_fixture(self, root, directory, pdf, source, mode, observation, verdict='pass'):
        child = directory / 'qpdf'
        syntax_identity = read_props(root / 'run/native-nested-split-type3/syntax/result.properties')
        identity = {key: syntax_identity[key] for key in ('qpdf-version', 'qpdf-binary-sha256',
                    'qpdf-wrapper-sha256', 'qpdf-runtime-sha256')}
        common = dict(identity, **{'profile': 'T13-text-logical-structure', 'semantic': verdict,
            'semantic-scope': 'independent-qpdf-decoded-graph-preservation',
            'input-sha256': digest(pdf), 'reference-sha256': source['sha256'],
            'expectations-sha256': digest(root / 'capabilities/profiles/T13-text/corpus.json'),
            'observer-sha256': digest(root / 'scripts/t13-semantics.py'), 'python-version': '3.12.3',
            'python-executable-sha256': '1643dacd9feaedc58f3cc581e4d22577dfe25c09b10282936186ccf0f2e61118',
            'finding': 'The complete reachable decoded graph matches the frozen original Source.'})
        for stem in ('reference-qpdf', 'qpdf'):
            write(child / (stem + '.json'), '{"version": 2, "qpdf": [{"jsonversion": 2}, {}]}\n')
            write(child / (stem + '-stderr.txt'), '')
        for name in ('reference.json', 'observed.json'):
            write(child / name, '{"catalog": {"reference": 1}, "objects": [{"value": {"/Type": "/Catalog"}}]}\n')
        if verdict == 'fail':
            write(child / 'observed.json', '{"catalog": {"reference": 2}, "objects": [{"value": {"/Type": "/Catalog"}}]}\n')
        write(child / 'qpdf-version.txt', 'qpdf version 12.4.0\nRun qpdf --copyright to see copyright and license information.\n')
        write(child / 'qpdf-version-stderr.txt', '')
        common.update({
            'qpdf-json-sha256': digest(child / 'qpdf.json'),
            'reference-qpdf-json-sha256': digest(child / 'reference-qpdf.json')})
        props(child / 'result.properties', common)
        write(child / 'semantic.txt', common['finding'] + '\n')
        write(directory / 'semantic-command.txt', 'observer: scripts/t13-semantics.py\ninput exact SHA-256: '
              + digest(pdf) + '\nexit-code: 0\n' + ''.join(digest(path) + '  ' + path.name + '\n'
              for path in sorted(child.iterdir())))
        if verdict == 'pass':
            write(directory / 'observation.properties', observation)
        write(directory / 'semantic.txt', 'Input exact SHA-256: ' + digest(pdf) + '\n' + common['finding'] + '\n')
        shutil.copyfile(pdf, directory / 'extraction.pdf')
        props(directory / 'result.properties', dict(common, **{
            'execution-profile': mode if verdict == 'pass' else 'unavailable',
            'public-observation': 'pass' if verdict == 'pass' else 'indeterminate'}))
        seal(directory)

    def program_fixture(self, root, directory, pdf):
        authority = root / 'capabilities/profiles/T13-standards'
        profile = read_props(authority / 'program-qualification.properties')
        identity = {key: value for key, value in read_props(directory.parent / 'syntax/result.properties').items()
                    if key.startswith('qpdf-') and key != 'qpdf-result'}
        identity.update({'observer-sha256': digest(root / 'scripts/t13-program-standards.py'),
            'qualification-profile-sha256': digest(authority / 'program-qualification.properties'),
            'fonttools-wheel-sha256': '8bd0f759020e87bb5d323e6283914d9bf4ae35a7307dafb2cbd1e379e720ad37',
            'python-executable-sha256': '1643dacd9feaedc58f3cc581e4d22577dfe25c09b10282936186ccf0f2e61118'})
        common = {key: identity[key] for key in ('observer-sha256', 'python-executable-sha256',
                  'qpdf-version', 'qpdf-binary-sha256', 'qpdf-wrapper-sha256', 'qpdf-runtime-sha256')}
        common.update({'profile': 'T13-text-logical-structure', 'python-version': '3.12.3',
                       'python-optimization-level': '0'})

        def observe(folder, scope, source, verdict, rule=None):
            values = dict(common, **{'input-sha256': digest(source), 'scope': scope,
                                     'standards': verdict, 'finding': 'protocol observation'})
            if scope in ('fonts', 'font-metrics'):
                values.update({'fonttools-version': '4.59.2', 'fonttools-wheel-sha256': identity['fonttools-wheel-sha256']})
            if rule:
                values['rule'] = rule
            write(folder / 'qpdf-version.txt', 'qpdf version 12.4.0\nRun qpdf --copyright to see copyright and license information.\n')
            write(folder / 'qpdf-version.txt.stderr', '')
            write(folder / 'qpdf.json', '{"version": 2, "qpdf": [{"jsonversion": 2}, {}]}\n')
            write(folder / 'qpdf.json.stderr', '')
            values['qpdf-json-sha256'] = digest(folder / 'qpdf.json')
            props(folder / 'result.properties', values)
            write(folder / 'process.txt', 'exit=0\n' + ''.join(digest(path) + '  ' + path.name + '\n'
                  for path in sorted(folder.iterdir())))

        for scope in profile['scopes'].split(','):
            observe(directory / 'input' / scope, scope, pdf, 'pass')
        findings = ['Input exact SHA-256: ' + digest(pdf)]
        for control in profile['controls'].split(','):
            key = 'negative.' + control
            source = authority / profile[key + '.path']
            copied = directory / ('control-' + control + '.pdf')
            shutil.copyfile(source, copied)
            identity[key + '.sha256'] = profile[key + '.sha256']
            observe(directory / 'qualifications' / control, profile[key + '.scope'], copied, 'fail', profile[key + '.rule'])
            findings.append('Negative control ' + control + ' sha256=' + profile[key + '.sha256']
                            + ' detected=true rule=' + profile[key + '.rule'])
        for name in ('identities-before.properties', 'identities-after.properties'):
            props(directory / name, identity)
        write(directory / 'programs.txt', '\n'.join(findings) + '\n')
        shutil.copyfile(pdf, directory / 'extraction.pdf')
        props(directory / 'result.properties', dict(identity, **{
            'profile': 'T13-text-logical-structure', 'standards-scope': 'programs-only',
            'program-standards': 'pass', 'input-sha256': digest(pdf), 'qualified-rule-count': '42',
            'negative-control-count': '165', 'covered-rules': profile['required-rules']}))
        seal(directory)

    def visual_fixture(self, root, directory, pdf, product, visual, verdict='pass'):
        directory.mkdir(parents=True, exist_ok=True)
        renderer_metric = '86.9647' if product == 'embedded-font-kinds' and verdict == 'pass' else '0'
        renderer_pixels = '672' if product == 'embedded-font-kinds' and verdict == 'pass' else '0'
        primary_metric = '1' if verdict == 'fail' else '0'
        for kind in ('expected', 'pdfium', 'implementation', 'difference', 'renderer-difference'):
            shutil.copyfile(root / 'capabilities/profiles/T13-text' / visual['raster'], directory / ('page-1-' + kind + '.png'))
        if verdict == 'fail':
            for kind in ('pdfium', 'implementation'):
                shutil.copyfile(ROOT / 'scripts/tests/fixtures/t13-foundation/outside-ink.png',
                                directory / ('page-1-' + kind + '.png'))
        pin = read_props(root / 'scripts/pdfium-pin.properties')
        magick = read_props(root / 'scripts/imagemagick-pin.properties')
        common = {'Input exact SHA-256': digest(pdf), 'Final determination': verdict,
            'Expected raster SHA-256': digest(directory / 'page-1-expected.png'),
            'PDFium raster SHA-256': digest(directory / 'page-1-pdfium.png'),
            'Implementation raster SHA-256': digest(directory / 'page-1-implementation.png'),
            'Expected comparison AE': primary_metric, 'Renderer agreement AE': renderer_metric, 'Review required': 'false',
            'PDFium CLI version': 'v0.11.2', 'PDFium engine version': 'chromium-7881',
            'ImageMagick version': '7.1.2-30', 'Implementation renderer version': '3.0.8'}
        record = dict(common, **{'Capability': 'document.text-structure.extract',
            'Acceptance Profile': 'T13-text-logical-structure', 'Chain': 'visual', 'Result': verdict,
            'Producer kind': 'external-tool', 'Producer': 'pdfium-cli',
            'Producer version': 'v0.11.2-pdfium-chromium-7881',
            'PDFium engine distribution SHA-256': pin['PDFIUM_ENGINE_ARCHIVE_SHA256'],
            'PDFium distribution SHA-256': pin['PDFIUM_ARCHIVE_SHA256'],
            'PDFium executable SHA-256': pin['PDFIUM_EXECUTABLE_SHA256'],
            'ImageMagick distribution SHA-256': magick['IMAGEMAGICK_ARCHIVE_SHA256'],
            'ImageMagick executable SHA-256': magick['IMAGEMAGICK_EXECUTABLE_SHA256']})
        profile = read_props(root / 'capabilities/profiles/T13-text/visual' / (product + '-page-1.properties'))
        raw = dict(common, **{'Profile': profile['PROFILE_ID'], 'Page box': profile['PAGE_BOX'],
            'DPI': profile['DPI'], 'Color policy': profile['COLOR_POLICY'],
            'Font policy': profile['FONT_POLICY'], 'Antialiasing policy': profile['ANTIALIASING_POLICY'],
            'Raster dimensions': '240x200', 'Comparison metric': 'AE', 'Comparison fuzz percent': '0',
            'Comparison threshold': '0', 'Renderer agreement threshold': profile['RENDERER_AGREEMENT_THRESHOLD'],
            'Difference raster SHA-256': digest(directory / 'page-1-difference.png'),
            'Renderer difference raster SHA-256': digest(directory / 'page-1-renderer-difference.png')})
        metadata = lambda values: ''.join(key + ': `' + str(value) + '`\n\n' for key, value in values.items())
        comparison = 'magick compare -metric AE -fuzz 0% -highlight-color #ff0000 -lowlight-color #ffffff -define png:exclude-chunk=time,date '
        processes = (
            ('PDFium identity', 'pdfium --version', 'pdfium version v0.11.2', ''),
            ('PDFium render', 'pdfium render extraction.pdf page-1-pdfium.png --dpi 144 --file-type png --pages first', 'Rendered page 1 into page-1-pdfium.png', ''),
            ('ImageMagick identity', 'magick --version', 'Version: ImageMagick 7.1.2-30 Q16-HDRI', ''),
            ('Expected-to-PDFium raster comparison', comparison + 'page-1-expected.png page-1-pdfium.png page-1-difference.png', '', primary_metric + ' (0)'),
            ('PDFium-to-implementation renderer comparison', comparison + 'page-1-pdfium.png page-1-implementation.png page-1-renderer-difference.png', '', renderer_metric + ' (0.00181176)'))
        text = metadata(raw)
        for title, invocation, stdout, stderr in processes:
            code = '1' if title.startswith('PDFium-to-') and renderer_metric != '0' else '0'
            if title.startswith('Expected-to-') and verdict == 'fail':
                code = '1'
            text += '## ' + title + '\n\nInvocation: `' + invocation + '`\n\nExit code: `' + code + '`\n\n'
            text += '### Standard output\n\n```text\n' + stdout + '\n```\n\n### Standard error\n\n```text\n' + stderr + '\n```\n\n'
        text += 'Exact changed RGB pixels, expected to PDFium: ' + primary_metric + '\nExact changed RGB pixels, PDFium to secondary: ' + renderer_pixels + '\n'
        write(directory / 'page-1-visual.md', metadata(record))
        write(directory / 'page-1-visual.txt', text)
        if product == 'embedded-font-kinds' and verdict == 'pass':
            props(directory / 'font-raster-agreement.properties', {
                'profile': profile['PROFILE_ID'], 'raster-agreement': 'pass', 'changed-pixels': renderer_pixels,
                'outside-edge-pixels': '0', 'primary-sha256': digest(directory / 'page-1-pdfium.png'),
                'secondary-sha256': digest(directory / 'page-1-implementation.png')})
        shutil.copyfile(pdf, directory / 'extraction.pdf')
        props(directory / 'result.properties', {'profile': 'T13-text-logical-structure',
              'input-sha256': digest(pdf), 'visual': verdict, 'page-count': '1', 'page.1.visual': verdict})
        seal(directory)

    def semantic_controls_fixture(self, root, run, corpus, flat):
        directory = run / 'negative/semantic'
        result = {'profile': 'T13-text-logical-structure', 'native-execution-profile': 'HARDENED_WORKER',
                  'semantic': 'fail', 'positive': 'pass', 'control-count': '12'}
        for product in PRODUCTS:
            source = corpus['sources'][product]
            prefix = 'products.' + product + '.extraction.'
            observation = '\n'.join(line[len(prefix):] for line in flat if line.startswith(prefix)) + '\n'
            pdf = root / 'capabilities/profiles/T13-text' / source['path']
            self.semantic_fixture(root, directory / ('positive-' + product), pdf, source,
                                  'HARDENED_WORKER', observation)
            result['positive.' + product] = 'pass'
        controls = (
            ('contents-order', 'nested-split-type3', '/Contents [4 0 R 5 0 R]', '/Contents [5 0 R 4 0 R]'),
            ('form-position', 'nested-split-type3', '/Matrix [2 0 0 1 5 10]', '/Matrix [2 0 0 1 6 10]'),
            ('font-width', 'nested-split-type3', '/Widths [500]', '/Widths [600]'),
            ('replacement', 'marked-structure', '/ActualText (Outer)', '/ActualText (Other)'),
            ('alternate', 'marked-structure', '/Alt (PageAlt)', '/Alt (PageBad)'),
            ('language', 'marked-structure', '/Lang (fr)', '/Lang (de)'),
            ('mcr', 'marked-structure', '/Stm 7 0 R /MCID 0', '/Stm 7 0 R /MCID 1'),
            ('parent-tree', 'marked-structure', '/Nums [0 [10 0 R] 1 [10 0 R] 2 10 0 R]', '/Nums [0 [11 0 R] 1 [10 0 R] 2 10 0 R]'),
            ('object-reference', 'marked-structure', '/Obj 12 0 R', '/Obj 11 0 R'),
            ('role-target', 'marked-structure', '/Intermediate [/Document 15 0 R]', '/Intermediate [/Document 14 0 R]'),
            ('empty-replacement', 'marked-structure', '/ActualText ()', '              '),
            ('namespace-reference', 'marked-structure', '/S /Span /NS 15 0 R', '/S /Span /NS 14 0 R'))
        for name, product, before, after in controls:
            source = corpus['sources'][product]
            original = (root / 'capabilities/profiles/T13-text' / source['path']).read_bytes()
            pdf = root / 'protocol-inputs' / ('semantic-' + name + '.pdf')
            pdf.write_bytes(original.replace(before.encode('ascii'), after.encode('ascii')))
            self.semantic_fixture(root, directory / name, pdf, source, 'HARDENED_WORKER', '', 'fail')
            result[name] = 'fail'
        props(directory / 'result.properties', result)
        seal(directory)

    def visual_controls_fixture(self, root, run, corpus):
        directory = run / 'negative/visual'
        result = {'profile': 'T13-text-logical-structure', 'visual': 'fail', 'positive': 'pass',
                  'positive.embedded-font-kinds': 'pass', 'control-count': '4', 'raster-control-count': '3',
                  'source-sha256': corpus['sources']['nested-split-type3']['sha256']}
        for folder, product in (('positive', 'nested-split-type3'), ('positive-embedded-font-kinds', 'embedded-font-kinds')):
            source = corpus['sources'][product]
            self.visual_fixture(root, directory / folder, root / 'capabilities/profiles/T13-text' / source['path'],
                                product, corpus['products'][product]['visual'][0])
        controls = (
            ('glyph-ink', 'nested-split-type3', '0 0 400 600 re f', '0 0 300 600 re f'),
            ('form-position', 'nested-split-type3', '/Matrix [2 0 0 1 5 10]', '/Matrix [2 0 0 1 6 10]'),
            ('simple-font-position', 'embedded-font-kinds', '1 0 0 1 10 20 Tm', '1 0 0 1 11 20 Tm'),
            ('vertical-font-position', 'embedded-font-kinds', '1 0 0 1 40 80 Tm', '1 0 0 1 41 80 Tm'))
        for name, product, before, after in controls:
            source = corpus['sources'][product]
            original = (root / 'capabilities/profiles/T13-text' / source['path']).read_bytes()
            pdf = root / 'protocol-inputs' / ('visual-' + name + '.pdf')
            pdf.write_bytes(original.replace(before.encode(), after.encode()))
            self.visual_fixture(root, directory / name, pdf, product, corpus['products'][product]['visual'][0], 'fail')
            result[name] = 'fail'
        raster = directory / 'raster'
        raster.mkdir()
        original = root / 'capabilities/profiles/T13-fonts/embedded-font-kinds-reference.png'
        shutil.copyfile(original, raster / 'original.png')
        result['raster.primary-sha256'] = digest(original)
        for name, changed, outside in (('edge-only', 1, 0), ('missing-glyph', 432, 308), ('interior-hole', 1, 1), ('outside-ink', 1, 1)):
            folder = raster / name
            folder.mkdir()
            shutil.copyfile(ROOT / 'scripts/tests/fixtures/t13-foundation' / (name + '.png'), folder / 'secondary.png')
            verdict = 'pass' if name == 'edge-only' else 'fail'
            props(folder / 'result.properties', {'profile': 'T13-text-logical-structure-embedded-font-kinds-page-1',
                'raster-agreement': verdict, 'primary-sha256': digest(original), 'secondary-sha256': digest(folder / 'secondary.png'),
                'changed-pixels': str(changed), 'outside-edge-pixels': str(outside)})
            result['raster.' + name] = verdict
        props(directory / 'result.properties', result)
        seal(directory)

    def test_public_collect_preserves_required_chain_products_and_original_artifact_receipts(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = self.report_fixture(root)
            command = ('collect', 'run', '--execution-profile', 'HARDENED_WORKER')
            completed = self.cli(root, *command)
            self.assertEqual(0, completed.returncode, completed.stderr)
            reports = json.loads(completed.stdout)
            self.assertEqual(set(CHAINS), set(reports))
            self.assertEqual(10, len(reports['syntax']['products']))
            self.assertEqual(10, len(reports['standards']['products']))
            self.assertEqual(10, len(reports['semantic']['products']))
            self.assertEqual(6, len(reports['visual']['products']))
            self.assertIn('run/native-marked-structure/semantic/semantic.txt',
                          {item['path'] for item in reports['semantic']['findings']})
            self.assertIn('run/negative/visual/raw.txt',
                          {item['path'] for item in reports['visual']['negative-controls']})
            report = run / 'native-marked-structure/semantic/semantic.txt'
            report.write_text('changed after original observation\n')
            self.assertNotEqual(0, self.cli(root, *command).returncode)
            self.assertNotEqual(0, self.cli(root, 'collect', 'run').returncode)

    def test_public_collect_requires_successful_publication_and_exact_detached_reopened_values(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = self.report_fixture(root)
            command = ('collect', 'run', '--execution-profile', 'HARDENED_WORKER')
            self.assertEqual(0, self.cli(root, *command).returncode)
            substitutions = (
                ('publication.properties', 'status=COMMITTED', 'status=ROLLED_BACK'),
                ('publication.properties', 'source-preserved=pass', 'source-preserved=fail'),
                ('publication.properties', 'execution-profile=IN_PROCESS', 'execution-profile=HARDENED_WORKER'),
                ('observation.properties', 'pages.0.text=A AA', 'pages.0.text=A  AA'),
                ('reopened.properties', 'pages.0.items.0.source=41', 'pages.0.items.0.source=42'))
            directory = run / 'facade-uncertain-geometry'
            for name, before, after in substitutions:
                path = directory / name
                original = path.read_text()
                self.assertIn(before, original)
                path.write_text(original.replace(before, after))
                seal(directory)
                seal(run)
                self.assertNotEqual(0, self.cli(root, *command).returncode, name + ': ' + before)
                path.write_text(original)
                seal(directory)
                seal(run)

    def test_public_collect_requires_every_qualified_declaration_and_detected_original_control(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = self.report_fixture(root)
            command = ('collect', 'run', '--execution-profile', 'HARDENED_WORKER')
            self.assertEqual(0, self.cli(root, *command).returncode)
            directory = run / 'native-marked-structure/declarations'
            for name, before, after in (
                    ('arlington-cmaps/standards.properties', 'covered-rules=cmap-encoding-name-name,', 'covered-rules='),
                    ('arlington-cmaps/standards.properties', 'executable-sha256=', 'missing-executable='),
                    ('arlington-cmaps/findings.txt', 'detected=true', 'detected=false'),
                    ('arlington-cmaps/negative-cmap-encoding-name-name.txt', 'Error: wrong type:', 'No error:')):
                path = directory / name
                original = path.read_text()
                self.assertIn(before, original)
                path.write_text(original.replace(before, after))
                for parent in (directory, directory.parent, run):
                    seal(parent)
                self.assertNotEqual(0, self.cli(root, *command).returncode, name)
                path.write_text(original)
                for parent in (directory, directory.parent, run):
                    seal(parent)

    def test_public_collect_does_not_adopt_a_late_changed_generation_receipt(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = self.report_fixture(root)
            # The authority is read after the run's original manifest. Its access
            # time coordinates this test-owned file edit at the public CLI boundary.
            anchor = root / 'capabilities/profiles/T13-text/corpus.json'
            os.utime(anchor, ns=(0, anchor.stat().st_mtime_ns))
            command = ['/usr/bin/python3', str(ROOT / 'scripts/t03-foundation.py'),
                       'collect', 'run', '--execution-profile', 'HARDENED_WORKER',
                       '--root', str(root), '--obligation', 'text']
            process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            changed = False
            try:
                deadline = time.monotonic() + 30
                while process.poll() is None and time.monotonic() < deadline:
                    if anchor.stat().st_atime_ns != 0:
                        with (run / 'products.properties').open('a') as receipt:
                            receipt.write('# changed after original receipt verification\n')
                        changed = True
                        break
                    time.sleep(0.001)
                stdout, stderr = process.communicate(timeout=30)
            finally:
                if process.poll() is None:
                    process.kill()
                    process.communicate()
            self.assertTrue(changed, 'The original generation receipt must actually change during collection')
            self.assertNotEqual(0, process.returncode,
                                'The collector adopted changed producer bytes as passing evidence: ' + stderr)

    def test_public_collect_requires_completed_declaration_processes_for_qualification(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = self.report_fixture(root)
            command = ('collect', 'run', '--execution-profile', 'HARDENED_WORKER')
            self.assertEqual(0, self.cli(root, *command).returncode)
            directory = run / 'native-marked-structure/declarations'
            for name, before, after in (
                    ('pdfcpu/negative-catalog-type.txt', 'exit=1', 'exit=137'),
                    ('arlington-cmaps/negative-cmap-encoding-name-name.txt', 'exit=0', 'exit=137'),
                    ('pdfcpu/findings.txt', 'Validation exit: 0', 'Validation exit: 137'),
                    ('arlington-cmaps/findings.txt', 'DONE - 1 files processed', 'interrupted')):
                path = directory / name
                original = path.read_text()
                self.assertIn(before, original)
                path.write_text(original.replace(before, after))
                for parent in (directory, directory.parent, run):
                    seal(parent)
                self.assertNotEqual(0, self.cli(root, *command).returncode, name)
                path.write_text(original)
                for parent in (directory, directory.parent, run):
                    seal(parent)

    def test_public_collect_rejects_self_referential_original_manifest_entries(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = self.report_fixture(root)
            command = ('collect', 'run', '--execution-profile', 'HARDENED_WORKER')
            self.assertEqual(0, self.cli(root, *command).returncode)
            manifest = run / 'retained-files.sha256'
            record = run / 'result.properties'
            original_manifest = manifest.read_text()
            original_record = read_props(record)
            for name in ('result.properties', 'retained-files.sha256'):
                manifest.write_text(original_manifest + '0' * 64 + '  ' + name + '\n')
                props(record, dict(original_record, **{'retained-files-sha256': digest(manifest)}))
                self.assertNotEqual(0, self.cli(root, *command).returncode,
                                    'Conflicting original receipt was silently overwritten: ' + name)
                manifest.write_text(original_manifest)
                props(record, original_record)

    def test_public_collect_requires_successful_identity_bound_syntax_observations(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = self.report_fixture(root)
            command = ('collect', 'run', '--execution-profile', 'HARDENED_WORKER')
            completed = self.cli(root, *command)
            self.assertEqual(0, completed.returncode, completed.stderr)
            directory = run / 'native-nested-split-type3/syntax'
            for name, before, after in (
                    ('result.properties', 'syntax=pass', 'syntax=fail'),
                    ('result.properties', 'qpdf-version=12.4.0', 'qpdf-version=unqualified'),
                    ('qpdf-syntax.txt', 'exit code: `0`', 'exit code: `137`'),
                    ('qpdf-syntax.txt', '### Standard error\n\n```text\n\n```',
                     '### Standard error\n\n```text\ninterrupted\n```'),
                    ('qpdf-syntax.md', 'Input exact SHA-256: `', 'Different input: `')):
                path = directory / name
                original = path.read_text()
                self.assertIn(before, original)
                path.write_text(original.replace(before, after))
                for parent in (directory, directory.parent, run):
                    seal(parent)
                self.assertNotEqual(0, self.cli(root, *command).returncode, name + ': ' + before)
                path.write_text(original)
                for parent in (directory, directory.parent, run):
                    seal(parent)

    def test_public_collect_requires_original_semantic_producer_receipts_and_matching_public_values(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = self.report_fixture(root)
            command = ('collect', 'run', '--execution-profile', 'HARDENED_WORKER')
            completed = self.cli(root, *command)
            self.assertEqual(0, completed.returncode, completed.stderr)
            directory = run / 'facade-uncertain-geometry/semantic'
            for name, before, after in (
                    ('result.properties', 'semantic=pass', 'semantic=fail'),
                    ('result.properties', 'execution-profile=IN_PROCESS', 'execution-profile=HARDENED_WORKER'),
                    ('semantic-command.txt', 'exit-code: 0', 'exit-code: 137'),
                    ('semantic-command.txt', '  observed.json', '  missing.json'),
                    ('qpdf/result.properties', 'qpdf-version=12.4.0', 'qpdf-version=unqualified'),
                    ('qpdf/observed.json', '"reference": 1', '"reference": 2'),
                    ('observation.properties', 'pages.0.text=A AA', 'pages.0.text=A  AA')):
                path = directory / name
                original = path.read_text()
                self.assertIn(before, original)
                path.write_text(original.replace(before, after))
                for parent in (directory, directory.parent, run):
                    seal(parent)
                self.assertNotEqual(0, self.cli(root, *command).returncode, name + ': ' + before)
                path.write_text(original)
                for parent in (directory, directory.parent, run):
                    seal(parent)

    def test_public_collect_requires_all_program_scopes_rules_controls_and_original_process_receipts(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = self.report_fixture(root)
            command = ('collect', 'run', '--execution-profile', 'HARDENED_WORKER')
            completed = self.cli(root, *command)
            self.assertEqual(0, completed.returncode, completed.stderr)
            directory = run / 'native-nested-split-type3/programs'
            for name, before, after in (
                    ('result.properties', 'qualified-rule-count=42', 'qualified-rule-count=41'),
                    ('input/fonts/result.properties', 'standards=pass', 'standards=indeterminate'),
                    ('qualifications/content.show-name/result.properties', 'rule=content-operands', 'rule=unknown'),
                    ('qualifications/content.show-name/process.txt', 'exit=0', 'exit=137'),
                    ('qualifications/content.show-name/process.txt', '  qpdf.json', '  missing.json'),
                    ('identities-after.properties', 'qpdf-version=12.4.0', 'qpdf-version=unqualified'),
                    ('programs.txt', 'detected=true', 'detected=false')):
                path = directory / name
                original = path.read_text()
                self.assertIn(before, original)
                path.write_text(original.replace(before, after))
                for parent in (directory, directory.parent, run):
                    seal(parent)
                self.assertNotEqual(0, self.cli(root, *command).returncode, name + ': ' + before)
                path.write_text(original)
                for parent in (directory, directory.parent, run):
                    seal(parent)

    def test_public_collect_requires_qualified_visual_processes_original_rasters_and_bounded_font_edges(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = self.report_fixture(root)
            command = ('collect', 'run', '--execution-profile', 'HARDENED_WORKER')
            completed = self.cli(root, *command)
            self.assertEqual(0, completed.returncode, completed.stderr)
            directory = run / 'native-embedded-font-kinds/visual'
            for name, before, after in (
                    ('result.properties', 'visual=pass', 'visual=fail'),
                    ('page-1-visual.md', 'Producer: `pdfium-cli`', 'Producer: `other`'),
                    ('page-1-visual.md', 'Expected raster SHA-256: `', 'Different raster: `'),
                    ('page-1-visual.txt', 'Exit code: `0`', 'Exit code: `137`'),
                    ('page-1-visual.txt', 'Renderer agreement threshold: `1120`', 'Renderer agreement threshold: `9999`'),
                    ('font-raster-agreement.properties', 'outside-edge-pixels=0', 'outside-edge-pixels=1')):
                path = directory / name
                original = path.read_text()
                self.assertIn(before, original)
                path.write_text(original.replace(before, after))
                for parent in (directory, directory.parent, run):
                    seal(parent)
                self.assertNotEqual(0, self.cli(root, *command).returncode, name + ': ' + before)
                path.write_text(original)
                for parent in (directory, directory.parent, run):
                    seal(parent)

    def test_public_collect_requires_detected_truncated_syntax_and_invalid_catalog_controls(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = self.report_fixture(root)
            command = ('collect', 'run', '--execution-profile', 'HARDENED_WORKER')
            completed = self.cli(root, *command)
            self.assertEqual(0, completed.returncode, completed.stderr)
            for directory, name, before, after in (
                    ('syntax', 'result.properties', 'syntax=fail', 'syntax=pass'),
                    ('syntax', 'qpdf-syntax.txt', 'exit code: `2`', 'exit code: `137`'),
                    ('standards', 'result.properties', 'declarations=fail', 'declarations=indeterminate'),
                    ('standards', 'pdfcpu/findings.txt', 'Validation exit: 1', 'Validation exit: 137'),
                    ('standards', 'arlington-core/findings.txt', 'and is name==Bogus', 'no invalid value')):
                folder = run / 'negative' / directory
                path = folder / name
                original = path.read_text()
                self.assertIn(before, original)
                path.write_text(original.replace(before, after))
                for parent in (folder, folder.parent, run):
                    seal(parent)
                self.assertNotEqual(0, self.cli(root, *command).returncode, directory + '/' + name)
                path.write_text(original)
                for parent in (folder, folder.parent, run):
                    seal(parent)

    def test_public_collect_requires_every_source_bound_semantic_negative_and_positive(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = self.report_fixture(root)
            command = ('collect', 'run', '--execution-profile', 'HARDENED_WORKER')
            completed = self.cli(root, *command)
            self.assertEqual(0, completed.returncode, completed.stderr)
            folder = run / 'negative/semantic'
            for name, before, after in (
                    ('result.properties', 'control-count=12', 'control-count=11'),
                    ('positive-marked-structure/result.properties', 'semantic=pass', 'semantic=fail'),
                    ('namespace-reference/result.properties', 'semantic=fail', 'semantic=indeterminate'),
                    ('namespace-reference/semantic-command.txt', 'exit-code: 0', 'exit-code: 137'),
                    ('namespace-reference/extraction.pdf', '/S /Span /NS 14 0 R', '/S /Span /NS 15 0 R')):
                path = folder / name
                original = path.read_bytes()
                self.assertIn(before.encode(), original)
                path.write_bytes(original.replace(before.encode(), after.encode()))
                parents = ([path.parent] if '/' in name else []) + [folder, folder.parent, run]
                for parent in parents:
                    seal(parent)
                self.assertNotEqual(0, self.cli(root, *command).returncode, name)
                path.write_bytes(original)
                for parent in parents:
                    seal(parent)

    def test_public_plan_binds_all_eight_native_combinations_and_actual_facade_mode(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            helper = root / 'helper'
            helper.write_text('#!/bin/sh\nexit 0\n')
            helper.chmod(0o700)
            (root / 'capabilities').mkdir()
            (root / 'capabilities/foundation-release.yaml').write_text(yaml.safe_dump({
                'release': '0.1.0', 'required-artifacts': [], 'environments': 'capabilities/environments.yaml'}))
            shutil.copyfile(ROOT / 'capabilities/foundation-environments.yaml', root / 'capabilities/environments.yaml')
            (root / 'target/foundation-0.1.0').mkdir(parents=True)
            (root / 'target/foundation-0.1.0/build-inputs.json').write_text(json.dumps({'harness': []}))
            result = subprocess.run(['/usr/bin/python3', str(ROOT / 'scripts/t03-foundation.py'),
                                     'plan', 'plan', '--root', str(root), '--obligation', 'text'],
                                    env=dict(os.environ, FOLIO_HARFBUZZ_HELPER=str(helper)),
                                    capture_output=True, text=True, timeout=30)
            self.assertEqual(0, result.returncode, result.stderr)
            plan = json.loads((root / 'plan/plan.json').read_text())
            self.assertEqual('unverified-plan', plan['status'])
            self.assertEqual(8, len(plan['executions']))
            tools = {tool['id']: tool for tool in plan['tools']}
            self.assertEqual('scripts/t13-arlington-pin.properties', tools['arlington-t13-r1']['pin'])
            self.assertEqual('project-test', tools['folio-pdf-t13']['kind'])
            self.assertEqual({'syntax', 'standards', 'semantic'}, set(tools['qpdf']['chains']))
            for index, execution in enumerate(plan['executions']):
                mode = 'IN_PROCESS' if index % 2 == 0 else 'HARDENED_WORKER'
                self.assertEqual(126, execution['required-test-count'])
                self.assertIn('-Dfolio.t13.executionProfile=' + mode, execution['java-options'])
                self.assertIn('net.zerocloud.pdf.acceptance.T13EvidenceCommand', execution['recorder-command'])
                self.assertIn('net.zerocloud.pdf.consumer.TextStructureExtractionWorkflowTest',
                              execution['contract-tests-command'])
                self.assertIn('net.zerocloud.pdf.itext7.consumer.TextStructureFacadeTest',
                              execution['contract-tests-command'])
                self.assertIn('Stable Facade execution is IN_PROCESS', execution['settings']['providers'])
            for path in ('capabilities/profiles/T13-text', 'capabilities/profiles/T13-fonts',
                         'capabilities/profiles/T13-standards', 'scripts/t13-arlington-pin.properties',
                         'scripts/t13-program-standards.py', 'scripts/t13-semantics.py',
                         'scripts/t13-qpdf-runtime.sha256', 'scripts/t13_foundation_reports.py'):
                self.assertIn(path, plan['configuration-paths'])

    def test_public_collect_rejects_visual_versions_that_contradict_original_stdout(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = self.report_fixture(root)
            command = ('collect', 'run', '--execution-profile', 'HARDENED_WORKER')
            completed = self.cli(root, *command)
            self.assertEqual(0, completed.returncode, completed.stderr)
            directory = run / 'native-embedded-font-kinds/visual'
            path = directory / 'page-1-visual.txt'
            original = path.read_text()
            for before, after in (
                    ('pdfium version v0.11.2', 'pdfium version v0.0.0'),
                    ('Version: ImageMagick 7.1.2-30 ', 'Version: ImageMagick 0.0.0 '),
                    ('Rendered page 1 into page-1-pdfium.png', 'Rendered another input')):
                self.assertIn(before, original)
                path.write_text(original.replace(before, after))
                for parent in (directory, directory.parent, run):
                    seal(parent)

                self.assertNotEqual(0, self.cli(root, *command).returncode, before)
                path.write_text(original)
                for parent in (directory, directory.parent, run):
                    seal(parent)

    def test_public_collect_preserves_authority_identity_through_final_export(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = self.report_fixture(root)
            authority = root / 'capabilities/profiles/T13-text/corpus.json'
            last_observation = run / 'facade-uncertain-geometry/semantic/qpdf/observed.json'
            os.utime(authority, ns=(0, authority.stat().st_mtime_ns))
            command = ['/usr/bin/python3', str(ROOT / 'scripts/t03-foundation.py'),
                       'collect', 'run', '--execution-profile', 'HARDENED_WORKER',
                       '--root', str(root), '--obligation', 'text']
            process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            armed = changed = False
            try:
                deadline = time.monotonic() + 60
                while process.poll() is None and time.monotonic() < deadline:
                    if not armed and authority.stat().st_atime_ns != 0:
                        # Initial receipt verification has already read every artifact.
                        os.utime(last_observation, ns=(0, last_observation.stat().st_mtime_ns))
                        armed = True
                    elif armed and last_observation.stat().st_atime_ns != 0:
                        # The last semantic observation follows the last qpdf pin read.
                        with (root / 'scripts/qpdf-pin.properties').open('a') as pin:
                            pin.write('# changed after original authority observation\n')
                        changed = True
                        break
                    time.sleep(0.001)
                stdout, stderr = process.communicate(timeout=30)
            finally:
                if process.poll() is None:
                    process.kill()
                    process.communicate()
            self.assertTrue(armed and changed, 'The authority must actually change after its last observation')
            self.assertNotEqual(0, process.returncode, 'Changed original authority was accepted: ' + stderr)

    def test_public_collect_requires_every_visual_and_secondary_raster_control(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = self.report_fixture(root)
            command = ('collect', 'run', '--execution-profile', 'HARDENED_WORKER')
            completed = self.cli(root, *command)
            self.assertEqual(0, completed.returncode, completed.stderr)
            folder = run / 'negative/visual'
            for name, before, after in (
                    ('result.properties', 'raster-control-count=3', 'raster-control-count=2'),
                    ('positive/result.properties', 'visual=pass', 'visual=indeterminate'),
                    ('vertical-font-position/result.properties', 'visual=fail', 'visual=pass'),
                    ('vertical-font-position/page-1-visual.txt', 'Exit code: `1`', 'Exit code: `137`'),
                    ('raster/missing-glyph/result.properties', 'outside-edge-pixels=308', 'outside-edge-pixels=0'),
                    ('raster/edge-only/result.properties', 'raster-agreement=pass', 'raster-agreement=fail')):
                path = folder / name
                original = path.read_text()
                self.assertIn(before, original)
                path.write_text(original.replace(before, after))
                parents = ([path.parent] if '/' in name and not name.startswith('raster/') else []) + [folder, folder.parent, run]
                for parent in parents:
                    seal(parent)
                self.assertNotEqual(0, self.cli(root, *command).returncode, name)
                path.write_text(original)
                for parent in parents:
                    seal(parent)

    def test_public_collect_requires_named_raster_defects_in_original_pixels(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = self.report_fixture(root)
            command = ('collect', 'run', '--execution-profile', 'HARDENED_WORKER')
            completed = self.cli(root, *command)
            self.assertEqual(0, completed.returncode, completed.stderr)
            batch = run / 'negative/visual'
            control = batch / 'raster/missing-glyph'
            shutil.copyfile(batch / 'raster/edge-only/secondary.png', control / 'secondary.png')
            report = read_props(control / 'result.properties')
            report['secondary-sha256'] = digest(control / 'secondary.png')
            props(control / 'result.properties', report)
            for parent in (batch, batch.parent, run):
                seal(parent)
            self.assertNotEqual(0, self.cli(root, *command).returncode,
                                'Edge-only pixels were accepted as the missing-glyph negative control')

    def test_public_collect_requires_opaque_raster_controls(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = self.report_fixture(root)
            command = ('collect', 'run', '--execution-profile', 'HARDENED_WORKER')
            completed = self.cli(root, *command)
            self.assertEqual(0, completed.returncode, completed.stderr)
            batch = run / 'negative/visual'
            control = batch / 'raster/edge-only'
            path = control / 'secondary.png'
            original = path.read_bytes()
            # A legal RGB8 tRNS chunk makes black ink transparent without
            # changing any decoded RGB samples or the claimed edge-only defect.
            payload = b'tRNS' + b'\x00' * 6
            chunk = (6).to_bytes(4, 'big') + payload + zlib.crc32(payload).to_bytes(4, 'big')
            path.write_bytes(original[:33] + chunk + original[33:])
            report = read_props(control / 'result.properties')
            report['secondary-sha256'] = digest(path)
            props(control / 'result.properties', report)
            for parent in (batch, batch.parent, run):
                seal(parent)
            self.assertNotEqual(0, self.cli(root, *command).returncode,
                                'Transparent black ink was accepted as an opaque edge-only positive')
