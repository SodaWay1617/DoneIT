#!/usr/bin/env python3
"""Private PostgreSQL custom dumps via an existing Docker container (Linux, Python 3.10+)."""

import argparse
from contextlib import contextmanager
from datetime import datetime
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import uuid
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

MIB = 1024 * 1024
LOG_LIMIT = MIB
ARCHIVE = re.compile(r"^doneit_(\d{4}-\d{2}-\d{2})T\d{6}[+-]\d{4}_[A-Za-z0-9_-]+_[a-f0-9]{32}\.dump$")


class BackupError(Exception):
    """Only controlled, credential-free messages may be used here."""


class BackupBusy(BackupError):
    pass


def secure_open(path, flags):
    return os.open(path, flags | os.O_NOFOLLOW, 0o600)


def prepare_directory(path):
    path = path.expanduser().absolute()
    repository = Path(__file__).resolve().parent.parent
    if path.is_symlink():
        raise BackupError("backup directory must not be a symlink")
    resolved = path.resolve()
    protected = {Path(p) for p in ["/", "/home", "/root", "/tmp", "/var", "/var/lib", "/var/log", "/opt", "/srv"]}
    protected.add(Path.home().resolve())
    if resolved in protected or resolved.is_relative_to(repository):
        raise BackupError("backup directory must be outside the repository and not a filesystem root")
    forbidden = [Path(p) for p in ["/var/www", "/var/lib/docker", "/etc", "/usr", "/proc", "/sys", "/dev", "/run"]]
    forbidden.append(Path.home() / "doneit-secrets")
    if any(resolved.is_relative_to(p) for p in forbidden):
        raise BackupError("backup directory must be outside web roots and Docker storage")
    path.mkdir(mode=0o700, parents=True, exist_ok=True)
    if path.stat().st_uid != os.getuid():
        raise BackupError("backup directory must belong to the current user; do not run with sudo")
    path.chmod(0o700)
    return path


@contextmanager
def exclusive_lock(root):
    with os.fdopen(secure_open(root / ".backup.lock", os.O_CREAT | os.O_RDWR), "a+b") as lock:
        os.fchmod(lock.fileno(), 0o600)
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise BackupBusy("another backup is running") from None
        yield


def log(root, now, result, **fields):
    entry = json.dumps({"time": now.isoformat(), "result": result, **fields}, ensure_ascii=False)
    path = root / "backup.log"
    if path.is_symlink() or (root / "backup.log.1").is_symlink():
        raise BackupError("log files must not be symlinks")
    if path.exists() and path.stat().st_size >= LOG_LIMIT:
        path.chmod(0o600)
        path.replace(root / "backup.log.1")
    with os.fdopen(secure_open(path, os.O_CREAT | os.O_APPEND | os.O_WRONLY), "a", encoding="utf-8") as output:
        os.fchmod(output.fileno(), 0o600)
        output.write(entry + "\n")
    print(entry, flush=True)


def command(args, operation, **kwargs):
    try:
        result = subprocess.run(args, stderr=subprocess.DEVNULL, timeout=3600, check=False, **kwargs)
    except subprocess.TimeoutExpired:
        raise BackupError(f"{operation} timed out") from None
    except OSError:
        raise BackupError(f"{operation} could not start") from None
    if result.returncode:
        # Never copy database output or command arguments into logs.
        raise BackupError(f"{operation} failed (exit {result.returncode})")
    return result


def database_command(config, program):
    # Credentials remain inside the existing container; no host .env parsing or docker inspect.
    shell = """
        database=${1:-${POSTGRES_DB:-${POSTGRES_USER:-postgres}}}
        username=${2:-${POSTGRES_USER:-postgres}}
        export PGPASSWORD=${PGPASSWORD:-${POSTGRES_PASSWORD:-}}
        export PGHOST=/var/run/postgresql
    """ + program
    return ["docker", "exec", config.container, "sh", "-eu", "-c", shell,
            "doneit-backup", config.database or "", config.user or ""]


def database_size(config):
    args = database_command(config, """
        exec psql -X -q -A -t --no-password --username="$username" --dbname="$database" \
            -v ON_ERROR_STOP=1 -c 'SELECT pg_database_size(current_database())'
    """)
    value = command(args, "database size query", stdout=subprocess.PIPE).stdout.strip()
    if not value.isdigit() or int(value) <= 0:
        raise BackupError("database size query returned an invalid size")
    return int(value)


def checksum(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(MIB), b""):
            digest.update(block)
    return digest.hexdigest()


