#!/usr/bin/env python3
"""Qualified EFF observations of original bytes using separately pinned tools."""
import importlib.util
import json
from pathlib import Path
import re
import sys

SPEC = importlib.util.spec_from_file_location('t78_observations', Path(__file__).with_name('t78-observer.py'))
BASE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BASE)
PROFILE = 'T32-password-embedded-files-only'
require, digest = BASE.require, BASE.digest
PYTHON_SHA256, Decimal, cancellation_scope = BASE.PYTHON_SHA256, BASE.Decimal, BASE.cancellation_scope


def credential(case, owner=False):
    return BASE.credential({**case, 'credential': 'empty-user' if case['credential'] == 'empty' else case['credential']}, owner)


def expected_public(case, api='native'):
    value = BASE.expected_public(case)
    value.update(scope='EMBEDDED_FILES_ONLY', revision=str(case['revision']) if api == 'native' else 'not-exposed',
                 **{'crypto-mode': str({'RC4_128': 1, 'AES_128': 2, 'AES_256': 3}[case['algorithm']] | 24)})
    return value


class Observations(BASE.Observations):
    def __init__(self, root, output, replay=False):
        super().__init__(root, output, replay)
        self.model = self.root / '.build-cache/t80-arlington-model'
        self.qpdf = self.root / 'scripts/container-bin/t80-qpdf'

    @staticmethod
    def graph_projection(data):
        graph = BASE.Observations.graph_projection(data)
        objects = json.loads(data)['qpdf'][1]
        def resolve(value):
            seen = set()
            while isinstance(value, str) and re.fullmatch(r'[0-9]+ [0-9]+ R', value):
                require(value not in seen and len(seen) < 64, 'Cyclic independent extension value')
                seen.add(value)
                entry = objects['obj:' + value]
                require('value' in entry, 'Extension value cannot be a stream')
                value = entry['value']
            return value
        graph['extensions'] = {name: resolve(value) for name, value in graph['extensions'].items()}
        return graph

    def byte_observation(self, pdf, secret, directory):
        code, out, err = self.process([sys.executable, '-I', '-S', '-B', self.root / 'scripts/t80-byte-check.py', pdf],
                                     directory + '/pypdf-bytes', stdin=secret)
        require(code == 0 and not err.strip(), 'Independent EFF observer was unavailable')
        value = json.loads(out)
        require(value.get('pypdf') == '6.1.1', 'Original EFF parser identity mismatch')
        return value

    def raw_dictionary(self, pdf, directory, output=False):
        code, out, err = self.process([sys.executable, '-I', '-S', '-B', self.root / 'scripts/t80-dictionary-check.py', pdf,
                                     'output' if output else 'input'], directory + '/pypdf-dictionary')
        require(code == 0 and not err.strip(), 'Independent original dictionary parser was unavailable')
        value = json.loads(out)
        require(value.get('pypdf') == '6.1.1' and value.get('unauthenticated') is True, 'Original dictionary identity mismatch')
        return value

    def clear_pages(self, pdf, destination, directory, paint=True):
        # This process receives no credential channel. Its parser copies the
        # original Identity page graph before any authenticated observations.
        code, out, err = self.process([sys.executable, '-I', '-S', '-B', self.root / 'scripts/t80-byte-check.py', pdf, destination],
                                     directory + '/pypdf-clear-copy', temporary=(destination,))
        require(code == 0 and not err.strip(), 'Independent unauthenticated page observer was unavailable')
        value = json.loads(out)
        require(value.get('pypdf') == '6.1.1' and value.get('unauthenticated-page-copy') is True
                and value.get('diagnostic') == 'none' and not value.get('authenticated'), 'Clear page copy required authentication')
        require(value.get('unauthenticated-page-matches') is paint, 'The original clear paint predicate disagrees')
        for key in ('unauthenticated-title-matches', 'unauthenticated-metadata-matches',
                    'attachment-ciphertext-differs', 'routes-consistent'):
            require(value.get(key) is True, 'An original EFF clear predicate failed: ' + key)
        self.emit(directory + '/result.json', {'result': 'pass', 'input-sha256': digest(self.read(pdf)),
                  'unauthenticated': True, 'predicates': value, 'page-copy-sha256': digest(self.read(destination))})

    def semantic(self, pdf, case, public, directory, secret=None, api='native'):
        secret = credential(case) if secret is None else secret
        graph = self.graph(pdf, secret, directory + '/security-graph')
        original = self.byte_observation(pdf, secret, directory + '/original-bytes')
        failures = []
        if public != expected_public(case, api): failures.append('scope-public-observation')
        security = graph['security']
        expected_v = case.get('v', 5 if case['algorithm'] == 'AES_256' else 4)
        bits = 256 if case['algorithm'] == 'AES_256' else 128
        method = case.get('method', {'RC4_128': 'V2', 'AES_128': 'AESV2', 'AES_256': 'AESV3'}[case['algorithm']])
        if security['Filter'] != '/Standard' or security['V'] != expected_v or security['R'] != case['revision']:
            failures.append('algorithm-revision')
        if security['P'] != case['mask'] or security['Length'] != bits: failures.append('permissions-or-length')
        if security['EncryptMetadata'] is not case.get('metadata', False): failures.append('metadata-key-and-perms')
        if security['StmF'] not in (None, '/Identity') or security['StrF'] not in (None, '/Identity'):
            failures.append('ordinary-default-routes')
        if security['StdCF']['CFM'] != '/' + method or security['StdCF']['Length'] not in (None, bits // 8):
            failures.append('embedded-filter-method')
        if security['StdCF']['AuthEvent'] not in (None, '/DocOpen', '/EFOpen'): failures.append('authentication-event')
        if graph['title-sha256'] != digest(b'Folio T80 clear document title'): failures.append('clear-info-title')
        expected_boxes = [[0, 0, 72, 72]] + [[0, 0, 612, 792]] * (case['pages'] - 1)
        if [page['media-box'] for page in graph['pages']] != expected_boxes:
            failures.append('clear-page-geometry')
        if any(page['content-sha256'] for page in graph['pages'][1:]):
            failures.append('clear-appended-page-content')
        if graph['pages'][0]['content-sha256'] != [digest(b'0 0 1 rg 12 16 24 20 re f\n')]: failures.append('clear-page-content')
        if case['revision'] >= 5 and case['version'] == '1.7' and graph['extensions'] != {
                '/BaseVersion': '/1.7', '/ExtensionLevel': 3 if case['revision'] == 5 else 8}:
            failures.append('extension-declaration')
        for name in ('authenticated', 'unauthenticated-page-matches', 'unauthenticated-title-matches',
                     'unauthenticated-metadata-matches', 'attachment-ciphertext-differs', 'routes-consistent', 'embedded-file-matches'):
            if original.get(name) is not True: failures.append(name)
        if original.get('authority') != (2 if case['credential'] == 'equal' else 1): failures.append('credential-authority')
        if original.get('diagnostic') != 'none': failures.append('original-byte-observer-diagnostic')
        result = {'chain': 'semantic', 'result': 'fail' if failures else 'pass', 'findings': failures,
                  'input-sha256': digest(self.read(pdf)), 'observation': public}
        self.emit(directory + '/result.json', result)
        return result['result']
