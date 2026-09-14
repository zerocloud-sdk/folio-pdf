from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess
import sys

root = Path.cwd()
repro = Path(Path('/tmp/t75-unbalanced-worker-repro-path.txt').read_text().strip())
output = repro / 'split-green'
output.mkdir()
launcher = output / 'T75SelectedMethods.java'
launcher.write_text('''import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.internal.TextListener;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.manipulation.Filter;
public final class T75SelectedMethods {
    public static void main(String[] args) throws Exception {
        final Set<String> names = new HashSet<String>(Arrays.asList(args).subList(1, args.length));
        Request request = Request.aClass(Class.forName(args[0])).filterWith(new Filter() {
            public boolean shouldRun(Description description) {
                return !description.isTest() || names.contains(description.getMethodName());
            }
            public String describe() { return names.toString(); }
        });
        JUnitCore core = new JUnitCore();
        core.addListener(new TextListener(System.out));
        Result result = core.run(request);
        System.exit(result.wasSuccessful() && result.getRunCount() == names.size() ? 0 : 1);
    }
}
''')
test = root / 'pdf-document/src/test/java/net/zerocloud/pdf/consumer/TextStructureExtractionWorkflowTest.java'
(output / test.name).write_bytes(test.read_bytes())
base_compile = json.loads((repro / 'compile-command.json').read_text())
classes = Path(base_compile[-2]).parent / 'split-current'
classes.mkdir()
compile_command = base_compile[:-3] + ['-d', str(classes), str(launcher), str(test)]
(output / 'compile-command.json').write_text(json.dumps(compile_command, indent=2) + '\n')
with (output / 'compile.txt').open('xb') as stream:
    subprocess.run(compile_command, stdout=stream, stderr=subprocess.STDOUT, check=True)
base_command = json.loads((repro / 'worker-method-command.json').read_text())
index = base_command.index('-cp') + 1
base_command[index] = '/workspace/' + classes.relative_to(root).as_posix() + ':' + base_command[index]
main = base_command.index('T75SingleMethod')
base_command[main:] = ['T75SelectedMethods',
    'net.zerocloud.pdf.consumer.TextStructureExtractionWorkflowTest',
    'unbalancedSupportedOperatorStateCannotPublishPrefix',
    'unbalancedGraphicsAndPositioningStateCannotPublishPrefix',
    'unbalancedFormOperatorStateCannotPublishPrefix']
records = []
for mode in ('HARDENED_WORKER', 'IN_PROCESS'):
    command = ['-Dfolio.t13.executionProfile=' + mode
               if value == '-Dfolio.t13.executionProfile=HARDENED_WORKER' else value
               for value in base_command]
    started = datetime.now(timezone.utc).isoformat()
    with (output / (mode + '.txt')).open('xb') as stream:
        completed = subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT, timeout=60)
    record = {'execution-profile': mode, 'command': command, 'started-utc': started,
              'completed-utc': datetime.now(timezone.utc).isoformat(), 'returncode': completed.returncode,
              'current-test-source-sha256': hashlib.sha256(test.read_bytes()).hexdigest(),
              'scope': 'All three split methods, sixteen original workflows and eight fixtures, through unchanged staged product JARs and the pinned JDK8 image. Each test retains a 10000ms guard. This is focused development validation, not staged-candidate certification.'}
    records.append(record)
    (output / (mode + '.json')).write_text(json.dumps(record, indent=2) + '\n')
    print(mode, 'actual exit', completed.returncode, flush=True)
(output / 'observations.json').write_text(json.dumps(records, indent=2) + '\n')
sys.exit(1 if any(row['returncode'] for row in records) else 0)