def sync_directory(root):
    fd = os.open(root, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def atomic_text(path, text):
    fd, temporary = tempfile.mkstemp(prefix=".doneit-", suffix=".partial", dir=path.parent)
    temporary = Path(temporary)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as output:
            os.fchmod(output.fileno(), 0o600)
            output.write(text)
            output.flush()
            os.fsync(output.fileno())
        temporary.replace(path)
        sync_directory(path.parent)
    finally:
        temporary.unlink(missing_ok=True)


def verified_archive(path):
    sidecar = path.with_suffix(".dump.sha256")
    if path.is_symlink() or sidecar.is_symlink() or not sidecar.is_file() or path.stat().st_size == 0:
        return False
    try:
        return sidecar.read_text(encoding="utf-8") == f"{checksum(path)}  {path.name}\n"
    except (OSError, UnicodeError):
        return False


def rotate(root, current, keep):
    # Invalid/unpaired files are never silently counted as successful or deleted.
    groups = {}
    for path in root.iterdir():
        match = ARCHIVE.fullmatch(path.name)
        if match and path.is_file() and verified_archive(path):
            groups.setdefault(match[1], []).append(path)
    current_day = ARCHIVE.fullmatch(current.name)[1]
    days = [current_day] + sorted((day for day in groups if day != current_day), reverse=True)[:keep - 1]
    retained = {current}
    for day in days[1:]:
        retained.add(max(groups[day], key=lambda path: (path.stat().st_mtime_ns, path.name)))
    removed = 0
    for paths in groups.values():
        for path in paths:
            if path not in retained:
                path.unlink()
                path.with_suffix(".dump.sha256").unlink()
                removed += 1
    sync_directory(root)
    return removed


def create_backup(config, root, now):
    reserve = config.min_free_mib * MIB
    required = database_size(config) * 2 + reserve
    if shutil.disk_usage(root).free < required:
        raise BackupError(f"insufficient free space; need at least {required} bytes including reserve")
    stamp = now.strftime("%Y-%m-%dT%H%M%S%z")
    zone = config.timezone.replace("/", "-")
    final = root / f"doneit_{stamp}_{zone}_{uuid.uuid4().hex}.dump"
    sidecar = final.with_suffix(".dump.sha256")
    fd, temporary = tempfile.mkstemp(prefix=".doneit-", suffix=".partial", dir=root)
    temporary = Path(temporary)
    published = False
    try:
        with os.fdopen(fd, "wb") as output:
            os.fchmod(output.fileno(), 0o600)
            args = database_command(config, 'exec pg_dump --format=custom --lock-wait-timeout=30s --no-password --username="$username" --dbname="$database"')
            command(args, "pg_dump", stdout=output)
            output.flush()
            os.fsync(output.fileno())
        if temporary.stat().st_size == 0:
            raise BackupError("pg_dump produced an empty file")
        if shutil.disk_usage(root).free < reserve:
            raise BackupError("free space fell below the reserve during pg_dump")
        with temporary.open("rb") as source:
            command(["docker", "exec", "-i", config.container, "pg_restore", "--list"],
                    "pg_restore list", stdin=source, stdout=subprocess.DEVNULL)
        digest = checksum(temporary)
        # The .dump is published last: every published archive already has its checksum.
        atomic_text(sidecar, f"{digest}  {final.name}\n")
        temporary.replace(final)
        published = True
        sync_directory(root)
        removed = rotate(root, final, config.keep_days)
        return final, removed
    finally:
        temporary.unlink(missing_ok=True)
        if not published:
            sidecar.unlink(missing_ok=True)


def run(config, now=None):
    now = now or datetime.now(ZoneInfo(config.timezone))
    # Run from cron every minute; only 03:00 Moscow performs any disk/DB work.
    if config.scheduled and (now.hour, now.minute) != (3, 0):
        return 0
    root = prepare_directory(config.backup_dir)
    try:
        with exclusive_lock(root):
            try:
                receipt = root / ".last-scheduled-success"
                if receipt.is_symlink():
                    raise BackupError("schedule receipt must not be a symlink")
                if config.scheduled and receipt.exists() and receipt.read_text().strip() == now.date().isoformat():
                    return 0
                archive, removed = create_backup(config, root, now)
                if config.scheduled:
                    atomic_text(receipt, now.date().isoformat() + "\n")
                log(root, now, "OK", bytes=archive.stat().st_size, path=str(archive), rotated=removed)
                return 0
            except (BackupError, OSError):
                raise
    except BackupBusy:
        print('{"result":"SKIP","reason":"another backup is running"}', flush=True)
        return 0 if config.scheduled else 75
    except BackupError as error:
        log(root, now, "ERROR", reason=str(error))
        return 1
    except OSError:
        log(root, now, "ERROR", reason="filesystem operation failed; inspect completed archive/checksum pairs")
        return 1


def positive(value):
    number = int(value)
    if number < 1:
        raise argparse.ArgumentTypeError("must be positive")
    return number


def arguments(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backup-dir", type=Path, default=Path.home() / "doneit-backups")
    parser.add_argument("--container", default="doneit-postgres")
    parser.add_argument("--database", help="default: POSTGRES_DB from the container")
    parser.add_argument("--user", help="default: POSTGRES_USER from the container")
    parser.add_argument("--timezone", default="Europe/Moscow")
    parser.add_argument("--keep-days", type=positive, default=14)
    parser.add_argument("--min-free-mib", type=positive, default=512)
    parser.add_argument("--scheduled", action="store_true")
    config = parser.parse_args(argv)
    if not re.fullmatch(r"[A-Za-z0-9_/-]+", config.timezone):
        parser.error("invalid timezone")
    return config


def interrupted(signum, frame):
    raise BackupError("backup interrupted")


if __name__ == "__main__":
    os.umask(0o077)
    signal.signal(signal.SIGTERM, interrupted)
    try:
        sys.exit(run(arguments()))
    except (BackupError, OSError, ValueError, ZoneInfoNotFoundError, KeyboardInterrupt):
        # Also covers failures before the private log can be opened. No exception/secret output.
        print('{"result":"ERROR","reason":"backup could not complete; check configuration and private log"}', file=sys.stderr)
        sys.exit(1)
