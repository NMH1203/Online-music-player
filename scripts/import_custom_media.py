"""Import custom MP3 tracks, album covers, artist images, and metadata into MinIO and PostgreSQL."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import io
import json
import math
import mimetypes
import os
from pathlib import Path
import re
import sys
import unicodedata
from uuid import NAMESPACE_URL, uuid5

import psycopg
from minio import Minio
from mutagen.id3 import ID3, ID3NoHeaderError
from mutagen.mp3 import MP3

ROOT = Path(__file__).resolve().parents[1]


def read_env(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    if path.exists():
        for raw in path.read_text(encoding="utf-8-sig").splitlines():
            line = raw.strip()
            if line and not line.startswith("#"):
                key, sep, value = line.partition("=")
                if sep:
                    result[key.strip()] = value.strip().strip("\"'")
    return result


def settings() -> dict[str, str]:
    return {**read_env(ROOT / ".env"), **read_env(ROOT / "storage" / ".env"), **os.environ}


def slugify(value: str) -> str:
    normalized = unicodedata.normalize("NFKD", value)
    ascii_text = normalized.encode("ascii", "ignore").decode("ascii").lower()
    slug = re.sub(r"[^a-z0-9]+", "-", ascii_text).strip("-")
    if not slug:
        slug = hashlib.sha1(value.encode("utf-8")).hexdigest()[:12]
    return slug[:180]


def sha256_file(path: Path) -> str:
    with path.open("rb") as f:
        return hashlib.file_digest(f, "sha256").hexdigest()


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def storage_client(env: dict[str, str]) -> Minio:
    from urllib.parse import urlsplit

    endpoint = urlsplit(env.get("MINIO_ENDPOINT", "http://127.0.0.1:9000"))
    return Minio(
        endpoint.netloc,
        access_key=env["MINIO_ROOT_USER"],
        secret_key=env["MINIO_ROOT_PASSWORD"],
        secure=endpoint.scheme == "https",
        region=env.get("MINIO_REGION", "us-east-1"),
    )


def database(env: dict[str, str]):
    return psycopg.connect(
        host=env.get("PGHOST", "127.0.0.1"),
        port=env.get("DB_PORT", "5432"),
        dbname=env.get("POSTGRES_DB", "music_player"),
        user=env.get("POSTGRES_USER", "music_app"),
        password=env.get("POSTGRES_PASSWORD", "music_local_dev_only"),
        connect_timeout=10,
    )


def upload_bytes(client: Minio, bucket: str, object_key: str, data: bytes, content_type: str, metadata: dict[str, str] | None = None) -> str:
    stream = io.BytesIO(data)
    res = client.put_object(
        bucket,
        object_key,
        stream,
        length=len(data),
        content_type=content_type,
        metadata=metadata or {},
    )
    return res.etag or "custom-etag"


def upload_image(client: Minio, bucket: str, prefix: str, image_path: Path) -> str:
    if not image_path.exists():
        raise FileNotFoundError(f"Image file not found: {image_path}")
    data = image_path.read_bytes()
    sha = sha256_bytes(data)[:16]
    ext = image_path.suffix.lower()
    if ext not in {".jpg", ".jpeg", ".png", ".webp"}:
        ext = ".jpg"
    content_type = "image/png" if ext == ".png" else "image/jpeg"
    object_key = f"demo/audio/{prefix}/{slugify(image_path.stem)}-{sha}{ext}"
    upload_bytes(client, bucket, object_key, data, content_type)
    return object_key


def parse_release_date(raw: str | None) -> str | None:
    if not raw:
        return None
    text = raw.strip()
    if re.match(r"^\d{4}-\d{2}-\d{2}$", text):
        return text
    if re.match(r"^\d{4}$", text):
        return f"{text}-01-01"
    m = re.match(r"^(\d{4}-\d{2}-\d{2})", text)
    if m:
        return m.group(1)
    return None


def extract_id3_defaults(mp3_path: Path) -> dict[str, object]:
    stem = mp3_path.stem
    fallback_artist = "Unknown Artist"
    fallback_title = stem
    if " - " in stem:
        parts = stem.split(" - ", 1)
        fallback_artist = re.split(r"[,/]", parts[0])[0].strip() or fallback_artist
        fallback_title = parts[1].strip() or fallback_title

    defaults: dict[str, object] = {
        "title": fallback_title,
        "artist": fallback_artist,
        "collaborators": [],
        "album": "Singles",
        "release_date": None,
        "genre": None,
        "embedded_cover": None,
        "embedded_cover_mime": None,
    }
    try:
        tags = ID3(mp3_path)
        if "TIT2" in tags and str(tags["TIT2"]).strip():
            defaults["title"] = str(tags["TIT2"]).strip()

        tpe2 = str(tags.get("TPE2", "")).strip()
        tpe1 = str(tags.get("TPE1", "")).strip()
        if tpe2:
            defaults["artist"] = re.split(r"[,/]", tpe2)[0].strip()
        elif tpe1:
            defaults["artist"] = re.split(r"[,/]", tpe1)[0].strip()

        if tpe1:
            collabs = [p.strip() for p in re.split(r"[,/]", tpe1) if p.strip()]
            defaults["collaborators"] = collabs

        if "TALB" in tags and str(tags["TALB"]).strip():
            defaults["album"] = str(tags["TALB"]).strip()
        if "TDRC" in tags and str(tags["TDRC"]).strip():
            defaults["release_date"] = parse_release_date(str(tags["TDRC"]).strip())
        if "TCON" in tags and str(tags["TCON"]).strip():
            defaults["genre"] = str(tags["TCON"]).strip()

        for key in tags.keys():
            if key.startswith("APIC"):
                apic = tags[key]
                if getattr(apic, "data", None):
                    defaults["embedded_cover"] = apic.data
                    defaults["embedded_cover_mime"] = getattr(apic, "mime", "image/jpeg")
                    break
    except ID3NoHeaderError:
        pass
    except Exception:
        pass
    return defaults


UPLOADED_COVER_CACHE: dict[str, str] = {}


def infer_genre_for_artist(artist_name: str, explicit_genre: str | None, id3_genre: str | None) -> tuple[str, str]:
    if explicit_genre and explicit_genre.lower() != "pop":
        return slugify(explicit_genre), explicit_genre.title()
    if id3_genre:
        return slugify(id3_genre), id3_genre
    lower_artist = artist_name.lower()
    if any(k in lower_artist for k in ("obito", "mck", "rpt", "shiki", "wxrdie", "tlinh")):
        return "hip-hop", "Hip-Hop / Rap"
    if any(k in lower_artist for k in ("cassette", "flob")):
        return "indie-rock", "Indie Rock"
    return slugify(explicit_genre or "pop"), (explicit_genre or "Pop").title()


def import_single_item(
    conn,
    client: Minio,
    bucket: str,
    audio_path: Path | None,
    title: str | None,
    artist_name: str | None,
    artist_bio: str | None,
    artist_image_path: Path | None,
    album_title: str | None,
    release_date: str | None,
    album_cover_path: Path | None,
    genre_code: str | None,
    genre_name: str | None,
    popularity: float,
    extract_dir: Path | None = None,
) -> dict[str, object]:
    id3_info = extract_id3_defaults(audio_path) if audio_path else {}

    final_title = str(title or id3_info.get("title") or "").strip()
    final_artist = str(artist_name or id3_info.get("artist") or "").strip()
    final_album = str(album_title or id3_info.get("album") or "").strip()
    final_release_date = parse_release_date(release_date or (str(id3_info.get("release_date")) if id3_info.get("release_date") else None))

    artist_image_key = (
        upload_image(client, bucket, "artists", artist_image_path)
        if artist_image_path
        else None
    )
    album_cover_key = (
        upload_image(client, bucket, "albums", album_cover_path)
        if album_cover_path
        else None
    )
    if not album_cover_key and id3_info.get("embedded_cover"):
        raw_cover = id3_info["embedded_cover"]
        mime = str(id3_info.get("embedded_cover_mime") or "image/jpeg")
        ext = ".png" if "png" in mime.lower() else ".jpg"
        sha = sha256_bytes(raw_cover)[:16]
        if extract_dir is not None and audio_path is not None:
            track_covers_dir = extract_dir / "track_covers"
            track_covers_dir.mkdir(parents=True, exist_ok=True)
            track_cover_file = track_covers_dir / f"{audio_path.stem}{ext}"
            if not track_cover_file.exists():
                track_cover_file.write_bytes(raw_cover)
        if sha in UPLOADED_COVER_CACHE:
            album_cover_key = UPLOADED_COVER_CACHE[sha]
        else:
            album_cover_key = f"demo/audio/albums/{slugify(final_album or final_title)}-{sha}{ext}"
            upload_bytes(
                client,
                bucket,
                album_cover_key,
                raw_cover,
                "image/png" if ext == ".png" else "image/jpeg",
            )
            UPLOADED_COVER_CACHE[sha] = album_cover_key
            if extract_dir is not None:
                covers_dir = extract_dir / "covers"
                covers_dir.mkdir(parents=True, exist_ok=True)
                cover_file = covers_dir / f"{slugify(final_artist)}-{slugify(final_album or final_title)}{ext}"
                if not cover_file.exists():
                    cover_file.write_bytes(raw_cover)

    fallback_artist_image_key = artist_image_key or album_cover_key

    with conn.cursor() as cur:
        artist_id = None
        if final_artist:
            artist_slug = slugify(final_artist)
            cur.execute("SELECT id FROM artists WHERE lower(name) = lower(%s) OR slug = %s LIMIT 1", (final_artist, artist_slug))
            row = cur.fetchone()
            if row:
                artist_id = row[0]
                if artist_bio:
                    cur.execute("UPDATE artists SET bio = %s WHERE id = %s", (artist_bio, artist_id))
                if artist_image_key:
                    cur.execute("UPDATE artists SET image_object_key = %s WHERE id = %s", (artist_image_key, artist_id))
                elif fallback_artist_image_key:
                    cur.execute(
                        "UPDATE artists SET image_object_key = COALESCE(image_object_key, %s) WHERE id = %s",
                        (fallback_artist_image_key, artist_id),
                    )
            else:
                cur.execute(
                    """
                    INSERT INTO artists(name, slug, bio, image_object_key)
                    VALUES (%s, %s, %s, %s)
                    RETURNING id
                    """,
                    (
                        final_artist,
                        artist_slug,
                        artist_bio or f"Nghệ sĩ {final_artist}",
                        fallback_artist_image_key,
                    ),
                )
                artist_id = cur.fetchone()[0]
            print(f"[Artist] {final_artist} (id={artist_id}, image={fallback_artist_image_key or 'unchanged'})")

        album_id = None
        if final_album:
            if not artist_id:
                cur.execute("SELECT id, primary_artist_id FROM albums WHERE lower(title) = lower(%s) LIMIT 1", (final_album,))
                row = cur.fetchone()
                if not row:
                    raise ValueError(f"Album '{final_album}' chưa tồn tại; hãy truyền thêm --artist để tạo mới.")
                album_id, artist_id = row[0], row[1]
            else:
                album_slug = slugify(final_album)
                cur.execute(
                    "SELECT id FROM albums WHERE primary_artist_id = %s AND (lower(title) = lower(%s) OR slug = %s) LIMIT 1",
                    (artist_id, final_album, album_slug),
                )
                row = cur.fetchone()
                if row:
                    album_id = row[0]
                else:
                    cur.execute(
                        """
                        INSERT INTO albums(primary_artist_id, title, slug, release_date, cover_object_key)
                        VALUES (%s, %s, %s, %s, %s)
                        RETURNING id
                        """,
                        (artist_id, final_album, album_slug, final_release_date, album_cover_key),
                    )
                    album_id = cur.fetchone()[0]

            if final_release_date:
                cur.execute("UPDATE albums SET release_date = %s WHERE id = %s", (final_release_date, album_id))
            if album_cover_key:
                cur.execute("UPDATE albums SET cover_object_key = %s WHERE id = %s", (album_cover_key, album_id))
            print(f"[Album]  {final_album} (id={album_id}, date={final_release_date}, cover={album_cover_key or 'unchanged'})")

        duration_seconds = 0
        bitrate_kbps = 0
        audio_object_key = None
        track_id = None
        g_code, g_name = infer_genre_for_artist(final_artist, genre_code, str(id3_info.get("genre") or ""))
        if genre_name:
            g_name = genre_name.strip()

        if audio_path:
            if not audio_path.exists():
                raise FileNotFoundError(f"Audio file not found: {audio_path}")
            if not artist_id:
                raise ValueError("Cần tên nghệ sĩ (--artist) khi thêm bài hát.")

            mp3_info = MP3(audio_path).info
            duration_seconds = max(1, math.ceil(mp3_info.length))
            bitrate_kbps = max(32, round(mp3_info.bitrate / 1000)) if getattr(mp3_info, "bitrate", 0) else 128
            file_size_bytes = audio_path.stat().st_size
            sha256 = sha256_file(audio_path)
            track_slug = slugify(final_title or audio_path.stem)
            audio_object_key = f"demo/audio/custom-{track_slug}/{sha256}.mp3"
            track_id = uuid5(NAMESPACE_URL, f"music-player-custom:{sha256}")

            etag = upload_bytes(
                client,
                bucket,
                audio_object_key,
                audio_path.read_bytes(),
                "audio/mpeg",
                metadata={"sha256": sha256, "license": "CC-BY-4.0"},
            )

            cur.execute(
                """
                INSERT INTO tracks(
                    id, primary_artist_id, album_id, title, duration_seconds,
                    audio_object_key, cover_object_key, file_size_bytes, mime_type,
                    bitrate_kbps, status, popularity_score, published_at
                )
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, 'audio/mpeg', %s, 'draft', %s, now())
                ON CONFLICT (id) DO UPDATE SET
                    primary_artist_id = excluded.primary_artist_id,
                    album_id = excluded.album_id,
                    title = excluded.title,
                    duration_seconds = excluded.duration_seconds,
                    audio_object_key = excluded.audio_object_key,
                    cover_object_key = COALESCE(excluded.cover_object_key, tracks.cover_object_key),
                    file_size_bytes = excluded.file_size_bytes,
                    bitrate_kbps = excluded.bitrate_kbps,
                    popularity_score = excluded.popularity_score
                """,
                (
                    track_id,
                    artist_id,
                    album_id,
                    final_title,
                    duration_seconds,
                    audio_object_key,
                    album_cover_key,
                    file_size_bytes,
                    bitrate_kbps,
                    popularity,
                ),
            )

            cur.execute(
                """
                INSERT INTO track_audio_metadata(
                    track_id, bucket_name, sha256, etag,
                    license_code, license_url, source_url, attribution_text, verified_at
                )
                VALUES (%s, %s, %s, %s, 'CC-BY-4.0',
                        'https://creativecommons.org/licenses/by/4.0/',
                        %s, %s, %s)
                ON CONFLICT (track_id) DO UPDATE SET
                    bucket_name = excluded.bucket_name,
                    sha256 = excluded.sha256,
                    etag = excluded.etag,
                    verified_at = excluded.verified_at
                """,
                (
                    track_id,
                    bucket,
                    sha256,
                    etag.strip('"') or "custom-etag",
                    f"https://local.musicplayer.demo/custom/{track_slug}",
                    f"{final_title} - {final_artist}",
                    datetime.now(timezone.utc),
                ),
            )

            cur.execute(
                "UPDATE tracks SET status = 'published', published_at = COALESCE(published_at, now()) WHERE id = %s",
                (track_id,),
            )

            cur.execute(
                """
                INSERT INTO genres(code, name)
                VALUES (%s, %s)
                ON CONFLICT (code) DO UPDATE SET name = genres.name
                RETURNING id
                """,
                (g_code, g_name),
            )
            genre_id = cur.fetchone()[0]
            cur.execute(
                """
                INSERT INTO track_genres(track_id, genre_id)
                VALUES (%s, %s)
                ON CONFLICT DO NOTHING
                """,
                (track_id, genre_id),
            )
            print(f"[Track]  {final_title} ({duration_seconds}s, id={track_id}, key={audio_object_key})")

    return {
        "file": audio_path.name if audio_path else None,
        "track_id": str(track_id) if track_id else None,
        "title": final_title,
        "artist": final_artist,
        "collaborators": id3_info.get("collaborators", []),
        "album": final_album,
        "release_date": final_release_date,
        "genre": g_name,
        "duration_seconds": duration_seconds,
        "bitrate_kbps": bitrate_kbps,
        "audio_object_key": audio_object_key,
        "cover_object_key": album_cover_key,
    }


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--audio", type=Path, help="Đường dẫn file nhạc .mp3")
    parser.add_argument("--directory", type=Path, help="Thư mục chứa nhiều file .mp3 để import hàng loạt")
    parser.add_argument("--manifest", type=Path, help="File JSON chứa danh sách các bài/album/artist cần import")
    parser.add_argument("--title", help="Tên bài hát (mặc định tự đọc từ ID3 tag hoặc tên file)")
    parser.add_argument("--artist", help="Tên nghệ sĩ")
    parser.add_argument("--artist-bio", help="Tiểu sử nghệ sĩ")
    parser.add_argument("--artist-image", type=Path, help="Đường dẫn file ảnh nghệ sĩ (.jpg/.png)")
    parser.add_argument("--album", help="Tên album")
    parser.add_argument("--release-date", help="Ngày phát hành album (YYYY-MM-DD)")
    parser.add_argument("--album-cover", type=Path, help="Đường dẫn file ảnh bìa album (.jpg/.png)")
    parser.add_argument("--genre", default="pop", help="Mã thể loại (pop, rock, jazz, classical, electronic...)")
    parser.add_argument("--genre-name", help="Tên hiển thị của thể loại")
    parser.add_argument("--popularity", type=float, default=50.0, help="Điểm phổ biến ban đầu (mặc định: 50)")
    args = parser.parse_args()

    if not any([args.audio, args.directory, args.manifest, args.artist, args.album]):
        parser.error("Hãy truyền ít nhất --audio, --directory, --manifest, --artist hoặc --album.")

    env = settings()
    bucket = env.get("MINIO_BUCKET", "music-demo")
    client = storage_client(env)
    if not client.bucket_exists(bucket):
        client.make_bucket(bucket)

    with database(env) as conn:
        if args.manifest:
            items = json.loads(args.manifest.read_text(encoding="utf-8"))
            if isinstance(items, dict):
                items = items.get("items", [])
            for item in items:
                import_single_item(
                    conn=conn,
                    client=client,
                    bucket=bucket,
                    audio_path=Path(item["audio"]) if item.get("audio") else None,
                    title=item.get("title"),
                    artist_name=item.get("artist"),
                    artist_bio=item.get("artist_bio"),
                    artist_image_path=Path(item["artist_image"]) if item.get("artist_image") else None,
                    album_title=item.get("album"),
                    release_date=item.get("release_date"),
                    album_cover_path=Path(item["album_cover"]) if item.get("album_cover") else None,
                    genre_code=item.get("genre", "pop"),
                    genre_name=item.get("genre_name"),
                    popularity=float(item.get("popularity", 50.0)),
                )
        elif args.directory:
            mp3_files = sorted(args.directory.glob("*.mp3"))
            if not mp3_files:
                raise ValueError(f"Không tìm thấy file .mp3 nào trong thư mục: {args.directory}")
            folder_cover = None
            for candidate in ("cover.jpg", "cover.png", "folder.jpg", "album.jpg"):
                if (args.directory / candidate).exists():
                    folder_cover = args.directory / candidate
                    break
            extract_dir = args.directory / "extracted"
            extract_dir.mkdir(parents=True, exist_ok=True)
            extracted_records = []
            for mp3_file in mp3_files:
                rec = import_single_item(
                    conn=conn,
                    client=client,
                    bucket=bucket,
                    audio_path=mp3_file,
                    title=None,
                    artist_name=args.artist,
                    artist_bio=args.artist_bio,
                    artist_image_path=args.artist_image,
                    album_title=args.album,
                    release_date=args.release_date,
                    album_cover_path=args.album_cover or folder_cover,
                    genre_code=args.genre,
                    genre_name=args.genre_name,
                    popularity=args.popularity,
                    extract_dir=extract_dir,
                )
                extracted_records.append(rec)
            summary_path = extract_dir / "metadata.json"
            summary_path.write_text(
                json.dumps(extracted_records, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )
            print(f"[Extract] Đã lưu ảnh bìa và thông tin trích xuất tại: {extract_dir}")
        else:
            import_single_item(
                conn=conn,
                client=client,
                bucket=bucket,
                audio_path=args.audio,
                title=args.title,
                artist_name=args.artist,
                artist_bio=args.artist_bio,
                artist_image_path=args.artist_image,
                album_title=args.album,
                release_date=args.release_date,
                album_cover_path=args.album_cover,
                genre_code=args.genre,
                genre_name=args.genre_name,
                popularity=args.popularity,
            )
        conn.commit()
    print("HOÀN TẤT: Đã đồng bộ dữ liệu lên MinIO và PostgreSQL.")


if __name__ == "__main__":
    main()
