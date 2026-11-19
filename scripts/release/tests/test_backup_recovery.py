"""Contract tests for backup safety. Fake age/rclone DO NOT test encryption strength."""
from __future__ import annotations

import hashlib
import os
import pathlib
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]
SCRIPTS = ROOT / "scripts" / "release"

FAKE_DOCKER = r"""#!/usr/bin/env python3
import os, sys
a=sys.argv[1:]
with open(os.environ["MOCK_CALL_LOG"],"a") as f: f.write("docker "+ " ".join(a)[:100] +"\n")
if a[:2]==["compose","version"] or a[:1]==["info"]: exit(0)
if a[:2]==["compose","ps"]: print("isolated-pg-id"); exit(0)
if a[:4]==["compose","exec","-T","postgres"]:
 sub=a[4:]
 if sub[:1]==["sh"]:
  print("db_test" if "POSTGRES_DB" in " ".join(sub) else "teacher")
  exit(0)
 if sub[:1]==["pg_dump"]:
  sys.stdout.buffer.write(b"PGDMP\\x00mock-fixture-no-personal-data")
  sys.stdout.buffer.flush()
  if os.environ.get("MOCK_PGDUMP_FAIL")=="1":exit(12)
  exit(0)
 if sub[:2]==["pg_restore","--list"]:
  data=sys.stdin.buffer.read()
  if data.startswith(b"PGDMP"):exit(0)
  exit(9)
print("unexpected fake Docker invocation",a,file=sys.stderr)
exit(4)
"""

FAKE_AGE = r"""#!/usr/bin/env python3
import os, sys, pathlib
args=sys.argv[1:]
if args[:1]!=["--recipient"] or len(args)!=4 or args[2]!="--output":exit(3)
output=pathlib.Path(args[3])
data=sys.stdin.buffer.read()
output.write_bytes(b"AGE-FAKE-TEST-ONLY:"+data)
if os.environ.get("MOCK_AGE_FAIL")=="1":exit(23)
"""

FAKE_RCLONE = r"""#!/usr/bin/env python3
import os, sys, pathlib, shutil, hashlib
args=sys.argv[1:]
remote=pathlib.Path(os.environ["MOCK_REMOTE_DIR"])
if args[0]=="copyto":
 if os.environ.get("MOCK_RCLONE_FAIL")=="copy":exit(8)
 source=pathlib.Path(args[1])
 target=remote / args[2].split(":",1)[1].lstrip("/")
 target.parent.mkdir(parents=True,exist_ok=True)
 shutil.copyfile(source,target)
 exit(0)
if args[0]=="check":
 if os.environ.get("MOCK_RCLONE_FAIL")=="check":exit(9)
 manifest=pathlib.Path(args[args.index("--files-from")+1])
 local=pathlib.Path(args[-2])
 root=remote / args[-1].split(":",1)[1].lstrip("/")
 for name in manifest.read_text().splitlines():
  local_file=local/name; remote_file=root/name
  if not remote_file.exists() or hashlib.sha256(local_file.read_bytes()).digest()!=hashlib.sha256(remote_file.read_bytes()).digest():exit(10)
 exit(0)
exit(11)
"""


class BackupSafetyTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="ramuvi-recovery-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        for name, content in (("docker", FAKE_DOCKER), ("age", FAKE_AGE), ("rclone", FAKE_RCLONE)):
            path = self.bin / name
            path.write_text(content)
            path.chmod(0o755)
        self.backups = self.root / "backups"
        self.remote = self.root / "remote"
        self.log = self.root / "calls.log"
        self.env = {
            **os.environ,
            "PATH": str(self.bin) + os.pathsep + os.environ["PATH"],
            "BACKUP_DIR": str(self.backups),
            "MOCK_CALL_LOG": str(self.log),
            "MOCK_REMOTE_DIR": str(self.remote),
            "BACKUP_KEEP": "0",
            "BACKUP_REQUIRE_ENCRYPTION": "0",
        }
        for key in ("MOCK_PGDUMP_FAIL", "MOCK_AGE_FAIL", "MOCK_RCLONE_FAIL",
                    "BACKUP_AGE_RECIPIENT", "BACKUP_OFFSITE_REMOTE",
                    "BACKUP_ALLOW_UNVERIFIED", "BACKUP_REQUIRE_CHECKSUM",
                    "BACKUP_KEEP_LOCAL", "BACKUP_AGE_IDENTITY"):
            self.env.pop(key, None)

    def run_script(self, name, *args, env=None):
        return subprocess.run(
            ["bash", str(SCRIPTS / name), *map(str, args)],
            cwd=ROOT, env=env or self.env, capture_output=True, text=True,
            timeout=30,
        )

    def archives(self):
        return list(self.backups.glob("ramuvi-*.dump*")) if self.backups.exists() else []

    def test_requires_encryption_when_strict_and_creates_nothing(self):
        env = {**self.env, "BACKUP_REQUIRE_ENCRYPTION": "1"}
        result = self.run_script("backup-postgres.sh", env=env)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("BACKUP_AGE_RECIPIENT", result.stderr)
        self.assertFalse(self.archives())

    def test_plaintext_legacy_backup_requires_no_strict_flag_and_is_verified(self):
        result = self.run_script("backup-postgres.sh")
        self.assertEqual(result.returncode, 0, result.stderr)
        archive = pathlib.Path(result.stdout.strip())
        self.assertTrue(archive.name.startswith("ramuvi-db_test-"))
        self.assertEqual(archive.suffix, ".dump")
        self.assertTrue(archive.read_bytes().startswith(b"PGDMP"))
        self.assertEqual(archive.stat().st_mode & 0o777, 0o600)
        self.assertTrue(archive.with_suffix(".dump.sha256").exists())
        self.assertNotIn(b"AGE-FAKE", archive.read_bytes())

    def test_encrypted_stream_never_creates_plaintext_file(self):
        env = {**self.env, "BACKUP_REQUIRE_ENCRYPTION": "1",
               "BACKUP_AGE_RECIPIENT": "age1publiconly"}
        result = self.run_script("backup-postgres.sh", env=env)
        self.assertEqual(result.returncode, 0, result.stderr)
        archive = pathlib.Path(result.stdout.strip())
        self.assertEqual(archive.suffix, ".age")
        self.assertTrue(archive.read_bytes().startswith(b"AGE-FAKE-TEST-ONLY"))
        self.assertEqual(sorted(x.name for x in self.archives()),
                         sorted([archive.name, archive.name + ".sha256"]))
        expected = hashlib.sha256(archive.read_bytes()).hexdigest()
        self.assertIn(expected, pathlib.Path(str(archive) + ".sha256").read_text())
        self.assertEqual(archive.stat().st_mode & 0o777, 0o600)

    def test_failed_encryption_never_publishes_partial_archive(self):
        env = {**self.env, "BACKUP_AGE_RECIPIENT": "age1publiconly",
               "MOCK_AGE_FAIL": "1"}
        result = self.run_script("backup-postgres.sh", env=env)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.archives(), [])

    def test_failed_dump_pipe_is_detected_even_when_encryptor_exits_zero(self):
        env = {**self.env, "BACKUP_AGE_RECIPIENT": "age1publiconly",
               "MOCK_PGDUMP_FAIL": "1"}
        result = self.run_script("backup-postgres.sh", env=env)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.archives(), [])

    def test_restore_refuses_missing_or_corrupt_checksum_before_using_docker(self):
        self.backups.mkdir()
        path = self.backups / "synthetic.dump"
        path.write_bytes(b"PGDMP synthetic")
        missing = self.run_script("restore-postgres.sh", path, "--confirm-replace")
        self.assertNotEqual(missing.returncode, 0)
        self.assertIn("checksum SHA-256 ausente", missing.stderr)
        path.with_suffix(".dump.sha256").write_text("0" * 64 + "  synthetic.dump\n")
        corrupt = self.run_script("restore-postgres.sh", path, "--confirm-replace")
        self.assertNotEqual(corrupt.returncode, 0)
        self.assertIn("checksum SHA-256 inválido", corrupt.stderr)
        self.assertFalse(self.log.exists(), "Restore invoked docker before integrity gate")

    def test_verify_script_can_require_checksum_before_starting_container(self):
        self.backups.mkdir()
        path = self.backups / "synthetic.dump"
        path.write_bytes(b"PGDMP synthetic")
        result = self.run_script("verify-backup.sh", path,
                                 env={**self.env, "BACKUP_REQUIRE_CHECKSUM": "1"})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("checksum SHA-256 obrigatório ausente", result.stderr)
        self.assertNotIn("docker run", self.log.read_text())

    def test_offsite_rejects_missing_destination_before_backup(self):
        result = self.run_script("backup-offsite.sh",
                                 env={**self.env, "BACKUP_AGE_RECIPIENT": "age1publiconly"})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("BACKUP_OFFSITE_REMOTE", result.stderr)
        self.assertFalse(self.archives())

    def test_offsite_uploads_ciphertext_and_sha_and_verifies_both(self):
        env = {**self.env, "BACKUP_AGE_RECIPIENT": "age1publiconly",
               "BACKUP_OFFSITE_REMOTE": "test:ramuvi/production"}
        result = self.run_script("backup-offsite.sh", env=env)
        self.assertEqual(result.returncode, 0, result.stderr)
        archive = pathlib.Path(result.stdout.strip())
        self.assertEqual(archive.suffix, ".age")
        remote = self.remote / "ramuvi/production"
        self.assertEqual((remote / archive.name).read_bytes(), archive.read_bytes())
        self.assertEqual((remote / (archive.name + ".sha256")).read_bytes(),
                         pathlib.Path(str(archive) + ".sha256").read_bytes())
        self.assertNotIn("decrypt", self.log.read_text())

    def test_offsite_error_preserves_local_encrypted_recovery_point(self):
        env = {**self.env, "BACKUP_AGE_RECIPIENT": "age1publiconly",
               "BACKUP_OFFSITE_REMOTE": "test:ramuvi/production",
               "MOCK_RCLONE_FAIL": "check"}
        result = self.run_script("backup-offsite.sh", env=env)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("verificação externa falhou", result.stderr)
        self.assertTrue(list(self.backups.glob("ramuvi-*.dump.age")))
        self.assertTrue(list(self.backups.glob("ramuvi-*.dump.age.sha256")))


if __name__ == "__main__":
    unittest.main()
