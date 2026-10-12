"""Retain actual full-reactor and four-image matrix class execution counts."""
import hashlib
import json
from pathlib import Path
import re
import sys
root=Path(__file__).resolve().parents[3];delivery=Path(__file__).resolve().parent
sys.path.insert(0,str(root/'scripts'))
from t22_foundation_reports import require_exit_grace_observation
name=sys.argv[1]
gate=json.loads((delivery/'validation'/(name+'-result.json')).read_text())
assert gate['exit-code']==0
log=root/gate['log']['path'];text=re.sub(r'\x1b\[[0-9;]*m', '', log.read_text())
assert hashlib.sha256(log.read_bytes()).hexdigest()==gate['log']['sha256']
pattern=re.compile(r'^\[(?:INFO|WARNING)\] Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+).* -- in (\S+)$',re.MULTILINE)
def counts(transcript):
    rows=[dict(zip(('tests','failures','errors','skipped'),map(int,m[:4])),**{'class':m[4]}) for m in pattern.findall(transcript)]
    assert rows
    result={key:sum(row[key] for row in rows) for key in ('tests','failures','errors','skipped')}
    result.update({'recorded-class-executions':len(rows),'ordinary-skips':[row for row in rows if row['skipped']],
       'regression-executions':[row for row in rows if row['class']=='net.zerocloud.pdf.HardenedWorkerTerminationWaitTest']})
    assert result['failures']==result['errors']==0 and result['skipped']==4
    assert [(row['class'],row['skipped']) for row in result['ordinary-skips']] == [
        ('net.zerocloud.pdf.consumer.HardenedWorkerScaleProfileTest',3),
        ('net.zerocloud.pdf.acceptance.T30BarcodeEvidenceCommandTest',1)]
    assert 'BUILD SUCCESS' in transcript
    assert result['regression-executions']==[{'tests':8,'failures':0,'errors':0,'skipped':0,'class':'net.zerocloud.pdf.HardenedWorkerTerminationWaitTest'}]
    timing = {row['class']: row for row in rows if row['class'] in ('net.zerocloud.pdf.consumer.WorkflowElapsedTimeContractTest', 'net.zerocloud.pdf.WorkerInitializationTimingTest')}
    assert timing == {
        'net.zerocloud.pdf.consumer.WorkflowElapsedTimeContractTest': {'class': 'net.zerocloud.pdf.consumer.WorkflowElapsedTimeContractTest', 'tests': 6, 'failures': 0, 'errors': 0, 'skipped': 0},
        'net.zerocloud.pdf.WorkerInitializationTimingTest': {'class': 'net.zerocloud.pdf.WorkerInitializationTimingTest', 'tests': 1, 'failures': 0, 'errors': 0, 'skipped': 0}}
    result['monotonic-regression-executions'] = list(timing.values())
    result['actual-exit-grace-diagnostic']=require_exit_grace_observation(transcript)
    return result
if name=='jdk-matrix-final-r6':
    parts=re.split(r'(?m)^==> Verifying Folio PDF on JDK (8|11|17|21) \([^\n]+\)\n',text)
    assert len(parts)==9 and parts[1::2]==['8','11','17','21']
    result={'gate':gate['log'],'environments':[{'jdk-major':int(parts[i]),**counts(parts[i+1])} for i in (1,3,5,7)],
            'scope':'Actual original verify-jdk-matrix.sh reactor executions; ordinary opt-in skips do not qualify scale.'}
else:
    assert name=='full-verify-final-r13'
    result={**counts(text),'log':gate['log'],
            'scope':'Full ordinary reactor verification; repeated integration class executions count as executed. Opt-in CI skips are not recovery certification.'}
p=delivery/'validation'/(name+'-tests.json');assert not p.exists();p.write_text(json.dumps(result,indent=2)+'\n')
print(name+': actual class execution counts retained; regression passed without skip.')
