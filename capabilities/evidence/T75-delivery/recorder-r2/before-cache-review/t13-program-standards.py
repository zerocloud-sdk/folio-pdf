#!/usr/bin/env python3
"""Independent, bounded T13 program qualification over pinned qpdf objects.

This acceptance-only checker imports neither Folio nor the corpus authoring
code. Unsupported syntax remains unqualified. Apache-2.0 project code.
"""
import base64
from decimal import Decimal
from fractions import Fraction
import hashlib
from io import BytesIO
import json
import math
import os
from pathlib import Path
import platform
import re
import resource
import signal
import struct
import subprocess
import sys

PROFILE = 'T13-text-logical-structure'
QPDF_SHA = '9ac787a28597e8428289a12ba3fedafd74bdfb4b4da1be814722faf76f14f21b'
WRAPPER_SHA = 'a12d5a4e48fd37e8aefa3b92b30f002c25d2de9f96944fb68efeb64f2b79431e'
QPDF_PIN_SHA = '62c63de3d888ba08b60df9e3c33c9ebefe4cc0c8ee747cc5269390017b57169c'
QPDF_RUNTIME_SHA = 'a06ee3eb9314e2fa3db4fb475d806f8249f82e0daea85528535c04362fa280ad'
FONTTOOLS_SHA = '8bd0f759020e87bb5d323e6283914d9bf4ae35a7307dafb2cbd1e379e720ad37'
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


class FontProgram:
    def __init__(self, name, bounds, widths, unicode_cmap):
        self.name = name
        self.bounds = bounds
        self.widths = widths
        self.unicode_cmap = unicode_cmap


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


def qpdf_runtime(root, binary):
    manifest = bounded(root / 'scripts/t13-qpdf-runtime.sha256')
    if hashlib.sha256(manifest).hexdigest() != QPDF_RUNTIME_SHA:
        raise Unqualified('Pinned qpdf runtime manifest identity mismatch')
    for line in manifest.decode('ascii').splitlines():
        expected, name = line.split('  ', 1)
        if digest(binary.parent.parent / name) != expected:
            raise Unqualified('Pinned qpdf runtime library identity mismatch')
    return QPDF_RUNTIME_SHA


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


def load_fonttools(root, result):
    if sys.flags.optimize:
        raise Unqualified('fontTools qualification requires Python assertions enabled')
    default = root / '.build-cache/t75-fonttools-4.59.2/fonttools-4.59.2-py3-none-any.whl'
    wheel = Path(os.environ.get('FOLIO_FONTTOOLS_WHEEL', str(default))).resolve()
    if digest(wheel) != FONTTOOLS_SHA:
        raise Unqualified('Pinned fontTools wheel identity mismatch')
    sys.path.insert(0, str(wheel))
    import fontTools
    if fontTools.__version__ != '4.59.2' or not fontTools.__file__.startswith(str(wheel) + '/'):
        raise Unqualified('fontTools was not loaded from the pinned wheel')
    result.update({'fonttools-version': '4.59.2', 'fonttools-wheel': str(wheel), 'fonttools-wheel-sha256': FONTTOOLS_SHA})
    return wheel


def cff_index(data, start):
    require(type(start) is int and 0 <= start <= len(data) - 2,
            'font-program-layout', 'CFF INDEX location is outside the program')
    count = int.from_bytes(data[start:start + 2], 'big')
    if count > 4096:
        raise Unqualified('CFF INDEX count exceeds its qualification bound')
    if count == 0:
        return [], start + 2
    require(start + 3 <= len(data), 'font-program-layout', 'CFF INDEX offset size is missing')
    size = data[start + 2]
    base = start + 3 + (count + 1) * size
    require(1 <= size <= 4 and base <= len(data),
            'font-program-layout', 'CFF INDEX offset array is truncated or has an invalid offset size')
    offsets = [int.from_bytes(data[start + 3 + index * size:start + 3 + (index + 1) * size], 'big')
               for index in range(count + 1)]
    require(offsets[0] == 1 and offsets == sorted(offsets) and base - 1 + offsets[-1] <= len(data),
            'font-program-layout', 'CFF INDEX offsets must start at one, increase and stay inside the program')
    return [(base - 1 + offsets[index], base - 1 + offsets[index + 1]) for index in range(count)], base - 1 + offsets[-1]


