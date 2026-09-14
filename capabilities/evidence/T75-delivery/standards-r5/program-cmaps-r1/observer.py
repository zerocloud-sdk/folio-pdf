#!/usr/bin/env python3
"""Independent, bounded T13 program qualification over pinned qpdf objects.

This acceptance-only checker imports neither Folio nor the corpus authoring
code. Unsupported syntax remains unqualified. Apache-2.0 project code.
"""
import base64
from decimal import Decimal
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import resource
import signal
import subprocess
import sys

PROFILE = 'T13-text-logical-structure'
QPDF_SHA = '9ac787a28597e8428289a12ba3fedafd74bdfb4b4da1be814722faf76f14f21b'
WRAPPER_SHA = 'a12d5a4e48fd37e8aefa3b92b30f002c25d2de9f96944fb68efeb64f2b79431e'
MAX_BYTES = 16 * 1024 * 1024
REF = re.compile(r'^[1-9][0-9]* [0-9]+ R$')
SPACE = b'\x00\t\n\x0c\r '
DELIMITERS = SPACE + b'()<>[]{}/%'


class Invalid(Exception):
    def __init__(self, rule, finding):
        super().__init__(finding)
        self.rule = rule


class Unqualified(Exception):
    pass


class Name(str):
    pass


class Word(str):
    pass


class CMapDomain:
    def __init__(self, spaces, sources):
        self.spaces = spaces
        self.sources = sources


def require(condition, rule, finding):
    if not condition:
        raise Invalid(rule, finding)


def prefixes_overlap(low, high, other_low, other_high):
    return all(max(low[i], other_low[i]) <= min(high[i], other_high[i])
               for i in range(min(len(low), len(other_low))))


def digest(path):
    return hashlib.sha256(bounded(path)).hexdigest()


def bounded(path):
    if not path.is_file() or path.stat().st_size > MAX_BYTES:
        raise Unqualified('The acceptance input exceeds its byte bound')
    data = path.read_bytes()
    if len(data) > MAX_BYTES:
        raise Unqualified('The acceptance input grew beyond its byte bound')
    return data


