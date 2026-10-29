package vn.edu.usth.online_music_player;

import android.app.Application;
import vn.edu.usth.online_music_player.data.session.SessionManager;

public class MusicApplication extends Application {
    @Override public void onCreate() { super.onCreate(); SessionManager.initialize(this); vn.edu.usth.online_music_player.data.playback.ListeningOutbox.initialize(this); }
}
