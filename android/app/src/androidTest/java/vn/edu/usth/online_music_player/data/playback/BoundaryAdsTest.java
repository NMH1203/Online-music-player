package vn.edu.usth.online_music_player.data.playback;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.concurrent.*;
import okhttp3.mockwebserver.*;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import vn.edu.usth.online_music_player.R;
import vn.edu.usth.online_music_player.data.account.*;
import vn.edu.usth.online_music_player.data.session.AccessTokenStore;

@RunWith(AndroidJUnit4.class)
public class BoundaryAdsTest {
    private MockWebServer server;
    private ExoPlayer player;
    private BoundaryAds ads;
    private Context context;
    private final BlockingQueue<String> transitions=new LinkedBlockingQueue<>();
    @Before public void setup() throws Exception {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        server=new MockWebServer(); server.start();
        AccountApi api=new Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(AccountApi.class);
        AccessTokenStore.replaceAccessToken("test-account-token");
        onMain(()->{
            player=new ExoPlayer.Builder(context).build();
            ads=new BoundaryAds(context,player,api);
            player.addListener(new Player.Listener() {
                @Override public void onMediaItemTransition(MediaItem item,int reason) {
                    if(item!=null) transitions.offer(item.mediaId);
                }
            });
            player.setMediaItems(java.util.List.of(song("first"),song("second")));
            player.prepare();
        });
        assertEquals("first",transitions.poll(10,TimeUnit.SECONDS));
    }
    private MediaItem song(String id) {
        return new MediaItem.Builder().setMediaId(id)
            .setUri("android.resource://"+context.getPackageName()+"/"+R.raw.demo_audio_ad)
            .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder().setEndPositionMs(500).build()).build();
    }
    private void onMain(Runnable runnable) { InstrumentationRegistry.getInstrumentation().runOnMainSync(runnable); }
    @After public void teardown() throws Exception {
        onMain(()-> { ads.close(); player.release(); });
        AccessTokenStore.clear(); EntitlementStore.clear(); server.shutdown();
    }
    @Test public void freeAutomaticallyPlaysAdOnlyAfterSongEnds() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"adFree\":false,\"planCode\":\"FREE\"}").addHeader("Content-Type","application/json"));
        onMain(()->player.play());
        assertEquals(BoundaryAds.AD_ID,transitions.poll(15,TimeUnit.SECONDS));
        assertNotNull(server.takeRequest(3,TimeUnit.SECONDS));
        onMain(()->assertTrue(ads.isAd()));
        onMain(()->ads.next());
        assertEquals("second",transitions.poll(5,TimeUnit.SECONDS));
    }
    @Test public void premiumGoesDirectlyToNextSongWithoutAd() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"adFree\":true,\"planCode\":\"PREMIUM\"}").addHeader("Content-Type","application/json"));
        onMain(()->player.play());
        assertEquals("second",transitions.poll(15,TimeUnit.SECONDS));
        onMain(()->assertFalse(ads.isAd()));
    }
    @Test public void changingToFreeDoesNotInterruptCurrentSong() {
        onMain(()-> {
            AccountModels.Entitlement free=new AccountModels.Entitlement(); free.adFree=false; free.planCode="FREE";
            EntitlementStore.update(free);
            assertEquals("first",player.getCurrentMediaItem().mediaId);
            assertFalse(ads.isAd());
        });
    }
    @Test public void failedPolicyRequestUsesFreeFallbackAndContinues() throws Exception {
        EntitlementStore.clear();
        server.enqueue(new MockResponse().setResponseCode(503));
        onMain(()->ads.next());
        assertEquals(BoundaryAds.AD_ID,transitions.poll(10,TimeUnit.SECONDS));
        onMain(()->ads.next());
        assertEquals("second",transitions.poll(5,TimeUnit.SECONDS));
    }
    @Test public void offlineWithRecentPremiumSkipsAd() throws Exception {
        AccountModels.Entitlement premium=new AccountModels.Entitlement();
        premium.adFree=true; premium.expiresAt=java.time.Instant.now().plusSeconds(3600).toString();
        EntitlementStore.update(premium);
        server.enqueue(new MockResponse().setResponseCode(503));
        onMain(()->ads.next());
        assertEquals("second",transitions.poll(10,TimeUnit.SECONDS));
    }
    @Test public void premiumUpgradeDuringAdSkipsAdImmediately() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"adFree\":false}").addHeader("Content-Type","application/json"));
        onMain(()->ads.next());
        assertEquals(BoundaryAds.AD_ID,transitions.poll(10,TimeUnit.SECONDS));
        AccountModels.Entitlement premium=new AccountModels.Entitlement(); premium.adFree=true;
        EntitlementStore.update(premium);
        assertEquals("second",transitions.poll(10,TimeUnit.SECONDS));
    }
}
