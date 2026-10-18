"""Provision and verify a backend account that can only read demo audio."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import io
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
from urllib.parse import urlsplit
from uuid import uuid4

from minio import Minio
from minio.credentials import StaticProvider
from minio.error import MinioAdminException, S3Error
from minio.minioadmin import MinioAdmin
from urllib3 import PoolManager, Retry, Timeout

from seed_music import ROOT, read_env, settings, storage_client

BACKEND_ENV = ROOT / "storage" / "backend.env"
REPORT = ROOT / "runtime" / "storage-reader-check.json"


def run_windows_acl_helper(option: str, path: Path | None = None) -> None:
    shell = shutil.which("pwsh") or shutil.which("powershell.exe")
    if shell is None:
        legacy_shell = Path(os.environ.get("SystemRoot", r"C:\Windows")) / "System32/WindowsPowerShell/v1.0/powershell.exe"
        if legacy_shell.is_file():
            shell = str(legacy_shell)
    if shell is None:
        raise RuntimeError("PowerShell is required to restrict Windows storage permissions")
    arguments = [option]
    if path is not None:
        arguments.append(str(path))
    result = subprocess.run(
        [shell, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
         "-File", str(ROOT / "scripts" / "Setup-Minio.ps1"), *arguments],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False, timeout=30,
    )
    if result.returncode != 0:
        raise RuntimeError("Could not restrict Windows storage permissions")


def secure_storage_directory() -> None:
    directory = BACKEND_ENV.parent
    directory.mkdir(parents=True, exist_ok=True)
    if os.name == "nt":
        run_windows_acl_helper("-SecureStorageDirectory")
    else:
        directory.chmod(0o700)


def secure_credentials(path: Path) -> None:
    if os.name == "nt":
        run_windows_acl_helper("-SecureCredentialFile", path)
    else:
        path.chmod(0o600)


def reader_policy(bucket: str) -> dict:
    return {
        "Version": "2012-10-17",
        "Statement": [
            {"Effect": "Allow", "Action": ["s3:GetBucketLocation"],
             "Resource": [f"arn:aws:s3:::{bucket}"]},
            {"Effect": "Allow", "Action": ["s3:GetObject"],
             "Resource": [f"arn:aws:s3:::{bucket}/demo/audio/*"]},
        ],
    }


def policy_signature(document: dict) -> tuple:
    """Ignore JSON array ordering and optional statement labels only."""
    if set(document) - {"Version", "Statement", "Id"}:
        raise ValueError("Reader policy contains unexpected top-level fields")
    statements = []
    for statement in document.get("Statement", []):
        if set(statement) - {"Effect", "Action", "Resource", "Sid"}:
            raise ValueError("Reader policy contains unexpected permissions or conditions")
        actions = statement.get("Action", [])
        resources = statement.get("Resource", [])
        actions = [actions] if isinstance(actions, str) else actions
        resources = [resources] if isinstance(resources, str) else resources
        statements.append((statement.get("Effect"), tuple(sorted(actions)), tuple(sorted(resources))))
    return document.get("Version"), tuple(sorted(statements))


def load_backend(root_settings: dict, verify_only: bool) -> dict:
    if not BACKEND_ENV.exists():
        if verify_only:
            raise ValueError("storage/backend.env is missing; run setup_storage_reader.py first")
        values = {
            "MINIO_ENDPOINT": root_settings.get("MINIO_ENDPOINT", "http://127.0.0.1:9000"),
            "MINIO_REGION": root_settings.get("MINIO_REGION", "us-east-1"),
            "MINIO_BUCKET": root_settings.get("MINIO_BUCKET", "music-demo"),
            "MINIO_ACCESS_KEY": "demo" + secrets.token_hex(8),
            "MINIO_SECRET_KEY": secrets.token_hex(32),
        }
        if any("\n" in value or "\r" in value for value in values.values()):
            raise ValueError("Settings must not contain line breaks")
        secure_storage_directory()
        # Exclusive creation preserves credentials if two setup commands overlap.
        with BACKEND_ENV.open("x", encoding="utf-8", newline="\n"):
            pass
        try:
            secure_credentials(BACKEND_ENV)
            with BACKEND_ENV.open("w", encoding="utf-8", newline="\n") as stream:
                stream.write("# Backend read credentials. Never send these to the mobile client or commit this file.\n")
                stream.writelines(f"{key}={value}\n" for key, value in values.items())
        except Exception:
            BACKEND_ENV.unlink(missing_ok=True)
            raise
    secure_credentials(BACKEND_ENV)
    values = read_env(BACKEND_ENV)
    for key in ("MINIO_ENDPOINT", "MINIO_REGION", "MINIO_BUCKET", "MINIO_ACCESS_KEY", "MINIO_SECRET_KEY"):
        if not values.get(key):
            raise ValueError(f"Missing {key} in storage/backend.env")
    for key, fallback in (("MINIO_ENDPOINT", "http://127.0.0.1:9000"),
                          ("MINIO_REGION", "us-east-1"), ("MINIO_BUCKET", "music-demo")):
        if values[key] != root_settings.get(key, fallback):
            raise ValueError(f"Backend and root {key} differ; review storage/backend.env")
    if values["MINIO_ACCESS_KEY"] == root_settings["MINIO_ROOT_USER"]:
        raise ValueError("The backend account must be separate from the root account")
    if not re.fullmatch(r"demo[a-f0-9]{16}", values["MINIO_ACCESS_KEY"]):
        raise ValueError("Backend access key is not an account generated by this setup script")
    if len(values["MINIO_SECRET_KEY"]) < 8:
        raise ValueError("Backend secret requires at least 8 characters")
    return values


def attached_policies(user: dict) -> set[str]:
    if user.get("memberOf"):
        raise ValueError("The dedicated reader account must not inherit group permissions")
    return {name.strip() for name in user.get("policyName", "").split(",") if name.strip()}


def read_prefix(client: Minio, bucket: str, key: str) -> bytes:
    response = client.get_object(bucket, key, offset=0, length=1024)
    try:
        return response.read()
    finally:
        response.close()
        response.release_conn()


def verify_access(root_client: Minio, reader: Minio, bucket: str) -> dict:
    sample = next((item for item in root_client.list_objects(bucket, prefix="demo/audio/", recursive=True)
                   if item.object_name.endswith(".mp3") and item.size > 0), None)
    if sample is None:
        raise ValueError("No demo audio exists yet; run seed_music.py before verifying backend access")
    expected = read_prefix(root_client, bucket, sample.object_name)
    if not expected or read_prefix(reader, bucket, sample.object_name) != expected:
        raise ValueError("Backend read verification failed")

    probe_key = "demo/audio/_permission-check-" + uuid4().hex
    try:
        reader.put_object(bucket, probe_key, io.BytesIO(b"permission-check"), length=16)
    except S3Error as error:
        if error.code != "AccessDenied":
            raise
    else:
        root_client.remove_object(bucket, probe_key)
        raise ValueError("Backend write unexpectedly succeeded; probe removed using root credentials")

    try:
        next(iter(reader.list_objects(bucket, prefix="demo/audio/")), None)
    except S3Error as error:
        if error.code != "AccessDenied":
            raise
    else:
        raise ValueError("Backend bucket listing unexpectedly succeeded")

    return {"sample_object_key": sample.object_name, "read_allowed": True,
            "write_denied": True, "list_denied": True}


def run(verify_only: bool) -> None:
    secure_storage_directory()
    root_env = ROOT / "storage" / ".env"
    if root_env.is_file():
        secure_credentials(root_env)
    root_settings = settings()
    root_client = storage_client(root_settings)
    backend = load_backend(root_settings, verify_only)
    bucket = backend["MINIO_BUCKET"]
    endpoint = urlsplit(backend["MINIO_ENDPOINT"])
    policy_name = "music-demo-reader-" + hashlib.sha256(bucket.encode()).hexdigest()[:12]
    expected_policy = reader_policy(bucket)
    admin = MinioAdmin(
        endpoint=endpoint.netloc, secure=endpoint.scheme == "https", region=backend["MINIO_REGION"],
        credentials=StaticProvider(root_settings["MINIO_ROOT_USER"], root_settings["MINIO_ROOT_PASSWORD"]),
        http_client=PoolManager(timeout=Timeout(connect=10, read=30),
                                retries=Retry(total=2, backoff_factor=0.2)),
    )
    users = json.loads(admin.user_list())
    account_key = backend["MINIO_ACCESS_KEY"]
    user = users.get(account_key)
    if user is not None and attached_policies(user) - {policy_name}:
        raise ValueError("Backend account has unexpected policies; inspect its permissions before continuing")
    if not verify_only:
        admin.policy_add(policy_name, policy=expected_policy)
        if user is None:
            admin.user_add(account_key, backend["MINIO_SECRET_KEY"])
            user = {}
        if policy_name not in attached_policies(user):
            admin.attach_policy([policy_name], user=account_key)

    actual_user = json.loads(admin.user_info(account_key))
    if actual_user.get("status") != "enabled" or attached_policies(actual_user) != {policy_name}:
        raise ValueError("Backend account is disabled or lacks its exclusive read policy")
    actual_policy = json.loads(admin.policy_info(policy_name))
    actual_policy = actual_policy.get("policy", actual_policy)
    if isinstance(actual_policy, str):
        actual_policy = json.loads(actual_policy)
    if policy_signature(actual_policy) != policy_signature(expected_policy):
        raise ValueError("Backend policy does not match the required audio-only read permissions")

    reader = Minio(endpoint.netloc, access_key=account_key, secret_key=backend["MINIO_SECRET_KEY"],
                   secure=endpoint.scheme == "https", region=backend["MINIO_REGION"])
    report = {"verified_at": datetime.now(timezone.utc).isoformat(), "bucket": bucket,
              "policy_name": policy_name, **verify_access(root_client, reader, bucket)}
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("Backend account verified: demo audio read allowed; writes and bucket listing denied.")
    print(f"Credentials (kept private): {BACKEND_ENV}")
    print(f"Verification report: {REPORT}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify-only", action="store_true", help="Check the existing account without changing IAM")
    args = parser.parse_args()
    try:
        run(args.verify_only)
    except (MinioAdminException, S3Error) as error:
        # Do not print admin response bodies or resolved credentials.
        code = error.code if isinstance(error, S3Error) else getattr(error, "_code", "unknown")
        print(f"MinIO reader setup failed ({type(error).__name__}, code {code}).", file=sys.stderr)
        raise SystemExit(1) from None
    except Exception as error:
        print(f"Reader setup failed: {error}", file=sys.stderr)
        raise SystemExit(1) from None


if __name__ == "__main__":
    main()
