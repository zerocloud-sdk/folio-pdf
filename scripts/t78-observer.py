#!/usr/bin/env python3
"""Independent, bounded T78 observations; never imports the product or authoring code.

Commands and tool streams have a frozen privacy projection. Ciphertext products
keep their actual hashes; credential entries, plaintext and private paths are
never retained as reports. Collection replays each tool observation and requires
byte-for-byte equality of this projection, including all negative controls.
"""
import base64
from contextlib import contextmanager
import ctypes
from decimal import Decimal
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import selectors
import signal
import stringprep
import subprocess
import sys
import tempfile
import time
import unicodedata

PROFILE = 'T32-password-baseline'
MAX_BYTES = 16 * 1024 * 1024
PYTHON_SHA256 = '1643dacd9feaedc58f3cc581e4d22577dfe25c09b10282936186ccf0f2e61118'
# A non-exec supervisor survives tool forks (notably AppImage extraction).
# Save stdin before the shell applies its asynchronous-command /dev/null rule.
SUPERVISOR = ('trap \'kill -KILL "-$$"\' TERM INT HUP; exec 3<&0; '
              '"$@" <&3 3<&- & child=$!; exec 3<&- 0<&-; wait "$child"; result=$?; exit "$result"')
_cancelled = False


@contextmanager
def cancellation_scope():
    """Unwind private files and the active tool before honoring coordinator termination."""
    global _cancelled
    previous, prior_cancelled = signal.getsignal(signal.SIGTERM), _cancelled
    _cancelled = False
    def cancel(signum, frame):
        global _cancelled
        _cancelled = True
    signal.signal(signal.SIGTERM, cancel)
    try:
        yield
    finally:
        signal.signal(signal.SIGTERM, previous)
        _cancelled = prior_cancelled


def check_cancellation():
    if _cancelled:
        raise InterruptedError('Independent observation cancelled')


def child_guard():
    # This acceptance profile is Linux-only. The single-threaded coordinator
    # gives the group supervisor a parent-death signal, including forced exit.
    require(sys.platform == 'linux', 'The independent observer requires Linux')
    parent, libc = os.getpid(), ctypes.CDLL(None, use_errno=True)
    def guard():
        # Do not inherit the coordinator's deferred cancellation handler in the
        # interval before exec installs the shell's process-group cleanup trap.
        signal.signal(signal.SIGTERM, signal.SIG_DFL)
        if libc.prctl(1, signal.SIGTERM, 0, 0, 0) != 0 or os.getppid() != parent:
            os._exit(125)
    return guard


def stop_tool(process):
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass
    process.wait(timeout=5)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def json_bytes(value):
    return (json.dumps(value, sort_keys=True, indent=2) + '\n').encode()


def credential(case, owner=False):
    kind = case.get('credential', 'ordinary')
    if owner:
        value = '' if kind in ('empty-owner', 'input-empty-owner') else 'equal-baseline' if kind == 'equal' else 'baseline-owner'
    else:
        value = {'empty-user': '', 'equal': 'equal-baseline', 'unicode': 'I\u00adX\u00a0\u2168',
                 'unicode-127': 'a' * 127, 'unicode-split': 'a' * 126 + '\u00e9',
                 'bidi': '\u05d0\u05d1', 'legacy-original-32': '\u0080' + '\u00e9' * 32,
                 'long': 'a' * 128, 'legacy-32': '\u00e9' * 33}.get(kind, 'baseline-user')
    if case.get('algorithm', 'AES_256') in ('RC4_40', 'RC4_128', 'AES_128'):
        return value.encode('latin1')
    # Independently selected RFC3454 Unicode3.2 tables; ICU/product code is absent.
    mapped = ''.join(' ' if stringprep.in_table_c12(c) else c for c in value if not stringprep.in_table_b1(c))
    prepared = unicodedata.ucd_3_2_0.normalize('NFKC', mapped)
    require(not any(any(table(c) for table in (stringprep.in_table_c12, stringprep.in_table_c21,
            stringprep.in_table_c22, stringprep.in_table_c3, stringprep.in_table_c4, stringprep.in_table_c5,
            stringprep.in_table_c6, stringprep.in_table_c7, stringprep.in_table_c8, stringprep.in_table_c9)) for c in prepared),
            'Authored credential violates RFC4013')
    if any(stringprep.in_table_d1(c) for c in prepared):
        require(not any(stringprep.in_table_d2(c) for c in prepared)
                and stringprep.in_table_d1(prepared[0]) and stringprep.in_table_d1(prepared[-1]), 'Authored credential violates bidi rules')
    return prepared.encode('utf8')[:127]