class Tokens:
    """Literal PostScript objects for CMaps; executable names remain unevaluated."""
    def __init__(self, data):
        self.data = data
        self.offset = 0
        self.count = 0

    def skip(self):
        while self.offset < len(self.data):
            if self.data[self.offset] in SPACE:
                self.offset += 1
            elif self.data[self.offset] == 37:
                while self.offset < len(self.data) and self.data[self.offset] not in (10, 12, 13):
                    self.offset += 1
            else:
                break

    def all(self):
        result = []
        self.skip()
        while self.offset < len(self.data):
            result.append(self.value(0))
            self.skip()
        return result

    def value(self, depth):
        self.skip()
        self.count += 1
        if depth > 32 or self.count > 100000 or self.offset >= len(self.data):
            raise Unqualified('Program token/depth bound or incomplete lexical object')
        start = self.offset
        ch = self.data[start]
        self.offset += 1
        if ch == 40:
            result = bytearray()
            nesting = 1
            while self.offset < len(self.data):
                ch = self.data[self.offset]
                self.offset += 1
                if ch == 92:
                    if self.offset == len(self.data):
                        break
                    ch = self.data[self.offset]
                    self.offset += 1
                    if ch in (10, 13):
                        if ch == 13 and self.data[self.offset:self.offset + 1] == b'\n':
                            self.offset += 1
                        continue
                    if 48 <= ch <= 55:
                        octal = bytes([ch])
                        for unused in range(2):
                            if self.offset < len(self.data) and 48 <= self.data[self.offset] <= 55:
                                octal += self.data[self.offset:self.offset + 1]
                                self.offset += 1
                        result.append(int(octal, 8) % 256)
                    else:
                        result.append({110: 10, 114: 13, 116: 9, 98: 8, 102: 12}.get(ch, ch))
                    continue
                if ch == 40:
                    nesting += 1
                if ch == 41:
                    nesting -= 1
                    if nesting == 0:
                        return bytes(result)
                if ch in (10, 13):
                    if ch == 13 and self.data[self.offset:self.offset + 1] == b'\n':
                        self.offset += 1
                    ch = 10
                result.append(ch)
            raise Invalid('program-token', 'Unterminated literal string')
        if ch == 91 or self.data[start:start + 2] == b'<<':
            dictionary = ch != 91
            if dictionary:
                self.offset += 1
            end = b'>>' if dictionary else b']'
            values = []
            while True:
                self.skip()
                if self.data[self.offset:self.offset + len(end)] == end:
                    self.offset += len(end)
                    if not dictionary:
                        return values
                    require(len(values) % 2 == 0 and all(isinstance(key, Name) for key in values[::2]),
                            'program-token', 'A dictionary needs name/value pairs')
                    require(len(set(values[::2])) == len(values[::2]), 'program-token', 'Duplicate dictionary key')
                    return dict(zip(values[::2], values[1::2]))
                values.append(self.value(depth + 1))
        if ch == 60:
            end = self.data.find(b'>', self.offset)
            require(end >= 0, 'program-token', 'Unterminated hexadecimal string')
            encoded = bytes(ch for ch in self.data[self.offset:end] if ch not in SPACE)
            require(re.fullmatch(b'[0-9a-fA-F]*', encoded) is not None, 'program-token', 'Invalid hexadecimal string')
            self.offset = end + 1
            return bytes.fromhex((encoded + (b'0' if len(encoded) % 2 else b'')).decode('ascii'))
        if ch == 47:
            start = self.offset
            while self.offset < len(self.data) and self.data[self.offset] not in DELIMITERS:
                self.offset += 1
            encoded = self.data[start:self.offset]
            return Name(encoded.decode('latin1'))
        require(ch not in b')>]{}', 'program-token', 'Unexpected delimiter')
        while self.offset < len(self.data) and self.data[self.offset] not in DELIMITERS:
            self.offset += 1
        token = self.data[start:self.offset].decode('ascii')
        if len(token) > 4096:
            raise Unqualified('Program token length exceeds its bound')
        if re.fullmatch(r'[+-]?[0-9]+', token):
            return int(token)
        if re.fullmatch(r'[+-]?(?:[0-9]+\.[0-9]*|\.[0-9]+)', token):
            return Decimal(token)
        return Word(token)


class Graph:
    def __init__(self, raw):
        if raw.get('version') != 2 or raw['qpdf'][0]['jsonversion'] != 2:
            raise Unqualified('Unknown qpdf graph schema')
        self.objects = raw['qpdf'][1]

    def dictionaries(self):
        pending = [(entry.get('value', entry.get('stream', {}).get('dict')), 0) for entry in self.objects.values()]
        count = 0
        while pending:
            value, depth = pending.pop()
            count += 1
            if depth > 128 or count > 100000:
                raise Unqualified('PDF direct-container graph exceeds its acceptance bound')
            if isinstance(value, dict):
                yield value
                pending.extend((child, depth + 1) for child in value.values())
            elif isinstance(value, list):
                pending.extend((child, depth + 1) for child in value)

    def resolve(self, value):
        seen = set()
        while isinstance(value, str) and REF.fullmatch(value):
            if value in seen or len(seen) >= 128:
                raise Unqualified('Cyclic or excessive indirect value chain')
            seen.add(value)
            entry = self.objects['obj:' + value]
            value = entry['stream']['dict'] if 'stream' in entry else entry['value']
        return value

    def stream(self, reference):
        if not isinstance(reference, str) or not REF.fullmatch(reference):
            raise Unqualified('Program stream reference is unavailable')
        stream = self.objects['obj:' + reference]['stream']
        if '/Filter' in stream['dict'] or '/DecodeParms' in stream['dict']:
            raise Unqualified('qpdf has not completely decoded the program')
        data = base64.b64decode(stream['data'], validate=True)
        if len(data) > MAX_BYTES:
            raise Unqualified('Decoded program exceeds its acceptance bound')
        return stream['dict'], data