def qualify_cff_dictionary(data, dictionary):
    reader = dictionary.decompilerClass(dictionary.strings, dictionary)
    encoding = reader.operandEncoding

    def checked_operator(reader, code, data, offset):
        operator = (code, data[offset]) if code == 12 else code
        if code > 21 or operator not in reader.operators:
            raise Unqualified('Reserved or unknown CFF DICT operator prevents qualification')
        return encoding[code](reader, code, data, offset)

    reader.operandEncoding = [checked_operator if code < 28 or code in (31, 255) else encoding[code]
                              for code in range(256)]
    reader.decompile(data)
    reader.getDict()


def qualify_cff_outline(charstring):
    from fontTools.pens.boundsPen import BoundsPen
    if len(charstring.bytecode) > 65535:
        raise Unqualified('CFF charstring exceeds its byte qualification bound')
    operands = []
    offset = 0
    moved = False
    width_seen = False
    ended = False
    while offset < len(charstring.bytecode):
        token, operator, offset = charstring.getToken(offset)
        if not operator:
            if type(token) not in (int, float):
                raise Unqualified('Unknown CFF charstring token prevents qualification')
            require(math.isfinite(token) and len(operands) < 48,
                    'font-program-outline', 'CFF charstring operands must be finite and fit the operand stack')
            operands.append(token)
            continue
        if token == 'hmoveto':
            require(len(operands) in ((1, 2) if not width_seen else (1,)),
                    'font-program-outline', 'CFF hmoveto has an invalid operand count')
            moved = True
            width_seen = True
        elif token == 'hlineto':
            require(moved and len(operands) > 0,
                    'font-program-outline', 'CFF hlineto requires a current path and line operands')
        elif token == 'endchar':
            if len(operands) == 4 or (not width_seen and len(operands) == 5):
                raise Unqualified('Deprecated CFF composite endchar is outside outline qualification')
            require(len(operands) in ((0, 1) if not width_seen else (0,)) and offset == len(charstring.bytecode),
                    'font-program-outline', 'CFF endchar must have valid operands and terminate the glyph')
            ended = True
        else:
            raise Unqualified('CFF outline operator is outside the qualified path grammar: ' + token)
        operands.clear()
    require(ended, 'font-program-outline', 'CFF glyph must terminate with endchar')
    pen = BoundsPen(None)
    charstring.draw(pen)
    return pen.bounds


