"""Metadata-only runner. Optional explicit launcher for OEM background-launch restrictions."""
import os
from pathlib import Path
import subprocess

serial = os.environ["ANDROID_SERIAL"]
adb = ["adb", "-s", serial]
launch = os.environ.get("NEXUS_SHELL_LAUNCH") == "1"
process = subprocess.Popen(adb + ["shell", "am", "instrument", "-w", "-r",
    "org.nexus.lab.test/androidx.test.runner.AndroidJUnitRunner"],
    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
current_test = ""
with Path("build/device-instrumentation.log").open("w") as output:
    for line in process.stdout:
        print(line, end="", flush=True)
        output.write(line)
        output.flush()
        if line.startswith("INSTRUMENTATION_STATUS: test="):
            current_test = line.split("=", 1)[1].strip()
        if launch and line.strip() == "INSTRUMENTATION_STATUS_CODE: 1" and current_test.startswith("realCamera"):
            # Same public launcher intent used by ActivityScenario. Does not grant an
            # OEM background-launch permission, change keyguard, or change app code.
            result = subprocess.run(adb + ["shell", "am", "start", "-W", "-a",
                "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER",
                "-f", "0x10008000", "-n", "org.nexus.lab/.MainActivity"],
                check=True, capture_output=True, text=True)
            print(result.stdout, flush=True)
            output.write("EXPLICIT_SHELL_LAUNCH:\n" + result.stdout)
            output.flush()
code = process.wait()
text = Path("build/device-instrumentation.log").read_text()
if code != 0 or "FAILURES!!!" in text or "INSTRUMENTATION_FAILED" in text or "OK (" not in text:
    raise SystemExit("Instrumentation did not pass; inspect build/device-instrumentation.log")
