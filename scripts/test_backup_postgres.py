"""Linux-only, standard-library regression tests; no Docker/database calls."""

import importlib.util
import io
import json
import os
from contextlib import redirect_stdout
from datetime import datetime, timedelta
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import patch
from zoneinfo import ZoneInfo

SPEC = importlib.util.spec_from_file_location("backup_postgres", Path(__file__).with_name("backup-postgres.py"))
backup = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(backup)


class BackupTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="doneit-backup-unit-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name) / "backups"
        self.config = backup.arguments(["--backup-dir", str(self.root)])
        self.now = datetime(2026, 11, 1, 3, 0, tzinfo=ZoneInfo("Europe/Moscow"))
        self.payload = b"PGDMP fixture bytes"
        self.fail_operation = None
        self.dump_calls = 0
        self.calls = []
        command = patch.object(backup, "command", side_effect=self.fake_command)
        command.start()
        self.addCleanup(command.stop)
        space = patch.object(backup.shutil, "disk_usage", return_value=SimpleNamespace(free=5 * 1024**3))
        self.space = space.start()
        self.addCleanup(space.stop)

    def fake_command(self, args, operation, **kwargs):
        self.calls.append(operation)
        if operation == self.fail_operation:
            raise backup.BackupError(operation + " failed")
        if operation == "database size query":
            return SimpleNamespace(stdout=b"1024\n")
        if operation == "pg_dump":
            self.dump_calls += 1
            kwargs["stdout"].write(self.payload)
        if operation == "pg_restore list":
            self.assertTrue(kwargs["stdin"].read().startswith(b"PGDMP"))
        return SimpleNamespace(returncode=0)

    def run_backup(self, now=None):
        with redirect_stdout(io.StringIO()):
            return backup.run(self.config, now or self.now)

    def archives(self):
        return sorted(self.root.glob("*.dump"))

    def test_success_checksum_permissions_and_temporary_cleanup(self):
        self.assertEqual(self.run_backup(), 0)
        path, = self.archives()
        self.assertIn("2026-11-01T030000+0300_Europe-Moscow", path.name)
        self.assertTrue(backup.verified_archive(path))
        self.assertEqual(self.root.stat().st_mode & 0o777, 0o700)
        for child in self.root.iterdir():
            self.assertEqual(child.stat().st_mode & 0o777, 0o600)
        self.assertFalse(list(self.root.glob("*.partial")))
        entry = json.loads((self.root / "backup.log").read_text().splitlines()[-1])
        self.assertEqual(entry["result"], "OK")
        self.assertEqual(entry["bytes"], len(self.payload))

    def test_rotation_retains_14_days_and_manual_runs_do_not_displace_yesterday(self):
        for day in range(17):
            self.assertEqual(self.run_backup(self.now + timedelta(days=day)), 0)
        self.assertEqual(len(self.archives()), 14)
        self.assertEqual({p.name[7:17] for p in self.archives()},
                         {(self.now + timedelta(days=d)).date().isoformat() for d in range(3, 17)})
        for second in range(1, 5):
            self.assertEqual(self.run_backup(self.now + timedelta(days=16, seconds=second)), 0)
        self.assertEqual(len(self.archives()), 14)
        self.assertEqual(len(list(self.root.glob("*.sha256"))), 14)

    def test_database_dump_and_toc_failures_preserve_previous_backups(self):
        self.assertEqual(self.run_backup(), 0)
        old = self.archives()
        for operation in ["database size query", "pg_dump", "pg_restore list"]:
            self.fail_operation = operation
            self.assertEqual(self.run_backup(self.now + timedelta(days=1)), 1)
            self.assertEqual(self.archives(), old)
            self.assertTrue(backup.verified_archive(old[0]))
            self.assertFalse(list(self.root.glob("*.partial")))

    def test_insufficient_space_does_not_dump_or_rotate(self):
        self.assertEqual(self.run_backup(), 0)
        old = self.archives()
        calls = self.dump_calls
        self.space.return_value = SimpleNamespace(free=1)
        self.assertEqual(self.run_backup(self.now + timedelta(days=1)), 1)
        self.assertEqual(self.dump_calls, calls)
        self.assertEqual(self.archives(), old)

    def test_free_space_drop_and_empty_dump_are_not_published(self):
        self.payload = b""
        self.assertEqual(self.run_backup(), 1)
        self.assertFalse(self.archives())
        self.payload = b"PGDMP fixture"
        self.space.side_effect = [SimpleNamespace(free=5 * 1024**3), SimpleNamespace(free=1)]
        self.assertEqual(self.run_backup(), 1)
        self.assertFalse(self.archives())
        self.assertFalse(list(self.root.glob("*.sha256")))

    def test_publish_failure_removes_orphan_checksum_and_keeps_last_good(self):
        self.assertEqual(self.run_backup(), 0)
        old = self.archives()
        original = Path.replace
        def fail_dump(path, target):
            if Path(target).suffix == ".dump":
                raise OSError("simulated rename error")
            return original(path, target)
        with patch.object(Path, "replace", fail_dump):
            self.assertEqual(self.run_backup(self.now + timedelta(days=1)), 1)
        self.assertEqual(self.archives(), old)
        self.assertEqual(len(list(self.root.glob("*.sha256"))), 1)
        self.assertFalse(list(self.root.glob("*.partial")))

    def test_scheduled_moscow_time_duplicate_guard_and_manual_before_schedule(self):
        self.config.scheduled = True
        self.assertEqual(self.run_backup(self.now.replace(hour=2)), 0)
        self.assertFalse(self.root.exists())
        self.assertEqual(self.run_backup(), 0)
        self.assertEqual(self.run_backup(), 0)
        self.assertEqual(self.dump_calls, 1)
        self.assertEqual(self.run_backup(self.now + timedelta(days=1)), 0)
        self.assertEqual(self.dump_calls, 2)
        self.config.scheduled = False
        self.assertEqual(self.run_backup(self.now + timedelta(days=2, hours=-1)), 0)
        self.config.scheduled = True
        self.assertEqual(self.run_backup(self.now + timedelta(days=2)), 0)
        self.assertEqual(self.dump_calls, 4)
        self.assertEqual(len(self.archives()), 3)

    def test_overlapping_runs_cannot_dump(self):
        root = backup.prepare_directory(self.root)
        with backup.exclusive_lock(root):
            self.assertEqual(self.run_backup(), 75)
            self.config.scheduled = True
            self.assertEqual(self.run_backup(), 0)
        self.assertEqual(self.dump_calls, 0)

    def test_invalid_files_do_not_count_as_successful_backups_or_get_deleted(self):
        self.assertEqual(self.run_backup(), 0)
        invalid = self.archives()[0]
        invalid.write_bytes(b"CORRUPTED")
        partial = self.root / ".abandoned.partial"
        partial.write_bytes(b"unfinished")
        self.config.keep_days = 1
        self.assertEqual(self.run_backup(self.now + timedelta(days=1)), 0)
        self.assertTrue(invalid.exists())
        self.assertTrue(partial.exists())
        self.assertEqual(sum(backup.verified_archive(p) for p in self.archives()), 1)

    def test_log_rotation_and_rejection_of_symlink_or_repository_directory(self):
        self.root.mkdir()
        (self.root / "backup.log").write_text("x" * (backup.LOG_LIMIT + 1))
        self.assertEqual(self.run_backup(), 0)
        self.assertTrue((self.root / "backup.log.1").exists())
        self.assertEqual((self.root / "backup.log.1").stat().st_mode & 0o777, 0o600)
        self.assertLess((self.root / "backup.log").stat().st_size, 1024)
        link = Path(self.temporary.name) / "linked-backups"
        link.symlink_to(self.root, target_is_directory=True)
        with self.assertRaises(backup.BackupError):
            backup.prepare_directory(link)
        with self.assertRaises(backup.BackupError):
            backup.prepare_directory(Path(__file__).resolve().parent / "unsafe-backups")
        with self.assertRaises(backup.BackupError):
            backup.prepare_directory(Path.home() / "doneit-secrets/acme/unsafe-backups")
        with self.assertRaises(backup.BackupError):
            backup.prepare_directory(Path("/tmp"))


class CommandPrivacyTest(unittest.TestCase):
    def test_command_does_not_log_stderr_or_arguments(self):
        with patch.object(backup.subprocess, "run", return_value=SimpleNamespace(returncode=1, stderr=b"SECRET")) as run:
            with self.assertRaisesRegex(backup.BackupError, r"pg_dump failed \(exit 1\)") as error:
                backup.command(["tool", "SECRET"], "pg_dump")
            self.assertNotIn("SECRET", str(error.exception))
            self.assertEqual(run.call_args.kwargs["stderr"], backup.subprocess.DEVNULL)


if __name__ == "__main__":
    unittest.main()