def font_program_data(data, kind):
    from fontTools.cffLib import CFFFontSet
    from fontTools.misc.arrayTools import unionRect
    from fontTools.pens.boundsPen import BoundsPen
    from fontTools.pens.transformPen import TransformPen
    from fontTools.ttLib import TTFont, TTLibError
    bounds = None
    widths = {}
    unicode_cmap = None
    try:
        if kind == '/FontFile2':
            tables = int.from_bytes(data[4:6], 'big')
            if tables > 128:
                raise Unqualified('TrueType table count exceeds its qualification bound')
            tags = [data[12 + 16 * index:16 + 16 * index] for index in range(tables)]
            if len(set(tags)) != tables:
                raise Unqualified('Duplicate TrueType table tags prevent complete table qualification')
            require(tables > 0 and tags == sorted(tags),
                    'font-program-layout', 'TrueType table directory must be nonempty and ordered by tag')
            selector = tables.bit_length() - 1
            search = 16 * (1 << selector)
            require(struct.unpack('>3H', data[6:12]) == (search, selector, 16 * tables - search),
                    'font-program-layout', 'TrueType directory search fields disagree with the table count')
            directory_end = 12 + 16 * tables
            require(directory_end <= len(data), 'font-program-layout', 'TrueType table directory is truncated')
            spans = []
            for index in range(tables):
                tag, checksum, start, length = struct.unpack('>4sIII', data[12 + 16 * index:28 + 16 * index])
                require(re.fullmatch(rb'[!-~]{1,4} *', tag) is not None
                        and start % 4 == 0 and directory_end <= start <= start + length <= len(data),
                        'font-program-layout', 'TrueType table tag, alignment or byte range is invalid')
                spans.append((start, start + length))
            previous = directory_end
            for start, end in sorted(spans):
                require(not any(data[previous:start]), 'font-program-layout', 'TrueType inter-table padding must be zero')
                previous = max(previous, end)
            with TTFont(BytesIO(data), checkChecksums=2, lazy=True) as font:
                if font['maxp'].numGlyphs > 4096:
                    raise Unqualified('TrueType glyph count exceeds its qualification bound')
                font.ensureDecompiled()
                names = {record.toUnicode() for record in font['name'].names if record.nameID == 6} if 'name' in font else set()
                if len(names) > 1:
                    raise Unqualified('Conflicting TrueType PostScript names prevent font-name qualification')
                font_name = next(iter(names), None)
                require(16 <= font['head'].unitsPerEm <= 16384,
                        'font-program-units', 'TrueType unitsPerEm must be between 16 and 16384')
                scale = Fraction(1000, font['head'].unitsPerEm)
                if 'cmap' in font:
                    tables = [table for table in font['cmap'].tables if (table.platformID, table.platEncID) == (3, 1)]
                    if len(tables) == 1:
                        unicode_cmap = getattr(tables[0], 'cmap', None)
                for name in font.getGlyphOrder():
                    glyph = font['glyf'][name]
                    if glyph.isComposite():
                        raise Unqualified('Composite TrueType glyphs are outside outline qualification')
                    coordinates, ends, flags = glyph.getCoordinates(font['glyf'])
                    if coordinates:
                        require((glyph.xMin, glyph.yMin, glyph.xMax, glyph.yMax) == coordinates.calcIntBounds(),
                                'font-program-outline', 'TrueType glyph bounds disagree with its coordinate data')
                    if any(not flag & 1 for flag in flags):
                        raise Unqualified('Curved TrueType outlines are outside exact polygon-bounds qualification')
                    pen = BoundsPen(None)
                    glyph.draw(TransformPen(pen, (scale, 0, 0, scale, 0, 0)), font['glyf'])
                    if pen.bounds is not None:
                        bounds = pen.bounds if bounds is None else unionRect(bounds, pen.bounds)
                    widths[name] = Fraction(font['hmtx'].metrics[name][0] * 1000, font['head'].unitsPerEm)
            padded = data + b'\x00' * (-len(data) % 4)
            require(sum(value for (value,) in struct.iter_unpack('>I', padded)) & 0xffffffff == 0xb1b0afba,
                    'font-program-checksum', 'TrueType whole-font checksum adjustment is invalid')
        else:
            cursor = data[2]
            for index in range(4):
                entries, cursor = cff_index(data, cursor)
                if index < 2:
                    require(len(entries) == 1, 'font-program-data',
                            'An embedded CFF program requires one matching Name and Top DICT entry')
            font = CFFFontSet()
            font.decompile(BytesIO(data), None, isCFF2=False)
            require(len(font.fontNames) == len(font.topDictIndex) == 1,
                    'font-program-data', 'An embedded CFF program requires one matching Name and Top DICT entry')
            top = font.topDictIndex[0]
            font_name = font.fontNames[0]
            index = font.topDictIndex
            qualify_cff_dictionary(data[index.offsetBase + index.offsets[0]:index.offsetBase + index.offsets[1]], top)
            if top.CharstringType != 2:
                raise Unqualified('CFF CharstringType is outside the qualified Type 2 outline grammar')
            if 'FontMatrix' in top.rawDict:
                raise Unqualified('Explicit CFF FontMatrix is outside the default-matrix outline qualification')
            require('CharStrings' in top.rawDict,
                    'font-program-data', 'CFF Top DICT requires a CharStrings INDEX')
            require(('ROS' in top.rawDict) == (kind == '/CIDFontType0C'),
                    'font-program-kind', 'CFF character collection must match the declared embedded font kind')
            if kind == '/CIDFontType0C':
                require('FDArray' in top.rawDict and 'FDSelect' in top.rawDict,
                        'font-program-data', 'CID CFF requires FDArray and FDSelect')
            if top.numGlyphs > 4096:
                raise Unqualified('CFF glyph count exceeds its qualification bound')
            cff_index(data, top.rawDict['CharStrings'])
            require(len(top.charset) == len(top.CharStrings) == top.numGlyphs,
                    'font-program-data', 'CFF charset and CharStrings INDEX must describe the same glyph count')
            for gid, name in enumerate(top.charset):
                if kind == '/CIDFontType0C':
                    dictionary = top.FDArray[top.FDSelect.gidArray[gid]]
                    if 'FontMatrix' in dictionary.rawDict:
                        raise Unqualified('Explicit CID CFF FontMatrix is outside the default-matrix outline qualification')
                charstring = top.CharStrings[name]
                glyph_bounds = qualify_cff_outline(charstring)
                widths[name] = charstring.width if (type(charstring.private.defaultWidthX) is int
                                                    and type(charstring.private.nominalWidthX) is int) else None
                if glyph_bounds is not None:
                    bounds = glyph_bounds if bounds is None else unionRect(bounds, glyph_bounds)
    except NotImplementedError as error:
        raise Unqualified('fontTools cannot qualify this data: ' + (str(error) or type(error).__name__))
    except (AssertionError, EOFError, IndexError, KeyError, ValueError, struct.error, TTLibError) as error:
        raise Invalid('font-program-data', 'fontTools could not read the declared font data: ' + (str(error) or type(error).__name__))
    return FontProgram(font_name, bounds, widths, unicode_cmap)


