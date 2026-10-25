package vn.edu.usth.online_music_player.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import vn.edu.usth.online_music_player.backend.playback.StorageProperties;

@SpringBootApplication
@EnableConfigurationProperties(StorageProperties.class)
public class OnlineMusicBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(OnlineMusicBackendApplication.class, args);
    }
}
