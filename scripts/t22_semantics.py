#!/usr/bin/env python3
"""Observe every actual blank scale page through pinned independent qpdf JSON.

The source bytes are never shortened. Public Worker reopen observations are
retained by the scale tests separately; this observer has no product/backend API.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def blank_graph(graph, count):
    objects = graph['qpdf'][1]
    def value(item):
        if isinstance(item, str) and item.endswith(' R'):
            return objects['obj:' + item]['value']
        return item
    catalog = value(objects['trailer']['value']['/Root'])
    if catalog['/Type'] != '/Catalog':
        return False
    visited = set()
    leaves = []
    def walk(reference, parent=None):
        if reference in visited:
            raise ValueError('Cyclic or duplicate Page tree identity')
        visited.add(reference)
        node = value(reference)
        if parent is not None and node.get('/Parent') != parent:
            raise ValueError('Broken actual Parent relationship')
        if node['/Type'] == '/Pages':
            before = len(leaves)
            for kid in node['/Kids']:
                walk(kid, reference)
            if type(node['/Count']) is not int or node['/Count'] != len(leaves) - before:
                raise ValueError('Wrong actual subtree Count')
        elif node['/Type'] == '/Page':
            box = node.get('/MediaBox')
            if (box != [0, 0, 612, 792] or any(type(coordinate) not in (int, float) for coordinate in box)
                    or value(node.get('/Resources')) != {} or '/Contents' in node):
                raise ValueError('Scale page is not the declared resource-free blank page')
            leaves.append(reference)
        else:
            raise ValueError('Unexpected Page tree kind')
    walk(catalog['/Pages'])
    return len(leaves) == count and len(graph['pages']) == count


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('qpdf', type=Path)
    parser.add_argument('product', type=Path)
    parser.add_argument('count', type=int, choices=(1, 5000))
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    args.output.mkdir()
    before = digest(args.product)
    command = [str(args.qpdf), '--json=2', '--json-key=qpdf', '--json-key=pages', str(args.product)]
    process = subprocess.run(command, capture_output=True, timeout=300)
    (args.output / 'qpdf.json').write_bytes(process.stdout)
    (args.output / 'stderr.txt').write_bytes(process.stderr)
    passed = process.returncode == 0 and not process.stderr and before == digest(args.product)
    try:
        passed = passed and blank_graph(json.loads(process.stdout), args.count)
    except (ValueError, KeyError, TypeError):
        passed = False
    result = {'result': 'pass' if passed else 'fail', 'input-sha256': before,
              'page-count': args.count, 'all-pages-inspected': passed,
              'command': command, 'exit-code': process.returncode,
              'observer-sha256': digest(Path(__file__)), 'qpdf-sha256': digest(args.qpdf),
              'qpdf-json-sha256': digest(args.output / 'qpdf.json')}
    (args.output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    if not passed:
        raise ValueError('Actual scale artifact did not meet the complete blank-page graph predicates')


if __name__ == '__main__':
    main()