def check_font_programs(graph, root, result):
    wheel = load_fonttools(root, result)
    seen = {}
    descriptors = {}
    for dictionary in graph.dictionaries():
        if graph.resolve(dictionary.get('/Type')) != '/FontDescriptor':
            continue
        for key in ('/FontFile', '/FontFile2', '/FontFile3'):
            reference = dictionary.get(key)
            if reference is None:
                continue
            stream, data = graph.stream(reference)
            kind = graph.resolve(stream.get('/Subtype')) if key == '/FontFile3' else key
            if (reference, kind) not in seen:
                if len(seen) >= 128:
                    raise Unqualified('Embedded font program count exceeds its qualification bound')
                if kind in ('/Type1C', '/CIDFontType0C'):
                    require(len(data) >= 4 and data[0] == 1
                            and 4 <= data[2] <= len(data) and 1 <= data[3] <= 4,
                            'font-program-header', 'CFF header must declare major version 1 and valid header/offset sizes')
                elif kind == '/FontFile2':
                    if data[:4] == b'true':
                        raise Unqualified('Legacy TrueType signature is outside this font qualification')
                    require(len(data) >= 12 and data[:4] == b'\x00\x01\x00\x00',
                            'font-program-header', 'TrueType program must declare the expected sfnt version')
                    length = graph.resolve(stream.get('/Length1'))
                    require(type(length) is int and length == len(data),
                            'font-program-length', 'TrueType Length1 must equal the decoded font byte length')
                else:
                    raise Unqualified('Embedded font format is outside this program qualification')
                seen[(reference, kind)] = font_program_data(data, kind)
            program = seen[(reference, kind)]
            name, bounds = program.name, program.bounds
            declared = graph.resolve(dictionary.get('/FontName'))
            if name is None or re.match(r'^[A-Z]{6}\+', name) or (isinstance(declared, str) and re.match(r'^/[A-Z]{6}\+', declared)):
                raise Unqualified('Missing program names or subset tags are outside font-name qualification')
            require(declared == '/' + name, 'font-program-name',
                    'PDF FontName does not identify the embedded font program')
            box = graph.resolve(dictionary.get('/FontBBox'))
            if isinstance(box, list):
                box = [graph.resolve(value) for value in box]
            require(isinstance(box, list) and len(box) == 4
                    and all(type(value) in (int, Decimal) and math.isfinite(value) for value in box),
                    'font-descriptor-bounds', 'PDF FontBBox must be a finite rectangle')
            box = min(box[0], box[2]), min(box[1], box[3]), max(box[0], box[2]), max(box[1], box[3])
            if bounds is not None:
                require(box[0] <= bounds[0] and box[1] <= bounds[1]
                        and box[2] >= bounds[2] and box[3] >= bounds[3],
                        'font-descriptor-bounds', 'PDF FontBBox does not enclose the embedded glyph outlines')
            descriptors[id(dictionary)] = program
    if digest(wheel) != FONTTOOLS_SHA:
        raise Unqualified('fontTools wheel changed during observation')
    return len(seen), descriptors


def metric_program(graph, font, programs, expected_file):
    descriptor = graph.resolve(font.get('/FontDescriptor'))
    if not isinstance(descriptor, dict) or id(descriptor) not in programs:
        raise Unqualified('Font metrics require a qualified embedded font program')
    files = [key for key in ('/FontFile', '/FontFile2', '/FontFile3') if descriptor.get(key) is not None]
    if files != [expected_file]:
        raise Unqualified('Font program binding is outside metric qualification')
    program = programs[id(descriptor)]
    if any(width is None for width in program.widths.values()):
        raise Unqualified('CFF real DICT widths are outside the qualified integer-DICT width profile')
    return descriptor, program