def cmap_declaration(name, value):
    pending = [value]
    while pending:
        item = pending.pop()
        if isinstance(item, Word):
            raise Unqualified('Executable declaration values are outside the literal CMap grammar')
        if isinstance(item, list):
            pending.extend(item)
        elif isinstance(item, dict):
            pending.extend(item.values())
    if name == 'XUID':
        require(isinstance(value, list) and bool(value) and all(type(item) is int for item in value),
                'cmap-declaration-type', 'XUID must be a nonempty array of integers')
    elif name == 'CMapVersion':
        require(type(value) in (int, Decimal), 'cmap-declaration-type', 'CMapVersion must be a number')
    elif name == 'UIDOffset':
        require(type(value) is int, 'cmap-declaration-type', 'UIDOffset must be an integer')
    return value


def cmap_blocks(data, role):
    tokens = Tokens(data).all()
    prefix = [Name('CIDInit'), Name('ProcSet'), Word('findresource'), Word('begin')]
    suffix = [Word('endcmap'), Word('CMapName'), Word('currentdict'), Name('CMap'),
              Word('defineresource'), Word('pop'), Word('end'), Word('end')]
    same = lambda left, right: type(left) is type(right) and left == right
    require(sum(same(token, Word('begincmap')) for token in tokens) == 1
            and sum(same(token, Word('endcmap')) for token in tokens) == 1,
            'cmap-envelope', 'CMap program must contain one matched begincmap/endcmap pair')
    qualified = (len(tokens) >= 16 and all(same(a, b) for a, b in zip(tokens[:4], prefix))
            and type(tokens[4]) is int and tokens[4] > 0
            and all(same(a, b) for a, b in zip(tokens[5:8], [Word('dict'), Word('begin'), Word('begincmap')]))
            and all(same(a, b) for a, b in zip(tokens[-8:], suffix)))
    if not qualified:
        raise Unqualified('CMap resource envelope is outside the qualified grammar')
    widths = {'codespacerange': 2, 'cidchar': 2, 'bfchar': 2, 'cidrange': 3,
              'bfrange': 3, 'notdefchar': 2, 'notdefrange': 3}
    blocks = []
    definitions = {}
    index = 8
    while index < len(tokens) - 8:
        token = tokens[index]
        if isinstance(token, Name):
            if index + 1 < len(tokens) - 8 and same(tokens[index + 1], Word('usecmap')):
                require(not blocks and '_UseCMap' not in definitions, 'cmap-usecmap-order',
                        'usecmap must occur once before range and mapping blocks')
                definitions['_UseCMap'] = token
                index += 2
                continue
            if token in definitions or token not in ('CIDSystemInfo', 'CMapName', 'CMapType', 'WMode',
                                                     'CMapVersion', 'UIDOffset', 'XUID'):
                raise Unqualified('CMap definition is outside the qualified declaration grammar')
            require(index + 2 < len(tokens) - 8 and same(tokens[index + 2], Word('def')),
                    'cmap-envelope', 'CMap definition does not end with def')
            definitions[token] = cmap_declaration(token, tokens[index + 1])
            index += 3
        elif type(token) is int and index + 1 < len(tokens) - 8 and isinstance(tokens[index + 1], Word):
            operation = tokens[index + 1]
            if operation == 'usefont':
                require(token == 0, 'cmap-usefont-zero', 'PDF CMaps may select only font zero')
                index += 2
                continue
            if not operation.startswith('begin') or operation[5:] not in widths:
                raise Unqualified('CMap operator is outside the qualified block grammar')
            kind = operation[5:]
            allowed = ('codespacerange', 'cidchar', 'cidrange', 'notdefchar', 'notdefrange') if role == 'encoding' else (
                'codespacerange', 'bfchar', 'bfrange')
            require(kind in allowed, 'cmap-operator-family', 'Mapping operator is forbidden for this CMap use')
            require(0 <= token <= 100,
                    'cmap-block-count', 'A CMap block requires an integer count from 0 to 100')
            end = index + 2 + token * widths[kind]
            require(end < len(tokens) - 8 and same(tokens[end], Word('end' + kind)),
                    'cmap-block-count', 'CMap block count does not match its entries')
            blocks.append((kind, tokens[index + 2:end]))
            index = end + 1
        else:
            raise Unqualified('CMap instruction is outside the qualified block grammar')
    if type(definitions.get('CMapType')) is int and definitions['CMapType'] not in (1, 2):
        raise Unqualified('Legacy or other CMapType is outside the qualified program types')
    require(type(definitions.get('CMapType')) is int and definitions['CMapType'] == (1 if role == 'encoding' else 2),
            'cmap-program-type', 'CMapType does not match the owning Encoding or ToUnicode entry')
    return definitions, blocks


