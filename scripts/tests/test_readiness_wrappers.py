"""Execute the PowerShell wrappers without builds, services, or real load.

Run: python -m unittest discover -s scripts/tests -p 'test_*.py' -v
"""

import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
POWERSHELL = shutil.which("powershell.exe")


@unittest.skipUnless(POWERSHELL, "Windows PowerShell is required")
class ReadinessWrapperTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="food-wrapper-tests-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.scripts = self.root / "scripts"
        self.scripts.mkdir()
        self.env = os.environ.copy()

    def run_script(self, script, *arguments):
        return subprocess.run(
            [POWERSHELL, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(script), *arguments],
            cwd=self.root,
            env=self.env,
            capture_output=True,
            text=True,
            timeout=30,
        )

    def prepare_readiness(self, gateway_present=False):
        shutil.copy2(ROOT / "scripts/verify-company-readiness.ps1", self.scripts)
        for name in (
            "use-java17-plus", "verify-microservice-boundaries", "verify-ddd-boundaries",
            "verify-database-migrations", "verify-operation-audit-ui", "verify-p0-readiness",
            "check-microservice-infra", "verify-nacos-configs", "smoke-test-local-microservices",
        ):
            (self.scripts / f"{name}.ps1").write_text(f"Write-Output 'CALLED {name}'\n", encoding="utf-8")
        runner = self.root / "readiness.ps1"
        connection = "[pscustomobject]@{ OwningProcess = 123 }" if gateway_present else "$null"
        runner.write_text(
            "function Get-NetTCPConnection {\n"
            "    param([int]$LocalPort, [string]$State)\n"
            f"    return {connection}\n"
            "}\n"
            "& (Join-Path $PSScriptRoot 'scripts/verify-company-readiness.ps1') "
            "-SkipMaven -SkipFrontend -SkipPython @args\n",
            encoding="utf-8",
        )
        return runner

    def test_requested_runtime_without_gateway_fails(self):
        result = self.run_script(self.prepare_readiness())
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("start-local-services.ps1", result.stderr)
        self.assertIn("SkipRuntimeSmoke", result.stderr)
        self.assertNotIn("Company readiness verification completed", result.stdout)

    def test_explicit_runtime_skip_succeeds_without_gateway(self):
        result = self.run_script(self.prepare_readiness(), "-SkipRuntimeSmoke")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertNotIn("CALLED check-microservice-infra", result.stdout)
        self.assertIn("Company readiness verification completed", result.stdout)

    def test_requested_runtime_executes_checks_with_gateway(self):
        result = self.run_script(self.prepare_readiness(gateway_present=True))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("CALLED check-microservice-infra", result.stdout)
        self.assertIn("CALLED verify-nacos-configs", result.stdout)
        self.assertIn("CALLED smoke-test-local-microservices", result.stdout)

    def prepare_k6(self, failing_scenario=""):
        shutil.copy2(ROOT / "scripts/run-k6-food-flows.ps1", self.scripts)
        native_dir = self.root / "fake-native"
        native_dir.mkdir(exist_ok=True)
        self.env["PATH"] = str(native_dir) + os.pathsep + os.environ["PATH"]
        self.env["TEST_K6_FAILURE"] = failing_scenario
        self.env["TEST_K6_CALLS"] = str(self.root / "calls.txt")
        (self.root / "calls.txt").write_text("", encoding="utf-8")
        (native_dir / "k6.cmd").write_text(
            f'@"{sys.executable}" "%~dp0fake_k6.py" %*\n', encoding="utf-8"
        )
        (native_dir / "fake_k6.py").write_text(
            "import os, pathlib, sys\n"
            "scenario = pathlib.Path(sys.argv[-1]).stem\n"
            "with open(os.environ['TEST_K6_CALLS'], 'a', encoding='utf-8') as log:\n"
            "    log.write(scenario + '\\n')\n"
            "sys.exit(23 if scenario == os.environ['TEST_K6_FAILURE'] else 0)\n",
            encoding="utf-8",
        )
        return self.scripts / "run-k6-food-flows.ps1"

    def k6_calls(self):
        return (self.root / "calls.txt").read_text(encoding="utf-8").splitlines()

    def test_k6_stops_on_each_failed_scenario(self):
        scenarios = ["normal-order", "group-buy-order", "seckill-order"]
        for position, scenario in enumerate(scenarios):
            with self.subTest(scenario=scenario):
                result = self.run_script(self.prepare_k6(scenario), "-AuthToken", "test-only-token")
                self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
                self.assertIn("23", result.stderr)
                self.assertIn(scenario, result.stderr)
                self.assertEqual(self.k6_calls(), scenarios[:position + 1])

    def test_k6_all_successful_scenarios_succeed(self):
        result = self.run_script(self.prepare_k6(), "-AuthToken", "test-only-token")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.k6_calls(), ["normal-order", "group-buy-order", "seckill-order"])


if __name__ == "__main__":
    unittest.main()