def check_simple_font_metrics(graph, programs):
    from fontTools.agl import UV2AGL
    count = 0
    for font in graph.dictionaries():
        if graph.resolve(font.get('/Type')) != '/Font':
            continue
        subtype = graph.resolve(font.get('/Subtype'))
        if subtype not in ('/Type1', '/MMType1', '/TrueType'):
            continue
        descriptor, program = metric_program(graph, font, programs,
                                              '/FontFile2' if subtype == '/TrueType' else '/FontFile3')
        flags = graph.resolve(descriptor.get('/Flags'))
        first, last = graph.resolve(font.get('/FirstChar')), graph.resolve(font.get('/LastChar'))
        widths = graph.resolve(font.get('/Widths'))
        require(type(first) is int and type(last) is int and 0 <= first <= last <= 255
                and isinstance(widths, list) and len(widths) == last - first + 1,
                'font-widths', 'Simple font Widths must cover exactly FirstChar through LastChar')
        if (graph.resolve(font.get('/Encoding')) != '/WinAnsiEncoding' or first < 32 or last > 126
                or type(flags) is not int or flags & 4 or not flags & 32):
            raise Unqualified('Simple font metrics currently qualify nonsymbolic WinAnsi ASCII glyph selection')
        for index, value in enumerate(widths):
            code = first + index
            if subtype == '/TrueType':
                if program.unicode_cmap is None:
                    raise Unqualified('TrueType metric selection requires one Windows Unicode cmap')
                name = program.unicode_cmap.get(code)
            else:
                name = UV2AGL.get(code)
            if name is None or name not in program.widths:
                raise Unqualified('Simple font glyph selection cannot be qualified')
            width = graph.resolve(value)
            require(type(width) in (int, Decimal) and math.isfinite(width)
                    and math.isfinite(program.widths[name]) and width == program.widths[name],
                    'font-widths', 'PDF Widths disagrees with the selected embedded glyph advance')
        count += 1
    return count


def cid_widths(graph, font):
    from itertools import repeat
    items = graph.resolve(font.get('/W', []))
    require(isinstance(items, list), 'font-widths', 'CID W must be an array')
    result = {}
    offset = 0
    entries = 0
    while offset < len(items):
        require(offset + 1 < len(items), 'font-widths', 'CID W group is incomplete')
        first, values = graph.resolve(items[offset]), graph.resolve(items[offset + 1])
        offset += 2
        require(type(first) is int and 0 <= first <= 65535, 'font-widths', 'CID W starts with an invalid CID')
        if isinstance(values, list):
            size = len(values)
        else:
            require(type(values) is int and first <= values <= 65535 and offset < len(items),
                    'font-widths', 'CID W range is invalid or incomplete')
            size = values - first + 1
            values = repeat(items[offset], size)
            offset += 1
        require(first + size <= 65536, 'font-widths', 'CID W extends outside the CID domain')
        if size > 4096 - entries:
            raise Unqualified('CID width declarations exceed their qualification bound')
        entries += size
        for index, value in enumerate(values):
            cid = first + index
            if cid in result:
                raise Unqualified('Overlapping CID width declarations are outside metric qualification')
            width = graph.resolve(value)
            require(type(width) in (int, Decimal) and math.isfinite(width),
                    'font-widths', 'CID W contains a non-finite or nonnumeric width')
            result[cid] = width
    return result


def check_cid_font_metrics(graph, programs):
    count = 0
    for font in graph.dictionaries():
        if graph.resolve(font.get('/Type')) != '/Font':
            continue
        subtype = graph.resolve(font.get('/Subtype'))
        if subtype not in ('/CIDFontType0', '/CIDFontType2'):
            continue
        _, program = metric_program(graph, font, programs,
                                    '/FontFile2' if subtype == '/CIDFontType2' else '/FontFile3')
        if subtype == '/CIDFontType2':
            if graph.resolve(font.get('/CIDToGIDMap', '/Identity')) != '/Identity':
                raise Unqualified('CID TrueType metrics currently require an Identity CIDToGIDMap')
            advances = dict(enumerate(program.widths.values()))
        else:
            advances = {}
            for name, width in program.widths.items():
                if name != '.notdef' and re.fullmatch(r'cid[0-9]{5}', name) is None:
                    raise Unqualified('CFF glyph identifiers cannot qualify CID font metrics')
                advances[0 if name == '.notdef' else int(name[3:])] = width
        widths = cid_widths(graph, font)
        default = graph.resolve(font.get('/DW', 1000))
        require(type(default) in (int, Decimal) and math.isfinite(default),
                'font-widths', 'CID DW must be a finite number')
        for cid, advance in advances.items():
            require(math.isfinite(advance) and widths.get(cid, default) == advance,
                    'font-widths', 'PDF W or DW disagrees with the selected embedded CID glyph advance')
        count += 1
    return count


