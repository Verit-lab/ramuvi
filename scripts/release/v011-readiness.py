#!/usr/bin/env python3
"""Fail-closed v0.11 release readiness decision; never creates tags or deploys.

CI --check-template validates that operational gates remain explicitly pending.
With --attestation, GO is possible only with a named owner, a matching exact
candidate SHA, all named field validations and a successful CI run for that SHA.
This checks evidence references, not the truth of physical measurements.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

REQUIRED = (
    "ios_safari_physical",
    "android_pwa_upgrade",
    "voiceover_keyboard_zoom",
    "network_degraded_two_projectors",
    "teacher_pedagogical_pilot",
    "backup_restore_rollback",
    "security_privacy_authorization",
    "release_owner_go_nogo",
)
V012_REQUIRED = REQUIRED + (
    "student_oidc_disabled_or_verified_providers",
    "v68_representative_restore",
    "public_https_routing_and_secure_session",
)
V0121_REQUIRED = V012_REQUIRED + (
    "offline_journey_safari_android_cold_start_sync_revocation",
    "onboarding_verified_google_scoped_join_link_xp_continuity",
    "v71_v72_representative_backup_restore",
    "legacy_device_claim_revocation_rollout_decision",
    "join_link_google_oidc_https_csrf_and_teacher_approval",
    "teacher_classroom_authorization_scope_decision",
)

V0122_REQUIRED = V0121_REQUIRED + (
    "v73_representative_backup_restore_and_binary_rollback",
    "classroom_hub_session_lifecycle_pilot_teacher",
    "live_member_join_real_oidc_https_two_devices",
    "library_share_token_privacy_copyright_review",
    "personal_library_cross_device_lgpd_retention_decision",
    "v0122_flags_off_until_cohort_acceptance",
)

def required_for_version(version: str) -> tuple[str, ...]:
    if version == "0.12.2":
        return V0122_REQUIRED
    if version == "0.12.1":
        return V0121_REQUIRED
    if version == "0.12.0":
        return V012_REQUIRED
    return REQUIRED


CI_JOBS = (
    "Backend — Java 21, PostgreSQL and integration tests",
    "Frontend — tests, TypeScript and production build",
    "Compose and E2E — Chromium critical flows",
)


def decide(document: dict, candidate: str, expected_version: str = "0.11.1") -> list[str]:
    problems: list[str] = []
    if not re.fullmatch(r"[0-9a-f]{40}", candidate):
        return ["Candidate SHA must be full 40-character Git commit SHA"]
    if document.get("version") != expected_version:
        problems.append(f"Release attestation version must be {expected_version}")
    if document.get("candidateSha") != candidate:
        problems.append("Attestation candidateSha differs from the tested SHA")
    if document.get("decision") != "GO":
        problems.append("Release decision is not GO")
    if not str(document.get("approvedBy") or "").strip():
        problems.append("Missing named release owner")
    if not str(document.get("approvedAt") or "").strip():
        problems.append("Missing release decision timestamp")
    gates = document.get("gates")
    if not isinstance(gates, list):
        return problems + ["Attestation gates must be an array"]
    required = required_for_version(expected_version)
    lookup = {g.get("id"): g for g in gates if isinstance(g, dict)}
    if len(lookup) != len(gates) or set(lookup) != set(required):
        problems.append("Attestation requires exactly the documented operational gates")
    for gate in required:
        evidence = lookup.get(gate) or {}
        if evidence.get("status") != "PASS":
            problems.append(f"{gate}: not marked PASS")
        if not str(evidence.get("evidenceRef") or "").strip():
            problems.append(f"{gate}: missing evidence reference")
        if not str(evidence.get("verifiedBy") or "").strip():
            problems.append(f"{gate}: missing named tester/reviewer")
        if not str(evidence.get("verifiedAt") or "").strip():
            problems.append(f"{gate}: missing verification timestamp")
    return problems


def gh_verify(repo: str, sha: str, run_id: str) -> list[str]:
    if not run_id.isdigit():
        return ["CI run id must be numeric"]
    try:
        raw = subprocess.check_output(
            ["gh", "run", "view", run_id, "--repo", repo,
             "--json", "headSha,status,conclusion,jobs"],
            text=True, timeout=30, stderr=subprocess.PIPE,
        )
        run = json.loads(raw)
    except (subprocess.CalledProcessError, OSError, ValueError,
            subprocess.TimeoutExpired) as exc:
        return [f"GitHub CI verification unavailable: {type(exc).__name__}"]
    issues = []
    if run.get("headSha") != sha:
        issues.append("CI run head SHA does not match attested candidate")
    if run.get("status") != "completed" or run.get("conclusion") != "success":
        issues.append("CI run not completed successfully")
    passed = {job["name"] for job in run.get("jobs", [])
              if job.get("status") == "completed"
              and job.get("conclusion") == "success"}
    for name in CI_JOBS:
        if name not in passed:
            issues.append(f"Missing green CI gate: {name}")
    return issues


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--attestation", required=True, type=Path)
    parser.add_argument("--candidate", help="Full 40-character exact candidate SHA")
    parser.add_argument("--ci-run-id", help="Required to claim GO")
    parser.add_argument("--repo", default="Verit-lab/ramuvi")
    parser.add_argument("--expected-version", choices=("0.11.1", "0.12.0", "0.12.1", "0.12.2"), default="0.11.1")
    parser.add_argument("--check-template", action="store_true",
                        help="CI check: template must be deliberately incomplete")
    args = parser.parse_args()
    try:
        doc = json.loads(args.attestation.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        print(f"NO-GO: invalid attestation: {type(exc).__name__}")
        return 2
    if args.check_template:
        gates = doc.get("gates") or []
        ids = [g.get("id") for g in gates if isinstance(g, dict)]
        required = required_for_version(args.expected_version)
        if (len(ids) != len(required) or set(ids) != set(required)
                or (args.expected_version in ("0.12.0", "0.12.1", "0.12.2")
                    and doc.get("version") != args.expected_version)
                or doc.get("decision") != "NO-GO"
                or any(g.get("status") != "PENDING" for g in gates)
                or doc.get("approvedBy")
                or doc.get("candidateSha")):
            print("INVALID TEMPLATE: operational evidence must start PENDING")
            return 2
        print("TEMPLATE VALID: mandatory operational gates start NO-GO")
        return 0
    problems = decide(doc, args.candidate or "", expected_version=args.expected_version)
    if not args.ci_run_id:
        problems.append("Missing CI run ID tied to exact candidate SHA")
    elif not problems:
        problems.extend(gh_verify(args.repo, args.candidate, args.ci_run_id))
    if problems:
        print(f"NO-GO for v{args.expected_version} — release not authorized:")
        for issue in problems:
            print(f"  - {issue}")
        return 2
    print("ELIGIBLE FOR HUMAN RELEASE DECISION: conditions recorded and CI confirmed.")
    print("No tag, deploy or release is created by this tool.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
