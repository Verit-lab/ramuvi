"""v0.12.1 operational gates cannot be bypassed by old attestation."""
import importlib.util
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
source = ROOT / "scripts/release/v011-readiness.py"
spec = importlib.util.spec_from_file_location("ramuvi_readiness_v0121", source)
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)
SHA = "a" * 40
TEMPLATE = ROOT / "docs/v0.12.1/RELEASE_ACCEPTANCE_TEMPLATE.json"

class ReleaseV0121ReadinessTests(unittest.TestCase):
    def complete(self):
        doc = json.loads(TEMPLATE.read_text())
        doc.update(candidateSha=SHA, decision="GO", approvedBy="Owner", approvedAt="2026-11-19T16:00:00Z")
        for gate in doc["gates"]:
            gate.update(status="PASS", evidenceRef="synthetic-ref", verifiedBy="Reviewer", verifiedAt="2026-11-19")
        return doc

    def test_template_never_self_approves(self):
        doc = json.loads(TEMPLATE.read_text())
        self.assertEqual(doc["decision"], "NO-GO")
        self.assertEqual(doc["version"], "0.12.1")
        self.assertEqual({v["id"] for v in doc["gates"]}, set(checker.V0121_REQUIRED))
        self.assertTrue(all(v["status"] == "PENDING" for v in doc["gates"]))

    def test_new_offline_gate_is_required(self):
        doc = self.complete()
        self.assertEqual(checker.decide(doc,SHA,"0.12.1"), [])
        doc["gates"] = [v for v in doc["gates"] if v["id"] != "offline_journey_safari_android_cold_start_sync_revocation"]
        self.assertTrue(checker.decide(doc,SHA,"0.12.1"))

    def test_old_v012_evidence_cannot_approve_v0121(self):
        doc = self.complete()
        self.assertTrue(checker.decide(doc,SHA,"0.12.0"))
        doc["version"] = "0.12.0"
        self.assertTrue(checker.decide(doc,SHA,"0.12.1"))

    def test_missing_physical_evidence_and_sha_block(self):
        doc = self.complete()
        doc["gates"][-1]["evidenceRef"] = ""
        self.assertTrue(checker.decide(doc,SHA,"0.12.1"))
        self.assertTrue(checker.decide(self.complete(),"b"*40,"0.12.1"))