def check_content(graph):
    signatures = {'BT': '', 'ET': '', 'Tf': 'nm', 'Tm': 'mmmmmm', 'Tj': 's', 'TJ': 'a',
                  'Tz': 'm', 'Ts': 'm', 'Tc': 'm', 'Tw': 'm', 'q': '', 'Q': '', 'cm': 'mmmmmm',
                  'rg': 'mmm', 'Do': 'n', 'BDC': 'np', 'EMC': ''}
    count = 0
    page_nodes = 0

    def numeric(value):
        return type(value) is int or isinstance(value, Decimal) and value.is_finite()

    def operand(kind, value):
        if kind == 'm':
            return numeric(value)
        if kind == 'n':
            return isinstance(value, Name)
        if kind == 's':
            return isinstance(value, bytes)
        if kind == 'a':
            return isinstance(value, list) and all(isinstance(item, bytes) or numeric(item) for item in value)
        return isinstance(value, (Name, dict))

    def program(references, resources, path):
        nonlocal count
        if count + len(references) > 128:
            raise Unqualified('Content stream occurrences exceed their qualification bound')
        count += len(references)
        streams = []
        size = 0
        for reference in references:
            _, data = graph.stream(reference)
            size += len(data)
            if size > MAX_BYTES:
                raise Unqualified('Combined Contents exceeds its decoded qualification bound')
            streams.append(data)
        operands = []
        for token in Tokens(b''.join(streams)).all():
            if not isinstance(token, Word):
                operands.append(token)
                continue
            if token not in signatures:
                raise Unqualified('Content operator is outside the qualified grammar: ' + token)
            signature = signatures[token]
            require(len(operands) == len(signature)
                    and all(operand(kind, value) for kind, value in zip(signature, operands)),
                    'content-operands', 'Content operator has invalid operands: ' + token)
            if token == 'Do':
                objects = graph.resolve(resources.get('/XObject', {}))
                if not isinstance(objects, dict):
                    raise Unqualified('The content XObject resource context is unavailable')
                reference = objects.get('/' + operands[0])
                if reference in path:
                    raise Unqualified('Cyclic Form execution is outside content qualification')
                form, _ = graph.stream(reference)
                if graph.resolve(form.get('/Subtype')) != '/Form':
                    raise Unqualified('Only Form XObjects are qualified in this content scope')
                nested = graph.resolve(form.get('/Resources', resources))
                if not isinstance(nested, dict):
                    raise Unqualified('The Form resource context is unavailable')
                program([reference], nested, path | {reference})
            operands = []
        require(not operands, 'content-operands', 'Content stream ends with operands and no operator')

    def pages(reference, inherited, path):
        nonlocal page_nodes
        page_nodes += 1
        if page_nodes > 1000 or len(path) >= 128 or reference in path:
            raise Unqualified('The page tree exceeds content qualification bounds')
        page = graph.resolve(reference)
        if not isinstance(page, dict):
            raise Unqualified('The page tree context is unavailable')
        resources = graph.resolve(page.get('/Resources', inherited))
        if not isinstance(resources, dict):
            raise Unqualified('The page resource context is unavailable')
        kind = graph.resolve(page.get('/Type'))
        if kind == '/Pages':
            children = graph.resolve(page.get('/Kids'))
            if not isinstance(children, list):
                raise Unqualified('The page tree children are unavailable')
            for child in children:
                pages(child, resources, path | {reference})
        elif kind == '/Page':
            contents = page.get('/Contents')
            resolved = graph.resolve(contents)
            if resolved is not None:
                program(resolved if isinstance(resolved, list) else [contents], resources, set())
        else:
            raise Unqualified('Unknown page tree node kind')

    catalog = graph.resolve(graph.objects['trailer']['value'].get('/Root'))
    if not isinstance(catalog, dict):
        raise Unqualified('The content catalog is unavailable')
    pages(catalog.get('/Pages'), {}, set())
    return count


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
              'python-optimization-level': sys.flags.optimize,
              'python-executable-sha256': digest(Path(sys.executable).resolve())}
    wrapper = root / 'scripts/container-bin/qpdf'
    binary = Path(os.environ.get('QPDF_CACHE_DIRECTORY', str(root / '.build-cache/qpdf'))) / '12.4.0/bin/qpdf'
    pin = root / 'scripts/qpdf-pin.properties'
    try:
        result['input-sha256'] = digest(pdf)
        pin_before = digest(pin)
        if pin_before != QPDF_PIN_SHA:
            raise Unqualified('Pinned qpdf authority identity mismatch')
        values = dict(line.split('=', 1) for line in pin.read_text().splitlines() if line and not line.startswith('#'))
        if (values['QPDF_VERSION'] != '12.4.0' or values['QPDF_BINARY_SHA256'] != QPDF_SHA
                or values['QPDF_EXECUTABLE'] != 'container-bin/qpdf' or digest(wrapper) != WRAPPER_SHA or digest(binary) != QPDF_SHA):
            raise Unqualified('Pinned qpdf identity mismatch')
        result['qpdf-runtime-sha256'] = qpdf_runtime(root, binary)
        version = run([str(wrapper), '--version'], output, 'qpdf-version.txt').decode('ascii')
        if not version.startswith('qpdf version 12.4.0\n'):
            raise Unqualified('Observed qpdf version mismatch')
        result.update({'qpdf-version': '12.4.0', 'qpdf-binary-sha256': QPDF_SHA, 'qpdf-wrapper-sha256': WRAPPER_SHA})
        data = run([str(wrapper), '--json=2', '--json-stream-data=inline', '--decode-level=all', str(pdf)], output, 'qpdf.json')
        result['qpdf-json-sha256'] = hashlib.sha256(data).hexdigest()
        graph = Graph(json.loads(data, parse_float=Decimal))
        if scope == 'cmaps':
            result['observed-cmaps'] = check_cmaps(graph)
            result.update(standards='pass', finding='Decoded CMap grammar, character domains and declared inheritance are consistent.')
        elif scope == 'content':
            result['observed-content-streams'] = check_content(graph)
            result.update(standards='pass', finding='Page and nested Form content satisfy the qualified operand grammar.')
        elif scope in ('fonts', 'font-metrics'):
            result['observed-font-programs'], programs = check_font_programs(graph, root, result)
            if scope == 'font-metrics':
                result['observed-simple-font-metrics'] = check_simple_font_metrics(graph, programs)
                result['observed-cid-font-metrics'] = check_cid_font_metrics(graph, programs)
            result.update(standards='pass', finding='Embedded font programs satisfy the qualified layout, outline, name and bounds predicates.'
                          + (' Selected simple and CID advances agree with PDF widths.' if scope == 'font-metrics' else ''))
        else:
            raise Unqualified('Unknown program qualification scope')
    except Invalid as error:
        result.update(standards='fail', rule=error.rule, finding=str(error))
    except (Unqualified, OSError, ValueError, KeyError, TypeError, MemoryError, RecursionError, subprocess.TimeoutExpired) as error:
        result.update(standards='indeterminate', finding=str(error) or type(error).__name__)
    try:
        if result['input-sha256'] != 'unavailable' and (digest(pdf) != result['input-sha256'] or digest(wrapper) != WRAPPER_SHA or digest(binary) != QPDF_SHA
                or digest(pin) != pin_before or digest(Path(__file__)) != result['observer-sha256']):
            result.update(standards='indeterminate', finding='Input or checker identity changed during observation')
        if 'fonttools-wheel' in result and digest(Path(result['fonttools-wheel'])) != FONTTOOLS_SHA:
            result.update(standards='indeterminate', finding='fontTools wheel identity changed during observation')
        if 'qpdf-runtime-sha256' in result:
            qpdf_runtime(root, binary)
    except (OSError, Unqualified, UnboundLocalError):
        result.update(standards='indeterminate', finding='An input or checker identity is unavailable')
    def escaped(value):
        return str(value).replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '\\r')
    (output / 'result.properties').write_text(''.join(key + '=' + escaped(value) + '\n' for key, value in sorted(result.items())))


if __name__ == '__main__':
    if len(sys.argv) != 5:
        raise SystemExit('Usage: t13-program-standards.py <repository> <pdf> <scope> <fresh-output>')
    inspect(Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve(), sys.argv[3], Path(sys.argv[4]).resolve())