def cmap_domains(blocks, inherited):
    require(bool(inherited.spaces) or not blocks or blocks[0][0] == 'codespacerange',
            'cmap-codespace', 'The first range operator must declare codespaces unless they are inherited')
    spaces = []
    mappings = []
    mapping_seen = False
    for kind, values in blocks:
        if kind != 'codespacerange':
            mapping_seen = True
        if kind == 'codespacerange':
            require(not mapping_seen, 'cmap-codespace', 'Codespaces must precede all mapping blocks, including empty ones')
            for low, high in zip(values[::2], values[1::2]):
                if len(spaces) >= 256:
                    raise Unqualified('CMap codespace count exceeds its qualification bound')
                require(isinstance(low, bytes) and isinstance(high, bytes) and 1 <= len(low) == len(high) <= 4
                        and all(left <= right for left, right in zip(low, high)),
                        'cmap-codespace', 'Codespace endpoints must have equal lengths and ordered byte dimensions')
                for other_low, other_high in spaces:
                    require(not prefixes_overlap(low, high, other_low, other_high),
                            'cmap-codespace-overlap', 'Codespaces overlap or have ambiguous prefixes')
                spaces.append((low, high))
        elif kind in ('cidchar', 'bfchar', 'notdefchar'):
            if len(mappings) + len(values) // 2 > 4096:
                raise Unqualified('CMap expanded mapping count exceeds its qualification bound')
            mappings.extend((kind, code, destination) for code, destination in zip(values[::2], values[1::2]))
        else:
            for low, high, destination in zip(values[::3], values[1::3], values[2::3]):
                require(isinstance(low, bytes) and isinstance(high, bytes) and 1 <= len(low) == len(high) <= 4
                        and low <= high, 'cmap-mapping-domain', 'Mapping range endpoints are invalid')
                if low[:-1] != high[:-1]:
                    raise Unqualified('Mapping range crosses the qualified final-byte interval')
                count = high[-1] - low[-1] + 1
                if len(mappings) + count > 4096:
                    raise Unqualified('CMap expanded mapping count exceeds its qualification bound')
                if kind == 'bfrange' and isinstance(destination, list):
                    require(len(destination) == count, 'cmap-range-cardinality', 'bfrange array length does not match its source interval')
                for offset in range(count):
                    code = low[:-1] + bytes([low[-1] + offset])
                    if kind == 'bfrange':
                        if isinstance(destination, bytes):
                            require(bool(destination), 'cmap-unicode', 'bfrange destination is empty')
                            if destination[-1] + offset > 255:
                                raise Unqualified('bfrange destination exceeds the qualified final-byte interval')
                            value = destination[:-1] + bytes([destination[-1] + offset])
                        elif isinstance(destination, list):
                            value = destination[offset]
                        else:
                            raise Invalid('cmap-unicode', 'bfrange destination is not a string or string array')
                        mappings.append(('bfchar', code, value))
                    else:
                        require(type(destination) is int, 'cmap-cid-domain', 'CID range destination is not an integer')
                        mappings.append(('cidchar', code, destination + (offset if kind == 'cidrange' else 0)))
    require(not (spaces and inherited.spaces), 'cmap-inherited-codespace', 'An inherited codespace cannot be redefined')
    spaces = spaces or inherited.spaces
    require(bool(spaces), 'cmap-codespace', 'CMap has no local or inherited codespace')
    for kind, code, destination in mappings:
        require(isinstance(code, bytes) and any(len(code) == len(low) and all(left <= byte <= right
                for byte, left, right in zip(code, low, high)) for low, high in spaces),
                'cmap-mapping-domain', 'Mapping source is outside the CMap codespace')
        if kind in ('cidchar', 'notdefchar'):
            require(type(destination) is int and 0 <= destination <= 65535,
                    'cmap-cid-domain', 'CID mapping destination must be an unsigned 16-bit integer')
        else:
            require(isinstance(destination, bytes) and len(destination) >= 2 and len(destination) % 2 == 0,
                    'cmap-unicode', 'ToUnicode destination must be a UTF-16BE sequence')
            try:
                destination.decode('utf-16-be', errors='strict')
            except UnicodeDecodeError:
                raise Invalid('cmap-unicode', 'ToUnicode destination has an unpaired surrogate')
    if len(inherited.sources) > 4096:
        raise Unqualified('Inherited CMap source count exceeds its qualification bound')
    sources = set(inherited.sources)
    for kind, code, destination in mappings:
        if code not in sources and len(sources) >= 4096:
            raise Unqualified('Effective CMap source count exceeds its qualification bound')
        sources.add(code)
    return CMapDomain(spaces, sources)


