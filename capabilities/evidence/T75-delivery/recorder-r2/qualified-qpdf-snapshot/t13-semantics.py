#!/usr/bin/env python3
"""Compare frozen T13 Sources and rewrite products through pinned qpdf graphs.

This independent acceptance observer imports neither Folio nor its fixture
generator. It preserves decoded content bytes and reachable reference topology;
only serialization details and the declared output version are normalized.
Public extraction values are checked separately against the literal corpus.
Apache-2.0, Folio PDF by ZeroCloud contributors.
"""
import base64
from decimal import Decimal
import hashlib
import json
from pathlib import Path
import platform
import re
import resource
import subprocess
import sys

PROFILE = 'T13-text-logical-structure'
CORPUS_SHA256 = 'fb348a2b139df12b93633cbf9c999490c584030aebeacc9b95bff57c9982707f'
QPDF_BINARY_SHA256 = '9ac787a28597e8428289a12ba3fedafd74bdfb4b4da1be814722faf76f14f21b'
QPDF_WRAPPER_SHA256 = 'a12d5a4e48fd37e8aefa3b92b30f002c25d2de9f96944fb68efeb64f2b79431e'
QPDF_PIN_SHA256 = '62c63de3d888ba08b60df9e3c33c9ebefe4cc0c8ee747cc5269390017b57169c'
QPDF_RUNTIME_SHA256 = 'a06ee3eb9314e2fa3db4fb475d806f8249f82e0daea85528535c04362fa280ad'
MAX_BYTES = 16 * 1024 * 1024
REFERENCE = re.compile(r'^[1-9][0-9]* [0-9]+ R$')


class Mismatch(Exception):
    """A readable decoded graph differs from the frozen Source."""


def require(condition, field):
    if not condition:
        raise Mismatch(field)


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def bounded_bytes(path):
    if path.stat().st_size > MAX_BYTES:
        raise OSError('T13 input or declaration exceeds its acceptance bound')
    data = path.read_bytes()
    if len(data) > MAX_BYTES:
        raise OSError('T13 input grew beyond its acceptance bound')
    return data


def qpdf_runtime(root, binary):
    manifest = bounded_bytes(root / 'scripts/t13-qpdf-runtime.sha256')
    if sha256(manifest) != QPDF_RUNTIME_SHA256:
        raise OSError('Pinned qpdf runtime manifest identity mismatch')
    for line in manifest.decode('ascii').splitlines():
        expected, name = line.split('  ', 1)
        if sha256(bounded_bytes(binary.parent.parent / name)) != expected:
            raise OSError('Pinned qpdf runtime library identity mismatch')
    return QPDF_RUNTIME_SHA256


def canonical(graph, expected_version, page_count):
    require(graph['version'] == 2 and graph['qpdf'][0]['jsonversion'] == 2, 'qpdf JSON schema')
    source = graph['qpdf'][1]
    root = source['trailer']['value']['/Root']
    require(isinstance(root, str) and REFERENCE.fullmatch(root), 'indirect Catalog')
    catalog = source['obj:' + root]['value']
    require(catalog.get('/Type') == '/Catalog', 'Catalog Type')
    version = max(Decimal(graph['qpdf'][0]['pdfversion']), Decimal(catalog.get('/Version', '/1.0')[1:]))
    require(version in (Decimal(expected_version), Decimal('2.0')), 'declared Source or rewrite PDF version')
    require(len(graph['pages']) == page_count, 'page count')
    identities, pending, objects = {}, [], []

    def convert(value, depth=0):
        require(depth < 128, 'bounded direct-container nesting')
        if isinstance(value, str) and REFERENCE.fullmatch(value):
            require('obj:' + value in source, 'live indirect reference')
            if value not in identities:
                require(len(identities) < 10000, 'bounded reachable object graph')
                identities[value] = len(pending) + 1
                pending.append(value)
            return {'reference': identities[value]}
        if isinstance(value, dict):
            return {key: convert(value[key], depth + 1) for key in sorted(value)}
        if isinstance(value, list):
            return [convert(item, depth + 1) for item in value]
        if isinstance(value, (int, Decimal)) and not isinstance(value, bool):
            return {'number': str(Decimal(value).normalize())}
        require(value is None or isinstance(value, (bool, str)), 'known qpdf scalar type')
        return value

    result = {'catalog': convert(root), 'objects': objects}
    # Source-only Queries followed by rewrite must preserve any Info dictionary.
    if '/Info' in source['trailer']['value']:
        result['info'] = convert(source['trailer']['value']['/Info'])
    index = 0
    while index < len(pending):
        reference = pending[index]
        item = source['obj:' + reference]
        if 'stream' in item:
            stream = item['stream']
            data = base64.b64decode(stream['data'], validate=True)
            require(len(data) <= MAX_BYTES, 'bounded decoded stream')
            # qpdf removes filters it decoded. A remaining filter means this
            # observer has no qualified decoded representation for that stream.
            require('/Filter' not in stream['dict'] and '/DecodeParms' not in stream['dict'], 'fully decoded stream')
            dictionary = {key: value for key, value in stream['dict'].items() if key != '/Length'}
            objects.append({'stream': {'dictionary': convert(dictionary), 'hex': data.hex()}})
        else:
            value = item['value']
            if reference == root:
                value = {key: value for key, value in value.items() if key != '/Version'}
            objects.append({'value': convert(value)})
        index += 1
    return result