def expected_public(case):
    encrypted = case['algorithm'] != 'NONE'
    return {'version': case['version'], 'pages': str(case['pages']), 'encrypted': str(encrypted).lower(),
            'mask': str(case['mask']), 'owner': str(not encrypted or case['credential'] == 'equal').lower(),
            'algorithm': case['algorithm']}


class Observations:
    def __init__(self, root, output, replay=False):
        self.root, self.output, self.replay = root.resolve(), output.resolve(), replay
        self.files = {}
        self.qpdf = self.root / 'scripts/container-bin/t78-qpdf'
        self.pdfium = self.root / 'scripts/container-bin/pdfium'
        self.magick = self.root / 'scripts/container-bin/imagemagick'
        self.arlington = self.root / '.build-cache/arlington/fe4a1a8/TestGrammar/bin/linux/TestGrammar'
        self.pdfcpu = self.root / '.build-cache/pdfcpu/0.15.0/pdfcpu'
        self.security_pdfcpu = self.root / '.build-cache/pdfcpu/t78-r1/pdfcpu'
        self.model = self.root / '.build-cache/t78-arlington-model'

    def emit(self, relative, data):
        if not isinstance(data, bytes):
            data = json_bytes(data)
        target = self.output / relative
        if self.replay:
            require(target.is_file() and not target.is_symlink() and target.read_bytes() == data,
                    'Retained observation differs from independent replay: ' + relative)
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            with target.open('xb') as stream:
                stream.write(data)
        self.files[relative] = digest(data)

    def read(self, path):
        require(path.is_file() and not path.is_symlink() and path.stat().st_size <= MAX_BYTES, 'Missing, linked or oversized observation input')
        data = path.read_bytes()
        require(len(data) <= MAX_BYTES, 'Observation input grew beyond its bound')
        return data

    def logical(self, value):
        value = str(value)
        if value.startswith(str(self.output) + '/'):
            return '@evidence/' + value[len(str(self.output)) + 1:]
        if value.startswith(str(self.root) + '/'):
            return '@repository/' + value[len(str(self.root)) + 1:]
        return value

    def process(self, command, relative, stdin=None, secrets=(), temporary=(), projection=None, timeout=30):
        check_cancellation()
        command = [str(item) for item in command]
        normalized = []
        for value in command:
            if value in secrets:
                normalized.append('@credential')
            elif any(str(path) in value for path in temporary):
                selected = value
                for index, path in enumerate(temporary):
                    selected = selected.replace(str(path), '@private-' + str(index))
                normalized.append(selected)
            elif value == sys.executable:
                normalized.append('@observer-python')
            else:
                normalized.append(self.logical(value))
        start, captured, limited = time.monotonic(), {1: bytearray(), 2: bytearray()}, None
        invocation = ['/bin/sh', '-c', SUPERVISOR, 'folio-t78-tool'] + command
        process = subprocess.Popen(invocation, cwd=self.root, stdin=subprocess.PIPE if stdin is not None else subprocess.DEVNULL,
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                   start_new_session=True, preexec_fn=child_guard())
        try:
            if stdin is not None:
                require(len(stdin) <= 4096, 'Credential channel exceeds its bound')
                process.stdin.write(stdin)
                process.stdin.close()
            with selectors.DefaultSelector() as selector:
                selector.register(process.stdout, selectors.EVENT_READ, 1)
                selector.register(process.stderr, selectors.EVENT_READ, 2)
                while selector.get_map():
                    check_cancellation()
                    if time.monotonic() - start > timeout:
                        limited = 'time-limit'
                        break
                    for key, _ in selector.select(.1):
                        block = os.read(key.fileobj.fileno(), 65536)
                        if not block:
                            selector.unregister(key.fileobj)
                            continue
                        if sum(map(len, captured.values())) + len(block) > MAX_BYTES:
                            limited = 'byte-limit'
                            break
                        captured[key.data].extend(block)
                    if limited:
                        break
            if limited:
                stop_tool(process)
            code = process.wait(timeout=5)
        finally:
            stop_tool(process)
            if process.stdin is not None:
                process.stdin.close()
            process.stdout.close()
            process.stderr.close()
        raw_out, raw_err = bytes(captured[1]), bytes(captured[2])
        require(limited is None, 'External observation exceeded its bound')
        def safe(data):
            text = data.decode('utf8', errors='replace')
            # qpdf can disclose the user password even when checking with owner credentials.
            text = re.sub(r'(?im)^.*password\s*=.*$', '[credential redacted]', text)
            text = re.sub(r'<[0-9a-fA-F\s]{16,}>', '<bytes redacted>', text)
            for secret in secrets:
                if secret:
                    text = text.replace(secret, '[credential redacted]')
            for index, path in enumerate(temporary):
                text = text.replace(str(path), '@private-' + str(index))
            text = text.replace(str(self.output), '@evidence').replace(str(self.root), '@repository')
            return text.encode()
        out = json_bytes(projection(raw_out)) if projection is not None else safe(raw_out)
        err = safe(raw_err)
        self.emit(relative + '.stdout', out)
        self.emit(relative + '.stderr', err)
        self.emit(relative + '.command.json', {'command': normalized, 'exit-code': code, 'timeout-seconds': timeout,
            'supervisor': ['/bin/sh', '-c', SUPERVISOR, 'folio-t78-tool', '@tool-command'],
            'maximum-output-bytes': MAX_BYTES, 'limit': limited, 'stdin': 'credential-bytes' if stdin is not None else 'none',
            'privacy-projection': 'qpdf-security-graph-v1' if projection is not None else 'tool-text-v1',
            'stdout-sha256': digest(out), 'stderr-sha256': digest(err)})
        return code, raw_out, raw_err

    def qpdf_call(self, pdf, secret, options, relative, projection=None):
        with tempfile.TemporaryDirectory(prefix='folio-security-') as directory:
            password = Path(directory) / 'credential'
            password.write_bytes(secret.hex().encode())
            password.chmod(0o600)
            return self.process([self.qpdf, '--suppress-password-recovery', '--password-mode=hex-bytes',
                '--password-file=' + str(password)] + list(options) + [pdf], relative, temporary=(password,), projection=projection)

    def syntax(self, pdf, secret, directory):
        code, out, err = self.qpdf_call(pdf, secret, ['--check'], directory + '/qpdf')
        result = 'pass' if code == 0 and not err.strip() and b'No syntax or stream encoding errors found' in out else 'fail'
        self.emit(directory + '/result.json', {'chain': 'syntax', 'result': result, 'input-sha256': digest(self.read(pdf))})
        return result

    def pypdf(self, pdf, secret, directory):
        code, out, err = self.process([sys.executable, '-I', '-S', '-B', self.root / 'scripts/t78-pypdf-check.py', pdf],
                                     directory + '/pypdf', stdin=secret)
        require(code == 0 and not err.strip(), 'External credential observer was unavailable')
        value = json.loads(out)
        require(value['pypdf'] == '6.1.1' and value['pycryptodome'] == '3.23.0'
                and value['provider'] == ['pycryptodome', '3.23.0'], 'External cryptographic provider identity mismatch')
        return value

    def dictionary_standards(self, pdf, directory, output=False, secret=b'baseline-user'):
        # The Encrypt dictionary is plaintext, but Catalog extensions can be in
        # encrypted object streams. Each closed case has a known ASCII observer
        # credential; exact user/owner predicates are separately proved below.
        password = secret.decode('ascii')
        model = self.model / ('output' if output else 'input')
        code, out, err = self.process([self.arlington, '--tsvdir', model, '--force', 'exact', '--brief',
            '--no-color', '--password', password, '--pdf', pdf], directory + '/arlington', secrets=(password,))
        text = out.decode('utf8', errors='replace')
        require(code == 0 and not err.strip() and 'END' in text, 'Arlington could not process the security dictionary')
        findings = [line.strip() for line in text.splitlines()
                    if re.search(r'\b(Error|Warning):', line) or 'unknown key' in line]
        # Keep rule messages while suppressing value payloads and paths. The raw
        # stream has the same documented projection in process().
        safe_findings = [re.sub(r' and is .*$', ' [observed value redacted]', line)
                         .replace(str(self.root), '@repository').replace(str(self.output), '@evidence') for line in findings]
        self.emit(directory + '/arlington-findings.json', {'findings': safe_findings, 'result': 'fail' if findings else 'pass'})
        return findings

    @staticmethod
    def graph_projection(data):
        value = json.loads(data)
        objects = value['qpdf'][1]
        require(value['version'] == 2 and value['qpdf'][0]['jsonversion'] == 2 and len(objects) < 10000,
                'Unsupported or unbounded independent graph')
        def resolve(item):
            seen = set()
            while isinstance(item, str) and re.fullmatch(r'[0-9]+ [0-9]+ R', item):
                require(item not in seen and len(seen) < 64, 'Cyclic independent graph')
                seen.add(item)
                entry = objects['obj:' + item]
                item = entry['value'] if 'value' in entry else entry['stream']['dict']
            return item
        trailer = objects['trailer']['value']
        catalog = resolve(trailer['/Root'])
        encryption = resolve(trailer['/Encrypt']) if '/Encrypt' in trailer else {}
        filters = resolve(encryption.get('/CF', {}))
        std = resolve(filters.get('/StdCF', {}))
        def length(item):
            if isinstance(item, str) and item.startswith('b:'):
                return len(bytes.fromhex(item[2:]))
            if isinstance(item, str) and item.startswith('u:'):
                return len(item[2:].encode('utf8'))
            return None
        security = {name[1:]: resolve(encryption.get(name)) for name in
                    ('/Filter', '/V', '/R', '/Length', '/P', '/EncryptMetadata', '/StmF', '/StrF', '/EFF')}
        security.update({name[1:] + '-length': length(resolve(encryption.get(name))) for name in ('/O', '/U', '/OE', '/UE', '/Perms')})
        security['StdCF'] = {name[1:]: resolve(std.get(name)) for name in ('/CFM', '/Length', '/AuthEvent')}
        pages = []
        for page in value['pages']:
            dictionary = resolve(page['object'])
            content = []
            for reference in page['contents']:
                stream = objects['obj:' + reference]['stream']
                decoded = base64.b64decode(stream['data'], validate=True)
                require(len(decoded) < MAX_BYTES, 'Unbounded independent content stream')
                content.append(digest(decoded))
            pages.append({'media-box': resolve(dictionary.get('/MediaBox')), 'content-sha256': content})
        info = resolve(trailer.get('/Info', {}))
        title = info.get('/Title')
        title_bytes = bytes.fromhex(title[2:]) if isinstance(title, str) and title.startswith('b:') else title[2:].encode('utf8') if isinstance(title, str) and title.startswith('u:') else b''
        extensions = resolve(catalog.get('/Extensions', {}))
        adbe = resolve(extensions.get('/ADBE', {}))
        return {'security': security if encryption else None, 'catalog-version': catalog.get('/Version'),
                'extensions': {key: adbe.get(key) for key in ('/BaseVersion', '/ExtensionLevel')} if adbe else {},
                'pages': pages, 'title-sha256': digest(title_bytes)}

    def graph(self, pdf, secret, directory):
        code, out, err = self.qpdf_call(pdf, secret, ['--json=2', '--json-key=qpdf', '--json-key=pages',
            '--json-stream-data=inline', '--decode-level=all'], directory + '/qpdf', self.graph_projection)
        require(code == 0 and not err.strip(), 'Independent decrypted graph was unavailable')
        return self.graph_projection(out)

    def decrypted(self, pdf, secret, destination, directory):
        with tempfile.TemporaryDirectory(prefix='folio-security-') as folder:
            password = Path(folder) / 'credential'
            password.write_bytes(secret.hex().encode())
            password.chmod(0o600)
            code, _, err = self.process([self.qpdf, '--suppress-password-recovery', '--password-mode=hex-bytes',
                '--password-file=' + str(password), '--decrypt', '--deterministic-id', pdf, destination], directory + '/decrypt',
                temporary=(password, destination))
            require(code == 0 and not err.strip() and destination.is_file(), 'Independent decryption failed')
        self.emit(directory + '/decryption.json', {'input-sha256': digest(self.read(pdf)), 'decrypted-sha256': digest(self.read(destination))})

    def core_standards(self, pdf, directory, finding=None):
        code, out, err = self.process([self.pdfcpu, 'validate', '--mode', 'strict', '--conf', 'disable', '--offline', pdf],
                                      directory + '/pdfcpu', temporary=(pdf,))
        verdict = 'pass' if code == 0 and b'validation ok' in out + err else 'fail'
        if finding is not None:
            require(code != 0 and b'validation error' in out + err and finding.encode() in out + err,
                    'The required core standards control was not detected')
        self.emit(directory + '/result.json', {'result': verdict, 'input-sha256': digest(self.read(pdf))})
        return verdict

    def authentication(self, pdf, secret, directory, authority):
        code, out, err = self.qpdf_call(pdf, secret, ['--show-encryption'], directory + '/qpdf')
        expected = b'Supplied password is ' + authority.encode() + b' password'
        passed = code == 0 and not err.strip() and expected in out
        self.emit(directory + '/result.json', {'result': 'pass' if passed else 'fail',
            'authority': authority if passed else 'unproven', 'input-sha256': digest(self.read(pdf))})
        return passed

    def encrypted_core_standards(self, pdf, secret, directory, finding=None, owner=True):
        password = secret.decode('ascii')
        code, out, err = self.process([self.security_pdfcpu, 'validate', '--mode', 'strict', '--conf', 'disable', '--offline',
            '--opw' if owner else '--upw', password, pdf], directory + '/pdfcpu', secrets=(password,))
        verdict = 'pass' if code == 0 and b'validation ok' in out + err else 'fail'
        if finding is not None:
            require(code != 0 and finding.encode() in out + err, 'The encrypted dictionary control was not detected')
        self.emit(directory + '/result.json', {'result': verdict, 'input-sha256': digest(self.read(pdf))})
        return verdict

    def semantic(self, pdf, case, public, directory, secret=None):
        graph = self.graph(pdf, credential(case) if secret is None else secret, directory + '/graph')
        data = self.read(pdf)
        match = re.match(rb'%PDF-(1\.[0-7]|2\.0)\r?\n', data)
        header = match[1].decode() if match else None
        catalog = graph['catalog-version']
        version = max(header or '', catalog[1:] if isinstance(catalog, str) else '')
        failures = []
        if public != expected_public(case): failures.append('public-observation')
        if version != case['version']: failures.append('effective-version')
        pages = graph['pages']
        if len(pages) != case['pages']: failures.append('page-count')
        expected_content = digest(b'0 0 1 rg 12 16 24 20 re f\n')
        if not pages or pages[0] != {'media-box': [0, 0, 72, 72], 'content-sha256': [expected_content]}:
            failures.append('decrypted-page')
        if any(page != {'media-box': [0, 0, 612, 792], 'content-sha256': []} for page in pages[1:]):
            failures.append('blank-page')
        if graph['title-sha256'] != digest(b'Folio baseline proof'): failures.append('decrypted-string')
        security = graph['security']
        if case['algorithm'] == 'NONE':
            if security is not None: failures.append('unexpected-encryption')
        else:
            revision = case['revision']
            expected_v = case.get('v', {'RC4_40': 1, 'RC4_128': 2, 'AES_128': 4, 'AES_256': 5}[case['algorithm']])
            if security is None:
                failures.append('missing-encryption')
            else:
                if security['Filter'] != '/Standard' or security['R'] != revision or security['V'] != expected_v:
                    failures.append('algorithm-revision')
                if security['P'] != case['mask']: failures.append('permission-mask')
                if (security['Length'] or 40) != {'RC4_40': 40, 'RC4_128': 128, 'AES_128': 128, 'AES_256': 256}[case['algorithm']]:
                    failures.append('key-length')
                metadata = security['EncryptMetadata'] is not False
                if metadata != case.get('metadata', True): failures.append('metadata-scope')
                if expected_v >= 4:
                    expected_method = case.get('method', {'AES_128': 'AESV2', 'AES_256': 'AESV3'}.get(case['algorithm'], 'V2'))
                    if security['StmF'] != '/StdCF' or security['StrF'] != '/StdCF' or security['StdCF']['CFM'] != '/' + expected_method:
                        failures.append('content-scope')
                if revision >= 5 and version == '1.7' and graph['extensions'] != {
                        '/BaseVersion': '/1.7', '/ExtensionLevel': 3 if revision == 5 else 8}:
                    failures.append('extension-declaration')
            if b'Folio baseline proof' in data or b'0 0 1 rg 12 16 24 20 re f' in data:
                failures.append('plaintext-content')
        result = {'chain': 'semantic', 'result': 'fail' if failures else 'pass', 'findings': failures,
                  'input-sha256': digest(data), 'observation': public}
        self.emit(directory + '/result.json', result)
        return result['result']

    def compare(self, expected, actual, difference, directory):
        code, out, err = self.process([self.magick, 'compare', '-metric', 'AE', '-fuzz', '0%',
                                     '-define', 'png:exclude-chunk=date,time', expected, actual, difference],
                                     directory + '/compare', temporary=(actual, difference))
        metric = err.decode('ascii').strip()
        require(code in (0, 1) and not out.strip() and re.fullmatch(r'[0-9.eE+\-]+(?:\s+\([0-9.eE+\-]+\))?', metric),
                'Independent comparator did not report an exact AE metric')
        value = Decimal(metric.split()[0])
        require(value.is_finite() and value >= 0 and (code != 1 or value > 0), 'Comparator status conflicts with its measurement')
        self.emit(directory + '/difference.png', self.read(difference))
        return str(value)

    def visual(self, original, decrypted, pages, directory):
        results = []
        with tempfile.TemporaryDirectory(prefix='folio-visual-') as folder:
            for number in range(1, pages + 1):
                raster, difference = Path(folder) / 'actual.png', Path(folder) / 'difference.png'
                code, _, err = self.process([self.pdfium, 'render', decrypted, raster, '--dpi', '144', '--file-type', 'png',
                    '--pages', str(number), '--render-annotations'], directory + '/page-' + str(number) + '/render',
                    temporary=(decrypted, raster))
                require(code == 0 and not err.strip() and raster.is_file(), 'Independent raster was unavailable')
                expected = self.root / 'capabilities/profiles/T78-password' / ('expected.png' if number == 1 else 'blank.png')
                measured = self.compare(expected, raster, difference, directory + '/page-' + str(number))
                self.emit(directory + '/page-' + str(number) + '/actual.png', self.read(raster))
                results.append({'page': number, 'absolute-error': measured, 'expected-sha256': digest(self.read(expected)),
                                'raster-sha256': digest(self.read(raster))})
        verdict = 'pass' if all(Decimal(page['absolute-error']) == 0 for page in results) else 'fail'
        self.emit(directory + '/result.json', {'chain': 'visual', 'result': verdict, 'input-sha256': digest(self.read(original)),
                  'dpi': 144, 'metric': 'AE', 'fuzz': 0, 'threshold': 0, 'render-annotations': True, 'pages': results})
        return verdict
