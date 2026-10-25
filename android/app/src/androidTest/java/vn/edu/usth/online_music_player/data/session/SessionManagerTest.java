package vn.edu.usth.online_music_player.data.session;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import vn.edu.usth.online_music_player.data.account.*;

@RunWith(AndroidJUnit4.class)
public class SessionManagerTest {
    private MockWebServer server;
    private SessionManager sessions;
    private Context context;
    @Before public void setup() throws Exception {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        server=new MockWebServer(); server.start();
        sessions=new SessionManager(context,new Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create()).build().create(AccountApi.class));
        sessions.clear();
        new SecureSessionStorage(context,"pending_revocations").clear();
    }
    private MockResponse json(String body) { return new MockResponse().setBody(body).addHeader("Content-Type","application/json"); }
    private void login() throws Exception {
        server.enqueue(json("{\"accessToken\":\"old-access\",\"refreshToken\":\"original-refresh\",\"userId\":\"test\"}"));
        sessions.login(new AccountModels.Login("demo@example.test","Password123","Demo"),false);
        server.takeRequest(3,TimeUnit.SECONDS);
    }
    @After public void teardown() throws Exception { sessions.clear(); new SecureSessionStorage(context,"pending_revocations").clear(); server.shutdown(); }
    @Test public void refreshTokenIsEncryptedAndAccessIsMemoryOnly() throws Exception {
        login();
        var prefs=context.getSharedPreferences("secure_session",Context.MODE_PRIVATE).getAll();
        assertFalse(prefs.toString().contains("original-refresh"));
        assertFalse(prefs.toString().contains("old-access"));
        assertEquals("original-refresh",new SecureSessionStorage(context).read());
        AccessTokenStore.clear();
        server.enqueue(json("{\"accessToken\":\"new-access\",\"refreshToken\":\"rotated-refresh\"}"));
        assertTrue(sessions.restore());
        assertEquals("new-access",AccessTokenStore.getAccessToken());
        assertEquals("rotated-refresh",new SecureSessionStorage(context).read());
    }
    @Test public void simultaneous401ResponsesRefreshOnlyOnce() throws Exception {
        login();
        AtomicInteger refreshes=new AtomicInteger();
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                if("/auth/refresh".equals(request.getPath())) {
                    refreshes.incrementAndGet();
                    return json("{\"accessToken\":\"new-access\",\"refreshToken\":\"new-refresh\"}");
                }
                return "Bearer new-access".equals(request.getHeader("Authorization")) ? json("{}") : new MockResponse().setResponseCode(401);
            }
        });
        OkHttpClient client=new OkHttpClient.Builder().authenticator((route,response)->sessions.authenticate(response)).build();
        Request request=new Request.Builder().url(server.url("/protected")).header("Authorization","Bearer old-access")
            .tag(SessionManager.Stamp.class,sessions.stamp()).build();
        ExecutorService pool=Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> calls=new ArrayList<>();
            for(int i=0;i<4;i++) calls.add(()-> { try(Response response=client.newCall(request).execute()) { return response.code(); } });
            for(Future<Integer> result:pool.invokeAll(calls)) assertEquals(Integer.valueOf(200),result.get(15,TimeUnit.SECONDS));
            assertEquals(1,refreshes.get());
        } finally { pool.shutdownNow(); }
    }
    @Test public void invalidRefreshClearsSessionButTemporaryFailurePreservesIt() throws Exception {
        login(); AccessTokenStore.clear();
        server.enqueue(new MockResponse().setResponseCode(503));
        try { sessions.restore(); fail(); } catch(java.io.IOException expected) {}
        assertEquals("original-refresh",new SecureSessionStorage(context).read());
        server.enqueue(new MockResponse().setResponseCode(401));
        assertFalse(sessions.restore());
        assertNull(new SecureSessionStorage(context).read());
        assertNull(AccessTokenStore.getAccessToken());
    }
    @Test public void logoutRevokesBeforeClearingAndOldRequestsCannotRetryAsAnotherAccount() throws Exception {
        login();
        SessionManager.Stamp stamp=sessions.stamp();
        server.enqueue(new MockResponse().setResponseCode(200));
        sessions.logout();
        assertNull(AccessTokenStore.getAccessToken()); assertNull(new SecureSessionStorage(context).read());
        Request old=new Request.Builder().url(server.url("/protected")).header("Authorization","Bearer old-access")
            .tag(SessionManager.Stamp.class,stamp).build();
        Response failure=new Response.Builder().request(old).protocol(Protocol.HTTP_1_1).code(401).message("Unauthorized").build();
        assertNull(sessions.authenticate(failure));
    }
}