def run_process(arguments, output, stem):
    def limits():
        resource.setrlimit(resource.RLIMIT_FSIZE, (MAX_BYTES, MAX_BYTES))
    with (output / (stem + '.txt')).open('wb') as stdout, (output / (stem + '-stderr.txt')).open('wb') as stderr:
        result = subprocess.run(arguments, stdout=stdout, stderr=stderr, timeout=30, preexec_fn=limits)
    if result.returncode != 0 or (output / (stem + '-stderr.txt')).stat().st_size:
        raise OSError('Pinned qpdf observation did not complete without findings')
    return bounded_bytes(output / (stem + '.txt'))


def properties(path, values):
    def escaped(value):
        return str(value).replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '\\r')
    path.write_text(''.join(key + '=' + escaped(value) + '\n' for key, value in sorted(values.items())), encoding='utf-8')


def inspect(root, input_pdf, product, output):
    output.mkdir()
    before = sha256(bounded_bytes(input_pdf))
    result = {'profile': PROFILE, 'semantic': 'indeterminate', 'input-sha256': before,
              'python-version': platform.python_version(), 'observer-sha256': sha256(Path(__file__).read_bytes()),
              'python-executable-sha256': sha256(Path(sys.executable).resolve().read_bytes()),
              'semantic-scope': 'independent-qpdf-decoded-graph-preservation'}
    reference_path = None
    reference_hash = None
    try:
        authority = root / 'capabilities/profiles/T13-text/corpus.json'
        corpus_bytes = bounded_bytes(authority)
        if sha256(corpus_bytes) != CORPUS_SHA256:
            raise OSError('Frozen T13 corpus identity mismatch')
        result['expectations-sha256'] = CORPUS_SHA256
        corpus = json.loads(corpus_bytes)
        definition = corpus['products'][product]
        source = corpus['sources'][definition['source']]
        reference_path = authority.parent / source['path']
        reference_hash = source['sha256']
        if sha256(bounded_bytes(reference_path)) != reference_hash:
            raise OSError('Frozen T13 Source identity mismatch')
        result['reference-sha256'] = reference_hash
        pin_path = root / 'scripts/qpdf-pin.properties'
        if sha256(bounded_bytes(pin_path)) != QPDF_PIN_SHA256:
            raise OSError('Pinned qpdf authority identity mismatch')
        pin = dict(line.split('=', 1) for line in pin_path.read_text().splitlines()
                   if line and not line.startswith('#'))
        if pin['QPDF_VERSION'] != '12.4.0' or pin['QPDF_BINARY_SHA256'] != QPDF_BINARY_SHA256:
            raise OSError('Pinned qpdf authority identity mismatch')
        executable = root / 'scripts' / pin['QPDF_EXECUTABLE']
        if executable != root / 'scripts/container-bin/qpdf':
            raise OSError('Unqualified qpdf executable')
        if sha256(bounded_bytes(executable.parent / '..' / 'qpdf-pin.properties')) != QPDF_PIN_SHA256:
            raise OSError('The pin actually sourced by the qpdf wrapper has an unqualified identity')
        if sha256(executable.read_bytes()) != QPDF_WRAPPER_SHA256:
            raise OSError('Observed qpdf wrapper identity mismatch')
        # The wrapper verifies distribution markers; this observer additionally
        # checks the actual binary bytes, including a configured local cache.
        import os
        cache = Path(os.environ.get('QPDF_CACHE_DIRECTORY') or str(root / '.build-cache/qpdf'))
        binary = cache / '12.4.0/bin/qpdf'
        if sha256(binary.read_bytes()) != QPDF_BINARY_SHA256:
            raise OSError('Observed qpdf binary identity mismatch')
        result['qpdf-runtime-sha256'] = qpdf_runtime(root, binary)
        result['qpdf-wrapper-sha256'] = sha256(executable.read_bytes())
        version = run_process([str(executable), '--version'], output, 'qpdf-version').decode('ascii')
        if not version.startswith('qpdf version 12.4.0\n'):
            raise OSError('Observed qpdf version mismatch')
        result['qpdf-version'] = '12.4.0'
        result['qpdf-binary-sha256'] = QPDF_BINARY_SHA256
        graphs = []
        for pdf, stem, name in ((reference_path, 'reference-qpdf', 'reference'), (input_pdf, 'qpdf', 'observed')):
            data = run_process([str(executable), '--json=2', '--json-stream-data=inline', '--decode-level=all', str(pdf)],
                               output, stem)
            (output / (stem + '.txt')).rename(output / (stem + '.json'))
            result[stem + '-json-sha256'] = sha256(data)
            observed = canonical(json.loads(data, parse_float=Decimal), source['pdf-version'],
                                 len(definition['extraction']['pages']))
            (output / (name + '.json')).write_text(json.dumps(observed, indent=2, sort_keys=True) + '\n')
            graphs.append(observed)
        require(graphs[0] == graphs[1], 'reachable dictionaries, decoded programs or reference relationships')
        result.update(semantic='pass', finding='The complete reachable decoded graph matches the frozen original Source.')
        if sha256(bounded_bytes(authority)) != CORPUS_SHA256:
            raise OSError('Frozen T13 corpus changed during observation')
    except (Mismatch, KeyError, TypeError, ValueError, IndexError, UnicodeError) as failure:
        result.update(semantic='fail', finding='Frozen semantic mismatch: ' + str(failure))
    except (OSError, subprocess.TimeoutExpired) as failure:
        result.update(semantic='indeterminate', finding=str(failure))
    try:
        if 'qpdf-binary-sha256' in result:
            if (sha256(bounded_bytes(pin_path)) != QPDF_PIN_SHA256
                    or sha256(bounded_bytes(executable.parent / '..' / 'qpdf-pin.properties')) != QPDF_PIN_SHA256
                    or sha256(bounded_bytes(executable)) != QPDF_WRAPPER_SHA256
                    or sha256(bounded_bytes(binary)) != QPDF_BINARY_SHA256
                    or sha256(bounded_bytes(Path(__file__))) != result['observer-sha256']):
                raise OSError('Input checker or qpdf identity changed during semantic observation')
            qpdf_runtime(root, binary)
    except OSError as failure:
        result.update(semantic='indeterminate', finding=str(failure))
    if sha256(bounded_bytes(input_pdf)) != before:
        result.update(semantic='indeterminate', finding='Input changed during independent semantic observation')
    if reference_path is not None and sha256(bounded_bytes(reference_path)) != reference_hash:
        result.update(semantic='indeterminate', finding='Original Source changed during independent semantic observation')
    properties(output / 'result.properties', result)
    (output / 'semantic.txt').write_text('Input exact SHA-256: ' + before + '\n' + result['finding'] + '\n')


if __name__ == '__main__':
    if len(sys.argv) != 5:
        raise SystemExit('Usage: t13-semantics.py <repository> <input.pdf> <product> <fresh-output>')
    inspect(Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve(), sys.argv[3], Path(sys.argv[4]).resolve())
