"""Download licensed demo MP3s, verify S3 bytes, then publish atomically in Postgres."""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import re
import sys
import time
from urllib.error import HTTPError
from urllib.parse import urlsplit, quote
from urllib.request import Request, urlopen
from uuid import NAMESPACE_URL, uuid5

import psycopg
from minio import Minio
from minio.error import S3Error
from mutagen.mp3 import MP3

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / "runtime" / "demo-music"
LICENSES = {"CC-BY-4.0": "https://creativecommons.org/licenses/by/4.0/",
            "CC0-1.0": "https://creativecommons.org/publicdomain/zero/1.0/"}
GENRES = {"pop": "Pop", "rock": "Rock", "jazz": "Jazz",
          "classical": "Classical", "electronic": "Electronic"}


def read_env(path: Path) -> dict[str, str]:
    result = {}
    if path.exists():
        for raw in path.read_text(encoding="utf-8-sig").splitlines():
            line = raw.strip()
            if line and not line.startswith("#"):
                key, sep, value = line.partition("=")
                if not sep:
                    raise ValueError(f"Invalid env entry in {path.name}")
                result[key.strip()] = value.strip().strip("\"'")
    return result


def settings() -> dict[str, str]:
    return {**read_env(ROOT / ".env"), **read_env(ROOT / "storage" / ".env"), **os.environ}


def fetch(url: str, destination: Path, max_bytes: int = 40_000_000) -> None:
    if urlsplit(url).scheme != "https":
        raise ValueError("Source downloads must use HTTPS")
    destination.parent.mkdir(parents=True, exist_ok=True)
    partial = destination.with_suffix(destination.suffix + ".part")
    for attempt in range(3):
        try:
            request = Request(url, headers={"User-Agent": "MusicPlayerEducationalDemo/1.0"})
            with urlopen(request, timeout=45) as response, partial.open("wb") as output:
                if urlsplit(response.url).scheme != "https":
                    raise ValueError("Download redirected away from HTTPS")
                size = 0
                while block := response.read(128 * 1024):
                    size += len(block)
                    if size > max_bytes:
                        raise ValueError(f"Download exceeds {max_bytes} bytes")
                    output.write(block)
                if not size:
                    raise ValueError("Empty download")
            partial.replace(destination)
            return
        except Exception:
            partial.unlink(missing_ok=True)
            if attempt == 2:
                raise
            time.sleep(attempt + 1)


