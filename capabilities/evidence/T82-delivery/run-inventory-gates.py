"""Run the final inventory gates sequentially and retain every actual result."""
from pathlib import Path
import subprocess
import sys

delivery = Path(__file__).resolve().parent
root = delivery.parents[2]
for action in ('validate', 'generate', 'check', 'readiness'):
    command = [sys.executable, str(delivery / 'run-gate.py'),
               'inventory-' + action + '-final-r1', 'host', './scripts/inventory', action]
    result = subprocess.run(command, cwd=root)
    expected = 1 if action == 'readiness' else 0
    if result.returncode != expected:
        raise SystemExit('Unexpected final inventory result for ' + action + ': ' + str(result.returncode))
print('Inventory gates completed; readiness blockers require explicit audit.', flush=True)