def cmap_dictionary(graph, dictionary, definitions):
    program_name = definitions.get('CMapName')
    info = definitions.get('CIDSystemInfo')
    mode = definitions.get('WMode', 0)
    if not isinstance(program_name, Name) or not isinstance(info, dict):
        raise Unqualified('CMap program name or character collection is unavailable')
    if not all(isinstance(info.get(key), bytes) for key in ('Registry', 'Ordering')) or type(info.get('Supplement')) is not int:
        raise Unqualified('CMap program character collection has unqualified types')
    require(type(mode) is int and mode in (0, 1), 'cmap-dictionary-program', 'CMap writing mode is not 0 or 1')
    if dictionary.get('/CMapName') is not None:
        require(graph.resolve(dictionary['/CMapName']) == '/' + program_name,
                'cmap-dictionary-program', 'CMapName differs between dictionary and decoded program')
    require(graph.resolve(dictionary.get('/WMode', 0)) == mode,
            'cmap-dictionary-program', 'WMode differs between dictionary and decoded program')
    if dictionary.get('/CIDSystemInfo') is not None:
        declared = graph.resolve(dictionary['/CIDSystemInfo'])
        require(isinstance(declared, dict), 'cmap-dictionary-program', 'CIDSystemInfo is not a dictionary')
        for key in ('Registry', 'Ordering'):
            text = info[key].decode('ascii')
            require(graph.resolve(declared.get('/' + key)) in ('u:' + text, 'b:' + info[key].hex()),
                    'cmap-dictionary-program', 'CIDSystemInfo ' + key + ' differs between dictionary and program')
        require(graph.resolve(declared.get('/Supplement')) == info['Supplement'],
                'cmap-dictionary-program', 'CIDSystemInfo Supplement differs between dictionary and program')


