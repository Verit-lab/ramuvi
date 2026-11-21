"""v0.12.2 operational gates cannot be bypassed by a v0.12.1 attestation."""
import importlib.util
import json
import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
source = ROOT / "scripts/release/v011-readiness.py"
spec = importlib.util.spec_from_file_location("ramuvi_readiness_v0122", source)
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)
SHA = "b" * 40
TEMPLATE = ROOT / "docs/v0.12.2/RELEASE_ACCEPTANCE_TEMPLATE.json"
V0121 = ROOT / "docs/v0.12.1/RELEASE_ACCEPTANCE_TEMPLATE.json"
NEW = (
    "v73_representative_backup_restore_and_binary_rollback",
    "classroom_hub_session_lifecycle_pilot_teacher",
    "live_member_join_real_oidc_https_two_devices",
    "library_share_token_privacy_copyright_review",
    "personal_library_cross_device_lgpd_retention_decision",
    "v0122_flags_off_until_cohort_acceptance",
)


class ReleaseV0122ReadinessTests(unittest.TestCase):
    def complete(self, path=TEMPLATE):
        doc = json.loads(path.read_text())
        doc.update(candidateSha=SHA, decision="GO", approvedBy="Owner", approvedAt="2026-11-20T16:00:00Z")
        for gate in doc["gates"]:
            gate.update(status="PASS", evidenceRef="synthetic-ref", verifiedBy="Reviewer", verifiedAt="2026-11-20")
        return doc

    def test_template_never_self_approves(self):
        doc = json.loads(TEMPLATE.read_text())
        self.assertEqual(doc["version"], "0.12.2")
        self.assertEqual(doc["decision"], "NO-GO")
        self.assertEqual({g["id"] for g in doc["gates"]}, set(checker.V0122_REQUIRED))
        self.assertTrue(all(g["status"] == "PENDING" for g in doc["gates"]))
        self.assertTrue(set(checker.V0121_REQUIRED) < set(checker.V0122_REQUIRED))

    def test_every_new_gate_is_mandatory(self):
        self.assertEqual(checker.decide(self.complete(), SHA, "0.12.2"), [])
        for gate_id in NEW:
            doc = self.complete()
            doc["gates"] = [g for g in doc["gates"] if g["id"] != gate_id]
            self.assertTrue(checker.decide(doc, SHA, "0.12.2"), gate_id)
            doc = self.complete()
            next(g for g in doc["gates"] if g["id"] == gate_id)["status"] = "PENDING"
            self.assertTrue(checker.decide(doc, SHA, "0.12.2"), gate_id)

    def test_v0121_attestation_cannot_authorize_v0122(self):
        doc = self.complete(V0121)
        doc["version"] = "0.12.2"
        self.assertTrue(checker.decide(doc, SHA, "0.12.2"))

    def test_template_check_cli(self):
        result = subprocess.run(["python3", str(source), "--attestation", str(TEMPLATE),
                                 "--expected-version", "0.12.2", "--check-template"],
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