def digest(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def load_sources(limit: int | None) -> list[dict]:
    document = json.loads((ROOT / "database/demo/sources.json").read_text(encoding="utf-8"))
    if document["schema_version"] != 1:
        raise ValueError("Unsupported source manifest version")
    tracks = document["tracks"]
    ids = set()
    for item in tracks:
        if not re.fullmatch(r"[a-z0-9-]+", item["id"]) or item["id"] in ids:
            raise ValueError("Unsafe or duplicate track ID")
        ids.add(item["id"])
        if item["license_code"] not in LICENSES or item["license_url"] != LICENSES[item["license_code"]]:
            raise ValueError(f"Unapproved demo license: {item['id']}")
        if not item["attribution_text"].strip() or item["genre_code"] not in GENRES:
            raise ValueError(f"Missing credit/genre: {item['id']}")
        for field in ("source_url", "download_url"):
            url = urlsplit(item[field])
            if url.scheme != "https" or url.hostname not in {"incompetech.com", "www.incompetech.com"}:
                raise ValueError(f"Unapproved source: {item['id']} {field}")
    return tracks[:limit] if limit else tracks


def prepare(item: dict, locked: dict) -> dict:
    path = CACHE / "audio" / (item["id"] + ".mp3")
    if not path.exists():
        fetch(item["download_url"], path)
    sha = digest(path)
    if item["id"] in locked and sha != locked[item["id"]]["sha256"]:
        raise ValueError(f"Audio checksum changed: {item['id']}; review source before updating lock")
    info = MP3(path).info  # Rejects HTML/error pages and invalid MP3s.
    if info.length <= 0 or info.bitrate <= 0:
        raise ValueError(f"Invalid MP3 metadata: {item['id']}")
    return {**item, "path": str(path), "sha256": sha,
            "file_size_bytes": path.stat().st_size, "duration_seconds": math.ceil(info.length),
            "bitrate_kbps": round(info.bitrate / 1000), "mime_type": "audio/mpeg",
            "audio_object_key": f"demo/audio/{item['id']}/{sha}.mp3"}


def storage_client(env: dict) -> Minio:
    endpoint = urlsplit(env.get("MINIO_ENDPOINT", "http://127.0.0.1:9000"))
    if endpoint.scheme not in {"http", "https"} or not endpoint.netloc or endpoint.path not in {"", "/"}:
        raise ValueError("MINIO_ENDPOINT must be an S3 endpoint, without bucket/path")
    return Minio(endpoint.netloc, access_key=env["MINIO_ROOT_USER"],
                 secret_key=env["MINIO_ROOT_PASSWORD"], secure=endpoint.scheme == "https",
                 region=env.get("MINIO_REGION", "us-east-1"))


def database(env: dict):
    return psycopg.connect(host=env.get("PGHOST", "127.0.0.1"),
                          port=env.get("DB_PORT", "5432"),
                          dbname=env.get("POSTGRES_DB", "music_player"),
                          user=env.get("POSTGRES_USER", "music_app"),
                          password=env.get("POSTGRES_PASSWORD", "music_local_dev_only"),
                          connect_timeout=10)


def verify_object(client: Minio, bucket: str, item: dict) -> dict:
    key = item["audio_object_key"]
    stat = client.stat_object(bucket, key)
    if stat.size != item["file_size_bytes"] or stat.content_type != "audio/mpeg":
        raise ValueError(f"Size/MIME mismatch: {key}")
    if stat.metadata.get("x-amz-meta-sha256") != item["sha256"]:
        raise ValueError(f"Object SHA metadata mismatch: {key}")
    for field, value in {"license": item["license_code"], "source-url": item["source_url"]}.items():
        if stat.metadata.get("x-amz-meta-" + field) != value:
            raise ValueError(f"Object {field} metadata mismatch: {key}; review before replacing the object")
    response = client.get_object(bucket, key)
    try:
        sha = hashlib.sha256()
        for block in response.stream(128 * 1024):
            sha.update(block)
        if sha.hexdigest() != item["sha256"]:
            raise ValueError(f"Downloaded S3 bytes mismatch: {key}")
    finally:
        response.close()
        response.release_conn()
    response = client.get_object(bucket, key, offset=0, length=1024)
    try:
        if response.status != 206 or response.read() != Path(item["path"]).read_bytes()[:1024]:
            raise ValueError(f"HTTP Range failed: {key}")
    finally:
        response.close()
        response.release_conn()
    return {**item, "etag": stat.etag, "verified_at": datetime.now(timezone.utc).isoformat()}


def ensure_private(client: Minio, bucket: str, env: dict, key: str | None = None) -> None:
    try:
        policy = json.loads(client.get_bucket_policy(bucket))
        if policy.get("Statement"):
            raise ValueError("Demo bucket already has a policy; use a separate private bucket")
    except S3Error as error:
        if error.code != "NoSuchBucketPolicy":
            raise
    if key:
        url = env.get("MINIO_ENDPOINT", "http://127.0.0.1:9000").rstrip("/") + "/" + bucket + "/" + quote(key)
        try:
            with urlopen(Request(url, method="HEAD"), timeout=10):
                raise ValueError("Anonymous object access unexpectedly succeeded")
        except HTTPError as error:
            if error.code != 403:
                raise


def publish(connection, items: list[dict], bucket: str) -> None:
    # One transaction, after ALL requested objects have passed full readback and Range.
    with connection.cursor() as cur:
        cur.execute("SELECT pg_advisory_xact_lock(7140616)")
        for index, item in enumerate(items):
            cur.execute("""INSERT INTO artists(name,slug,bio) VALUES(%s,%s,%s)
                ON CONFLICT(slug) DO UPDATE SET name=excluded.name RETURNING id""",
                (item["artist"], item["artist_slug"], "Music credited to the original creator; source and license on each track."))
            artist = cur.fetchone()[0]
            cur.execute("""INSERT INTO albums(primary_artist_id,title,slug) VALUES(%s,%s,%s)
                ON CONFLICT(primary_artist_id,slug) DO UPDATE SET title=excluded.title RETURNING id""",
                (artist, item["album"], item["album_slug"]))
            album = cur.fetchone()[0]
            track_id = uuid5(NAMESPACE_URL, "music-player-demo:" + item["source_url"])
            cur.execute("""INSERT INTO tracks(id,primary_artist_id,album_id,title,duration_seconds,
                audio_object_key,file_size_bytes,mime_type,bitrate_kbps,status,popularity_score,published_at)
                VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,'published',%s,now())
                ON CONFLICT(id) DO UPDATE SET primary_artist_id=excluded.primary_artist_id,
                album_id=excluded.album_id,title=excluded.title,duration_seconds=excluded.duration_seconds,
                audio_object_key=excluded.audio_object_key,file_size_bytes=excluded.file_size_bytes,
                mime_type=excluded.mime_type,bitrate_kbps=excluded.bitrate_kbps,status='published',
                published_at=COALESCE(tracks.published_at,excluded.published_at)""",
                (track_id, artist, album, item["title"], item["duration_seconds"], item["audio_object_key"],
                 item["file_size_bytes"], item["mime_type"], item["bitrate_kbps"], (index * 17) % 100))
            cur.execute("""INSERT INTO track_audio_metadata(track_id,bucket_name,sha256,etag,
                license_code,license_url,source_url,attribution_text,verified_at)
                VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s) ON CONFLICT(track_id) DO UPDATE SET
                bucket_name=excluded.bucket_name,sha256=excluded.sha256,etag=excluded.etag,
                license_code=excluded.license_code,license_url=excluded.license_url,
                source_url=excluded.source_url,attribution_text=excluded.attribution_text,
                verified_at=excluded.verified_at""",
                (track_id, bucket, item["sha256"], item["etag"], item["license_code"], item["license_url"],
                 item["source_url"], item["attribution_text"], item["verified_at"]))
            cur.execute("""INSERT INTO genres(code,name) VALUES(%s,%s)
                ON CONFLICT(code) DO UPDATE SET name=excluded.name RETURNING id""",
                (item["genre_code"], GENRES[item["genre_code"]]))
            genre_id = cur.fetchone()[0]
            cur.execute("DELETE FROM track_genres WHERE track_id=%s", (track_id,))
            cur.execute("INSERT INTO track_genres(track_id,genre_id) VALUES(%s,%s)", (track_id, genre_id))


def verify_database(connection, items: list[dict], bucket: str) -> None:
    with connection.cursor() as cur:
        for item in items:
            cur.execute("""SELECT t.audio_object_key,t.file_size_bytes,t.duration_seconds,t.mime_type,
                t.bitrate_kbps,t.status,m.bucket_name,m.sha256,m.etag,m.license_code,
                m.license_url,m.source_url,m.attribution_text
                FROM tracks t JOIN track_audio_metadata m ON m.track_id=t.id WHERE t.id=%s""",
                (uuid5(NAMESPACE_URL, "music-player-demo:" + item["source_url"]),))
            expected = (item["audio_object_key"],item["file_size_bytes"],item["duration_seconds"],
                        item["mime_type"],item["bitrate_kbps"],"published",bucket,item["sha256"],
                        item["etag"],item["license_code"],item["license_url"],item["source_url"],
                        item["attribution_text"])
            if cur.fetchone() != expected:
                raise ValueError(f"Database mapping mismatch: {item['id']}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--download-only", action="store_true")
    modes.add_argument("--verify-only", action="store_true")
    parser.add_argument("--limit", type=int)
    args = parser.parse_args()
    if args.limit is not None and args.limit < 1:
        parser.error("--limit must be positive")
    items = load_sources(args.limit)
    lock_path = ROOT / "database/demo/audio-lock.json"
    locked = json.loads(lock_path.read_text(encoding="utf-8")) if lock_path.exists() else {}
    if not args.download_only and any(i["id"] not in locked for i in items):
        raise ValueError("Missing reviewed audio-lock.json; download and review audio first")
    with ThreadPoolExecutor(max_workers=3) as pool:
        prepared = list(pool.map(lambda item: prepare(item, locked), items))
    print(f"MP3 checked: {len(prepared)} tracks, {sum(i['file_size_bytes'] for i in prepared):,} bytes", flush=True)
    CACHE.mkdir(parents=True, exist_ok=True)
    (CACHE / "download-manifest.json").write_text(json.dumps({"tracks": prepared},
                                                            ensure_ascii=False, indent=2), encoding="utf-8")
    if args.download_only:
        print("Download complete; no storage or database changes.")
        return
    env = settings()
    bucket = env.get("MINIO_BUCKET", "music-demo")
    client = storage_client(env)
    # Fail before upload if the database/migration isn't ready.
    with database(env) as connection:
        with connection.cursor() as cur:
            cur.execute("SELECT 1 FROM track_audio_metadata LIMIT 0")
        if not client.bucket_exists(bucket):
            if args.verify_only:
                raise ValueError("Demo bucket does not exist")
            client.make_bucket(bucket)
        ensure_private(client, bucket, env)
        verified = []
        for index, item in enumerate(prepared, 1):
            if not args.verify_only:
                try:
                    stat = client.stat_object(bucket, item["audio_object_key"])
                except S3Error as error:
                    if error.code != "NoSuchKey":
                        raise
                    stat = None
                if stat is None:
                    client.fput_object(bucket, item["audio_object_key"], item["path"], content_type="audio/mpeg",
                        metadata={"sha256": item["sha256"], "license": item["license_code"],
                                  "source-url": item["source_url"]})
            verified.append(verify_object(client, bucket, item))
            print(f"[{index}/{len(prepared)}] S3 bytes + Range OK: {item['title']}", flush=True)
        ensure_private(client, bucket, env, prepared[0]["audio_object_key"])
        if not args.verify_only:
            publish(connection, verified, bucket)
        verify_database(connection, verified, bucket)
    report = {"verified_at": datetime.now(timezone.utc).isoformat(), "bucket": bucket,
              "tracks": [{k: v for k, v in i.items() if k != "path"} for i in verified],
              "anonymous_access": "denied", "range": "206 verified on every track"}
    (CACHE / "verification.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"PASS: {len(verified)} published tracks; private bucket, SHA-256, MIME, Range and DB mapping verified.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # Do not dump connection strings, headers or credentials on failure.
        print(f"ERROR ({type(error).__name__}): {error}", file=sys.stderr)
        sys.exit(1)
