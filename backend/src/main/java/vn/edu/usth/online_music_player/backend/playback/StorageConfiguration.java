package vn.edu.usth.online_music_player.backend.playback;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfiguration {
    @Bean
    public MinioClient minioClient(StorageProperties properties) {
        String accessKey = properties.getAccessKey();
        String secretKey = properties.getSecretKey();
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "MINIO_ACCESS_KEY and MINIO_SECRET_KEY must be configured for audio streaming.");
        }

        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(accessKey, secretKey)
                .region(properties.getRegion())
                .build();
    }
}
