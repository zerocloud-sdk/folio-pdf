#!/usr/bin/env python3
"""Scope-specific observations composed with the unchanged qualified T78 tools."""
import base64
import importlib.util
import json
from pathlib import Path
import re
import sys

SPEC = importlib.util.spec_from_file_location('t78_observations', Path(__file__).with_name('t78-observer.py'))
BASE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BASE)
PROFILE = 'T32-password-clear-metadata'
require, digest, credential = BASE.require, BASE.digest, BASE.credential
PYTHON_SHA256, Decimal, cancellation_scope = BASE.PYTHON_SHA256, BASE.Decimal, BASE.cancellation_scope


def expected_public(case, api='native'):
    value = BASE.expected_public(case)
    selector = {'RC4_128': 1, 'AES_128': 2, 'AES_256': 3}[case['algorithm']]
    clear = case.get('metadata', False) is False
    value.update(scope='ALL_EXCEPT_METADATA' if clear else 'ALL_CONTENT',
                 revision=str(case['revision']) if api == 'native' else 'not-exposed',
                 **{'crypto-mode': str(selector | (8 if clear else 0))})
    return value


class Observations(BASE.Observations):
    def __init__(self, root, output, replay=False):
        super().__init__(root, output, replay)
        self.model = self.root / '.build-cache/t79-arlington-model'

    def dictionary_standards(self, pdf, directory, output=False, secret=b'baseline-user', metadata=False):
        if metadata:
            baseline = BASE.Observations(self.root, self.output, self.replay)
            baseline.model = self.model / 'all-content'
            result = baseline.dictionary_standards(pdf, directory, output, secret)
            self.files.update(baseline.files)
            return result
        return super().dictionary_standards(pdf, directory, output, secret)

    @staticmethod
    def graph_projection(data):
        value = BASE.Observations.graph_projection(data)
        objects = json.loads(data)['qpdf'][1]
        def resolve(item):
            seen = set()
            while isinstance(item, str) and re.fullmatch(r'[0-9]+ [0-9]+ R', item):
                require(item not in seen and len(seen) < 64, 'Cyclic scope graph')
                seen.add(item)
                entry = objects['obj:' + item]
                item = entry['value'] if 'value' in entry else entry['stream']['dict']
            return item
        def stream_hash(reference):
            stream = objects['obj:' + reference]['stream']
            return digest(base64.b64decode(stream['data'], validate=True))
        def string_hash(item):
            return digest(bytes.fromhex(item[2:]) if item.startswith('b:') else item[2:].encode('utf8'))
        trailer = objects['trailer']['value']
        catalog = resolve(trailer['/Root'])
        names = resolve(resolve(catalog['/Names'])['/EmbeddedFiles'])['/Names']
        file = resolve(names[1])
        info = resolve(trailer['/Info'])
        value.update({'metadata-sha256': stream_hash(catalog['/Metadata']),
                      'embedded-file-sha256': stream_hash(resolve(file['/EF'])['/F']),
                      'keywords-sha256': string_hash(info['/Keywords'])})
        return value

    def byte_observation(self, pdf, secret, directory):
        code, out, err = self.process([sys.executable, '-I', '-S', '-B', self.root / 'scripts/t79-byte-check.py', pdf],
                                     directory + '/pypdf-bytes', stdin=secret)
        require(code == 0 and not err.strip(), 'Independent original-byte observer was unavailable')
        value = json.loads(out)
        require(value.get('pypdf') == '6.1.1', 'Original-byte parser identity mismatch')
        return value

    def semantic(self, pdf, case, public, directory, secret=None, api='native'):
        secret = credential(case) if secret is None else secret
        common = {key: public.get(key) for key in BASE.expected_public(case)}
        base_result = super().semantic(pdf, case, common, directory + '/baseline', secret)
        graph = self.graph(pdf, secret, directory + '/scope-graph')
        original = self.byte_observation(pdf, secret, directory + '/original-bytes')
        failures = []
        if base_result != 'pass': failures.append('common-security-contract')
        if public != expected_public(case, api): failures.append('scope-public-observation')
        expected_xmp = digest(self.read(self.root / 'capabilities/profiles/T79-clear-metadata/metadata.xmp'))
        if graph['metadata-sha256'] != expected_xmp: failures.append('metadata-packet')
        if graph['keywords-sha256'] != digest(b'Folio protected metadata-like Info value'): failures.append('info-keywords')
        if graph['embedded-file-sha256'] != digest(b'Folio protected embedded data: metadata-like values remain private.\n'):
            failures.append('embedded-file')
        if original.get('unauthenticated-metadata-matches') != (case.get('metadata', False) is False):
            failures.append('unauthenticated-metadata-bytes')
        for name in ('authenticated', 'content-matches', 'title-matches', 'keywords-match', 'embedded-file-matches',
                     'metadata-dictionary-string-matches', 'component-metadata-matches'):
            if original.get(name) is not True: failures.append(name)
        if original.get('diagnostic') != 'none': failures.append('original-byte-observer-diagnostic')
        if original.get('unauthenticated-protected-data-unavailable') != {
                'content': True, 'embedded-file': True, 'info-title': True, 'info-keywords': True,
                'component-metadata': True, 'metadata-dictionary-string': True}:
            failures.append('unprotected-content')
        result = {'chain': 'semantic', 'result': 'fail' if failures else 'pass', 'findings': failures,
                  'input-sha256': digest(self.read(pdf)), 'observation': public}
        self.emit(directory + '/result.json', result)
        return result['result']
