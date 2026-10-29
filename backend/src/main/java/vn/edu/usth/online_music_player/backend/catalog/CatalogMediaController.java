package vn.edu.usth.online_music_player.backend.catalog;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.usth.online_music_player.backend.common.ApiException;
import vn.edu.usth.online_music_player.backend.playback.StorageProperties;

@RestController
public class CatalogMediaController {
    private final JdbcTemplate jdbcTemplate;
    private final MinioClient minioClient;
    private final StorageProperties storageProperties;

    public CatalogMediaController(
            JdbcTemplate jdbcTemplate,
            MinioClient minioClient,
            StorageProperties storageProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.minioClient = minioClient;
        this.storageProperties = storageProperties;
    }

    @GetMapping("/albums/{id}/cover")
    public ResponseEntity<byte[]> albumCover(@PathVariable("id") UUID albumId) {
        List<String> keys = jdbcTemplate.query(
                "SELECT cover_object_key FROM albums WHERE id = ?",
                (rs, rowNum) -> rs.getString("cover_object_key"),
                albumId);
        if (keys.isEmpty() || keys.get(0) == null || keys.get(0).isBlank()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND, "cover_not_found", "Không tìm thấy ảnh bìa album.");
        }
        return readImageObject(keys.get(0).trim());
    }

    @GetMapping("/artists/{id}/image")
    public ResponseEntity<byte[]> artistImage(@PathVariable("id") UUID artistId) {
        List<String> keys = jdbcTemplate.query(
                "SELECT image_object_key FROM artists WHERE id = ?",
                (rs, rowNum) -> rs.getString("image_object_key"),
                artistId);
        if (keys.isEmpty() || keys.get(0) == null || keys.get(0).isBlank()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND, "image_not_found", "Không tìm thấy ảnh nghệ sĩ.");
        }
        return readImageObject(keys.get(0).trim());
    }

    private ResponseEntity<byte[]> readImageObject(String objectKey) {
        String bucket = storageProperties.getBucket();
        if (bucket == null || bucket.isBlank()) {
            bucket = "music-demo";
        }
        GetObjectArgs args = GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build();
        try (InputStream input = minioClient.getObject(args)) {
            byte[] bytes = input.readNBytes(5 * 1024 * 1024 + 1);
            if (bytes.length > 5 * 1024 * 1024) throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE, "image_too_large", "Ảnh vượt quá giới hạn 5 MiB.");
            return ResponseEntity.ok()
                    .contentType(detectImageMediaType(objectKey))
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                    .body(bytes);
        } catch (ApiException exception) {
            throw exception;
        } catch (io.minio.errors.ErrorResponseException exception) {
            if (!"NoSuchKey".equals(exception.errorResponse().code())) throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "storage_unavailable", "Kho ảnh tạm thời không khả dụng.");
            throw new ApiException(HttpStatus.NOT_FOUND, "image_not_found", "Không tìm thấy tệp ảnh.");
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "storage_unavailable",
                    "Kho ảnh tạm thời không khả dụng.");
        }
    }

    private static MediaType detectImageMediaType(String objectKey) {
        String lower = objectKey.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