def check_cmaps(graph):
    references = set()
    composite_fonts = []
    unicode_fonts = []
    for value in graph.dictionaries():
        if graph.resolve(value.get('/Type')) != '/Font':
            continue
        if value.get('/ToUnicode') is not None:
            references.add((value['/ToUnicode'], 'unicode'))
            unicode_fonts.append(value)
        if graph.resolve(value.get('/Subtype')) == '/Type0':
            encoding = value.get('/Encoding')
            if isinstance(encoding, str) and REF.fullmatch(encoding):
                references.add((encoding, 'encoding'))
                composite_fonts.append((value, encoding))
            else:
                raise Unqualified('Predefined Encoding programs are outside this program qualification')
    pending = sorted(references)
    seen = set()
    parsed = {}
    while pending:
        reference, role = pending.pop()
        if (reference, role) in seen:
            continue
        if len(seen) >= 128:
            raise Unqualified('CMap graph exceeds its acceptance bound')
        seen.add((reference, role))
        dictionary, data = graph.stream(reference)
        definitions, blocks = cmap_blocks(data, role)
        cmap_dictionary(graph, dictionary, definitions)
        parsed[(reference, role)] = (dictionary, definitions, blocks)
        parent = dictionary.get('/UseCMap')
        if parent is not None:
            if not isinstance(parent, str) or not REF.fullmatch(parent):
                raise Unqualified('Predefined UseCMap programs are outside this program qualification')
            pending.append((parent, role))
    domains = {}

    def establish(key, path):
        require(key not in path, 'cmap-inheritance-cycle', 'CMap inheritance must be acyclic')
        if key in domains:
            return domains[key]
        dictionary, definitions, blocks = parsed[key]
        parent = dictionary.get('/UseCMap')
        inherited = establish((parent, key[1]), path | {key}) if parent is not None else CMapDomain([], set())
        domains[key] = cmap_domains(blocks, inherited)
        return domains[key]

    for key in parsed:
        dictionary, definitions, blocks = parsed[key]
        if '_UseCMap' in definitions:
            parent = dictionary.get('/UseCMap')
            require(parent is not None and (parent, key[1]) in parsed,
                    'cmap-usecmap-agreement', 'Program usecmap has no matching dictionary UseCMap')
            require(definitions['_UseCMap'] == parsed[(parent, key[1])][1].get('CMapName'),
                    'cmap-usecmap-agreement', 'Program usecmap name does not identify the declared parent')
        establish(key, set())
    for font, encoding in composite_fonts:
        descendants = graph.resolve(font.get('/DescendantFonts'))
        require(isinstance(descendants, list) and len(descendants) == 1,
                'cmap-font-collection', 'A Type0 Encoding requires one CIDFont descendant')
        descendant = graph.resolve(descendants[0])
        require(isinstance(descendant, dict), 'cmap-font-collection', 'CIDFont descendant is not a dictionary')
        collection = graph.resolve(descendant.get('/CIDSystemInfo'))
        require(isinstance(collection, dict), 'cmap-font-collection', 'CIDFont character collection is missing')
        info = parsed[(encoding, 'encoding')][1]['CIDSystemInfo']
        for key in ('Registry', 'Ordering'):
            require(graph.resolve(collection.get('/' + key)) in ('u:' + info[key].decode('ascii'), 'b:' + info[key].hex()),
                    'cmap-font-collection', 'Encoding and CIDFont ' + key + ' must identify the same character collection')
    for font in unicode_fonts:
        subtype = graph.resolve(font.get('/Subtype'))
        unicode = domains[(font['/ToUnicode'], 'unicode')]
        spaces = unicode.spaces
        if subtype in ('/Type1', '/MMType1', '/TrueType', '/Type3'):
            require(all(len(low) == 1 for low, high in spaces),
                    'cmap-font-domain', 'A simple-font ToUnicode codespace must use single-byte source codes')
        elif subtype == '/Type0':
            encoding_spaces = domains[(font['/Encoding'], 'encoding')].spaces
            encoded_lengths = {len(low) for low, high in encoding_spaces}
            require(all(len(low) in encoded_lengths for low, high in spaces),
                    'cmap-font-domain', 'ToUnicode source lengths must agree with the owning Encoding')
            for low, high in spaces:
                for encoded_low, encoded_high in encoding_spaces:
                    if len(low) != len(encoded_low):
                        require(not prefixes_overlap(low, high, encoded_low, encoded_high),
                                'cmap-font-domain', 'ToUnicode and Encoding assign conflicting lengths to a shared prefix')
            for code in unicode.sources:
                require(any(len(code) == len(low) and all(left <= byte <= right
                            for byte, left, right in zip(code, low, high)) for low, high in encoding_spaces),
                        'cmap-font-domain', 'ToUnicode mapping source is outside the owning Encoding codespace')
        else:
            raise Unqualified('ToUnicode owner is outside the qualified font kinds')
    return len(seen)


def run(arguments, output, name):
    def limits():
        resource.setrlimit(resource.RLIMIT_FSIZE, (MAX_BYTES, MAX_BYTES))
        resource.setrlimit(resource.RLIMIT_AS, (512 * 1024 * 1024, 512 * 1024 * 1024))
    with (output / name).open('wb') as stdout, (output / (name + '.stderr')).open('wb') as stderr:
        result = subprocess.run(arguments, stdout=stdout, stderr=stderr, timeout=15, preexec_fn=limits)
    if result.returncode != 0 or (output / (name + '.stderr')).stat().st_size:
        raise Unqualified('Pinned qpdf did not complete without findings')
    return bounded(output / name)


