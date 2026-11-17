"""Fail-closed v0.12 production readiness contract regression tests."""
import importlib.util
import json
import unittest
from pathlib import Path

SOURCE = Path(__file__).resolve().parents[1] / "v011-readiness.py"
spec = importlib.util.spec_from_file_location("readiness_v012", SOURCE)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)
SHA = "b" * 40
TEMPLATE = Path(__file__).resolve().parents[3] / "docs/v0.12/RELEASE_ACCEPTANCE_TEMPLATE.json"


def complete():
    return {
        "version": "0.12.0",
        "candidateSha": SHA,
        "decision": "GO",
        "approvedBy": "Release owner",
        "approvedAt": "2026-11-17T20:00:00-03:00",
        "gates": [
            {"id": name, "status": "PASS", "evidenceRef": "validated-evidence-ref",
             "verifiedBy": "Reviewer", "verifiedAt": "2026-11-17"}
            for name in gate.V012_REQUIRED
        ],
    }


class ReleaseV012ReadinessTests(unittest.TestCase):
    def test_template_remains_pending_and_not_a_real_approval(self):
        doc = json.loads(TEMPLATE.read_text(encoding="utf8"))
        self.assertEqual(doc["decision"], "NO-GO")
        self.assertEqual({g["id"] for g in doc["gates"]}, set(gate.V012_REQUIRED))
        self.assertTrue(all(g["status"] == "PENDING" for g in doc["gates"]))
        self.assertTrue(gate.decide(doc, SHA, "0.12.0"))

    def test_stale_v011_evidence_cannot_approve_v012(self):
        doc = complete()
        doc["version"] = "0.11.1"
        problems = gate.decide(doc, SHA, "0.12.0")
        self.assertTrue(any("version" in p for p in problems))

    def test_oidc_https_or_v68_gates_cannot_be_skipped(self):
        for name in gate.V012_REQUIRED[len(gate.REQUIRED):]:
            with self.subTest(name=name):
                doc = complete()
                doc["gates"] = [g for g in doc["gates"] if g["id"] != name]
                self.assertTrue(gate.decide(doc, SHA, "0.12.0"))

    def test_v012_requires_named_evidence_and_matching_sha(self):
        doc = complete()
        self.assertEqual(gate.decide(doc, SHA, "0.12.0"), [])
        doc["gates"][-1]["evidenceRef"] = ""
        self.assertTrue(gate.decide(doc, SHA, "0.12.0"))
        self.assertTrue(gate.decide(complete(), "c" * 40, "0.12.0"))

    def test_previous_v011_contract_remains_unchanged(self):
        self.assertEqual(len(gate.REQUIRED), 8)
        self.assertTrue(gate.decide(complete(), SHA))


if __name__ == "__main__":
    unittest.main()
