package vn.edu.usth.online_music_player.data.playback;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.lifecycle.Observer;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import java.util.concurrent.*;
import vn.edu.usth.online_music_player.R;
import vn.edu.usth.online_music_player.data.account.*;
import vn.edu.usth.online_music_player.data.account.AccountModels.Entitlement;
import vn.edu.usth.online_music_player.data.network.ApiClient;
import vn.edu.usth.online_music_player.data.session.AccessTokenStore;

/** Checks the server at a track boundary. Audio ads are ordinary local MediaItems in the same player,
 * so audio focus, background playback and the notification remain under MediaSession control. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class BoundaryAds {
    static final String AD_ID="music:demo-ad";
    private final Context context;
    private final ExoPlayer player;
    private final AccountApi api;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean pending, released;
    private long generation;
    private final Observer<Entitlement> entitlementObserver=this::onEntitlement;
    BoundaryAds(Context context,ExoPlayer player) {
        this(context, player, ApiClient.create(AccountApi.class));
    }
    BoundaryAds(Context context,ExoPlayer player,AccountApi api) {
        this.context=context; this.player=player; this.api=api;
        player.setPauseAtEndOfMediaItems(true);
        player.addListener(new Player.Listener() {
            @Override public void onPlayWhenReadyChanged(boolean ready,int reason) {
                if(!ready && reason==Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) next();
            }
            @Override public void onMediaItemTransition(MediaItem item,int reason) { cancelPending(); }
            @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
                if(reason==Player.DISCONTINUITY_REASON_SEEK && pending) cancelPending();
            }
        });
        EntitlementStore.observe().observeForever(entitlementObserver);
    }
    boolean isAd() { return player.getCurrentMediaItem()!=null && AD_ID.equals(player.getCurrentMediaItem().mediaId); }
    void next() {
        if(released || pending || !player.hasNextMediaItem()) return;
        if(isAd()) {
            int old=player.getCurrentMediaItemIndex();
            player.seekToNextMediaItem(); player.removeMediaItem(old); player.play(); return;
        }
        player.pause(); pending=true;
        long ticket=++generation;
        String token=AccessTokenStore.getAccessToken();
        long accountGeneration=vn.edu.usth.online_music_player.data.session.SessionManager.get().stamp().generation;
        worker.execute(() -> {
            Entitlement result=null;
            try {
                retrofit2.Response<Entitlement> response=api.entitlement().execute();
                if(response.isSuccessful()) result=response.body();
            } catch(Exception ignored) {}
            final Entitlement verified=result;
            main.post(() -> {
                if(released || ticket!=generation || !player.hasNextMediaItem()) return;
                pending=false;
                if(AccessTokenStore.getAccessToken()==null || token==null
                    || accountGeneration!=vn.edu.usth.online_music_player.data.session.SessionManager.get().stamp().generation) {
                    player.stop(); player.clearMediaItems(); return;
                }
                if(verified==null) {
                    Toast.makeText(context,"Không kiểm tra được gói. Kiểm tra mạng rồi nhấn Tiếp để thử lại.",Toast.LENGTH_LONG).show();
                    return;
                }
                EntitlementStore.update(verified);
                if(!verified.adFree) {
                    MediaItem ad=new MediaItem.Builder().setMediaId(AD_ID)
                        .setUri("android.resource://"+context.getPackageName()+"/"+R.raw.demo_audio_ad)
                        .setMediaMetadata(new MediaMetadata.Builder().setTitle("Quảng cáo mô phỏng")
                            .setArtist("Premium • Nghe nhạc không quảng cáo").build()).build();
                    player.addMediaItem(player.getCurrentMediaItemIndex()+1,ad);
                }
                player.seekToNextMediaItem(); player.prepare(); player.play();
            });
        });
    }
    private void onEntitlement(Entitlement entitlement) {
        if(released) return;
        if(entitlement==null && AccessTokenStore.getAccessToken()==null) {
            generation++; pending=false; player.stop(); player.clearMediaItems(); return;
        }
        if(entitlement!=null && entitlement.adFree && isAd()) next();
    }
    Player sessionPlayer() {
        return new ForwardingPlayer(player) {
            @Override public void seekToNextMediaItem() { next(); }
            @Override public void seekToNext() { next(); }
            @Override public void play() { if(!pending) super.play(); }
            @Override public void pause() { cancelPending(); super.pause(); }
            @Override public void setPlayWhenReady(boolean ready) {
                if(!ready) cancelPending();
                if(!pending) super.setPlayWhenReady(ready);
            }
        };
    }
    private void cancelPending() { generation++; pending=false; }
    void close() {
        released=true; generation++; worker.shutdownNow(); main.removeCallbacksAndMessages(null);
        EntitlementStore.observe().removeObserver(entitlementObserver);
    }
}