def inspect(root, pdf, scope, output):
    output.mkdir()
    def cpu_limit(signum, frame):
        raise Unqualified('Independent checker CPU qualification bound reached')
    signal.signal(signal.SIGXCPU, cpu_limit)
    resource.setrlimit(resource.RLIMIT_CPU, (5, 10))
    resource.setrlimit(resource.RLIMIT_AS, (512 * 1024 * 1024, 512 * 1024 * 1024))
    result = {'profile': PROFILE, 'standards': 'indeterminate', 'input-sha256': 'unavailable',
              'scope': scope, 'observer-sha256': digest(Path(__file__)), 'python-version': platform.python_version(),
              'python-executable-sha256': digest(Path(sys.executable).resolve())}
    wrapper = root / 'scripts/container-bin/qpdf'
    binary = Path(os.environ.get('QPDF_CACHE_DIRECTORY', str(root / '.build-cache/qpdf'))) / '12.4.0/bin/qpdf'
    pin = root / 'scripts/qpdf-pin.properties'
    try:
        result['input-sha256'] = digest(pdf)
        pin_before = digest(pin)
        values = dict(line.split('=', 1) for line in pin.read_text().splitlines() if line and not line.startswith('#'))
        if (values['QPDF_VERSION'] != '12.4.0' or values['QPDF_BINARY_SHA256'] != QPDF_SHA
                or values['QPDF_EXECUTABLE'] != 'container-bin/qpdf' or digest(wrapper) != WRAPPER_SHA or digest(binary) != QPDF_SHA):
            raise Unqualified('Pinned qpdf identity mismatch')
        version = run([str(wrapper), '--version'], output, 'qpdf-version.txt').decode('ascii')
        if not version.startswith('qpdf version 12.4.0\n'):
            raise Unqualified('Observed qpdf version mismatch')
        result.update({'qpdf-version': '12.4.0', 'qpdf-binary-sha256': QPDF_SHA, 'qpdf-wrapper-sha256': WRAPPER_SHA})
        data = run([str(wrapper), '--json=2', '--json-stream-data=inline', '--decode-level=all', str(pdf)], output, 'qpdf.json')
        result['qpdf-json-sha256'] = hashlib.sha256(data).hexdigest()
        graph = Graph(json.loads(data, parse_float=Decimal))
        if scope != 'cmaps':
            raise Unqualified('Unknown program qualification scope')
        result['observed-cmaps'] = check_cmaps(graph)
        result.update(standards='pass', finding='Decoded CMap grammar, character domains and declared inheritance are consistent.')
    except Invalid as error:
        result.update(standards='fail', rule=error.rule, finding=str(error))
    except (Unqualified, OSError, ValueError, KeyError, TypeError, MemoryError, RecursionError, subprocess.TimeoutExpired) as error:
        result.update(standards='indeterminate', finding=str(error) or type(error).__name__)
    try:
        if result['input-sha256'] != 'unavailable' and (digest(pdf) != result['input-sha256'] or digest(wrapper) != WRAPPER_SHA or digest(binary) != QPDF_SHA
                or digest(pin) != pin_before or digest(Path(__file__)) != result['observer-sha256']):
            result.update(standards='indeterminate', finding='Input or checker identity changed during observation')
    except (OSError, Unqualified, UnboundLocalError):
        result.update(standards='indeterminate', finding='An input or checker identity is unavailable')
    def escaped(value):
        return str(value).replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '\\r')
    (output / 'result.properties').write_text(''.join(key + '=' + escaped(value) + '\n' for key, value in sorted(result.items())))


if __name__ == '__main__':
    if len(sys.argv) != 5:
        raise SystemExit('Usage: t13-program-standards.py <repository> <pdf> <scope> <fresh-output>')
    inspect(Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve(), sys.argv[3], Path(sys.argv[4]).resolve())
