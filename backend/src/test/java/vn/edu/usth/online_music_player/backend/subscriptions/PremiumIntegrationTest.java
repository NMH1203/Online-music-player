package vn.edu.usth.online_music_player.backend.subscriptions;

import static org.junit.Assert.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import org.springframework.http.MediaType;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import vn.edu.usth.online_music_player.backend.auth.*;
import vn.edu.usth.online_music_player.backend.common.*;
import vn.edu.usth.online_music_player.backend.playlists.*;
import vn.edu.usth.online_music_player.backend.profile.ProfileController;

/** Runs real PostgreSQL 16 and every existing migration, including deferred user/subscription constraints. */
public class PremiumIntegrationTest {
    private static EmbeddedPostgres postgres;
    private static AnnotationConfigApplicationContext context;
    private static DataSource source;
    private static JdbcTemplate db;
    private AuthService auth;
    private SubscriptionService subscriptions;
    private MockMvc http;
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() { return source; }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean TokenService tokenService() { return new TokenService("test-only-32-byte-secret-never-use-in-production"); }
        @Bean AuthService authService(JdbcTemplate jdbc,TokenService tokens) { return new AuthService(jdbc,tokens); }
        @Bean SubscriptionService subscriptions(JdbcTemplate jdbc) { return new SubscriptionService(jdbc,true); }
        @Bean PlaylistService playlists(JdbcTemplate jdbc) { return new PlaylistService(jdbc); }
    }
    @BeforeClass public static void start() throws Exception {
        postgres=EmbeddedPostgres.builder().setPort(0).start();
        source=postgres.getPostgresDatabase(); db=new JdbcTemplate(source);
        try(var files=Files.list(Path.of("../database/migrations"))) {
            for(Path path:files.filter(p->p.toString().endsWith(".sql")).sorted().toList()) {
                try(var connection=source.getConnection();var statement=connection.createStatement()) {
                    statement.execute(Files.readString(path));
                }
            }
        }
        context=new AnnotationConfigApplicationContext(Config.class);
    }
    @AfterClass public static void stop() throws Exception {
        if(context!=null) context.close();
        if(postgres!=null) postgres.close();
    }
    @Before public void setup() {
        db.execute("TRUNCATE users CASCADE");
        auth=context.getBean(AuthService.class); subscriptions=context.getBean(SubscriptionService.class);
        CurrentUserId user=new CurrentUserId();
        http=MockMvcBuilders.standaloneSetup(new AuthController(auth),new SubscriptionController(subscriptions,user),
            new ProfileController(db,user,subscriptions))
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new BearerTokenFilter(context.getBean(TokenService.class),auth)).build();
    }
    private AuthService.Session user(String name) { return auth.register(name+"@example.test","TestPassword123!",name); }
    @Test public void fullSpringBootServerStartsAndServesRegistration() throws Exception {
        String url;
        try(var connection=source.getConnection()) { url=connection.getMetaData().getURL(); }
        var app=new org.springframework.boot.SpringApplication(
            vn.edu.usth.online_music_player.backend.OnlineMusicBackendApplication.class);
        try(var running=app.run("--server.port=0", "--auth.jwt-secret=test-only-32-byte-secret-never-use-in-production",
                "--spring.datasource.url="+url, "--spring.datasource.username=postgres", "--spring.datasource.password=postgres",
                "--storage.access-key=test-only-access", "--storage.secret-key=test-only-secret", "--demo.enabled=true")) {
            String port=running.getEnvironment().getProperty("local.server.port");
            var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/auth/register"))
                .header("Content-Type","application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                    "{\"email\":\"boot@example.test\",\"password\":\"Password123!\",\"displayName\":\"Boot test\"}")).build();
            var response=java.net.http.HttpClient.newHttpClient().send(request,java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200,response.statusCode()); assertTrue(response.body().contains("accessToken"));
        }
    }
    @Test public void freePremiumFreeTwiceAndOldPaymentCannotUpgradeAgain() {
        UUID user=user("demo").userId();
        for(int round=0;round<2;round++) {
            Entitlement free=subscriptions.current(user);
            assertFalse(free.adFree()); assertEquals(Integer.valueOf(3),free.maxPlaylists());
            assertEquals(Integer.valueOf(7),free.historyDays());
            UUID key=UUID.randomUUID();
            var upgraded=subscriptions.pay(user,key,"success");
            assertTrue(upgraded.entitlement().adFree()); assertNull(upgraded.entitlement().maxPlaylists());
            assertNull(upgraded.entitlement().historyDays());
            assertTrue(upgraded.entitlement().expiresAt().isAfter(Instant.now()));
            assertEquals(upgraded.paymentId(),subscriptions.pay(user,key,"success").paymentId());
            assertEquals("FREE",subscriptions.reset(user).planCode());
            assertEquals("FREE",subscriptions.pay(user,key,"success").entitlement().planCode());
        }
        assertEquals(Long.valueOf(1),db.queryForObject("SELECT count(*) FROM subscriptions WHERE user_id=? AND status='active'",Long.class,user));
        assertEquals(Long.valueOf(2),db.queryForObject("SELECT count(*) FROM payment_events",Long.class));
    }
    @Test public void failedPaymentAndDisabledDemoNeverGrantPremium() {
        UUID user=user("failure").userId();
        assertEquals("failed",subscriptions.pay(user,UUID.randomUUID(),"failed").status());
        assertEquals("FREE",subscriptions.current(user).planCode());
        try { new SubscriptionService(db,false).pay(user,UUID.randomUUID(),"success"); fail(); }
        catch(ApiException e) { assertEquals("demo_disabled",e.code()); }
        assertEquals(Long.valueOf(1),db.queryForObject("SELECT count(*) FROM payment_events",Long.class));
    }
    @Test public void reusedKeyCannotChangePayloadOrOwner() {
        UUID first=user("first").userId(),second=user("second").userId(),key=UUID.randomUUID();
        subscriptions.pay(first,key,"success");
        try { subscriptions.pay(second,key,"success"); fail(); } catch(ApiException e) { assertEquals("idempotency_conflict",e.code()); }
        try { subscriptions.pay(first,key,"failed"); fail(); } catch(ApiException e) { assertEquals("idempotency_conflict",e.code()); }
        assertEquals("FREE",subscriptions.current(second).planCode());
    }
    @Test public void concurrentDuplicatePaymentsHaveOneEventAndSubscription() throws Exception {
        UUID user=user("parallel").userId(),key=UUID.randomUUID();
        ExecutorService pool=Executors.newFixedThreadPool(6);
        try {
            List<Callable<SubscriptionService.Payment>> jobs=new ArrayList<>();
            for(int i=0;i<6;i++) jobs.add(()->subscriptions.pay(user,key,"success"));
            Set<UUID> events=new HashSet<>();
            for(Future<SubscriptionService.Payment> f:pool.invokeAll(jobs)) events.add(f.get(30,TimeUnit.SECONDS).paymentId());
            assertEquals(1,events.size());
            assertEquals(Long.valueOf(1),db.queryForObject("SELECT count(*) FROM subscriptions WHERE user_id=? AND status='active'",Long.class,user));
        } finally { pool.shutdownNow(); }
    }
    @Test public void expiredPremiumImmediatelyAppliesFreeQuotaThenReplacesSubscription() {
        UUID user=user("expired").userId();
        subscriptions.pay(user,UUID.randomUUID(),"success");
        db.update("UPDATE subscriptions SET starts_at=now()-interval '31 days', expires_at=now()-interval '1 day' WHERE user_id=? AND status='active'",user);
        PlaylistService playlists=context.getBean(PlaylistService.class);
        assertEquals(Integer.valueOf(3),playlists.getPlaylistQuota(user).maxPlaylists());
        assertEquals("FREE",subscriptions.current(user).planCode());
        assertEquals(Long.valueOf(1),db.queryForObject("SELECT count(*) FROM subscriptions WHERE user_id=? AND status='expired'",Long.class,user));
    }
    @Test public void resetPreservesPlaylistsButPreventsNewFreePlaylistBeyondQuota() {
        UUID user=user("quota").userId();
        subscriptions.pay(user,UUID.randomUUID(),"success");
        PlaylistService playlists=context.getBean(PlaylistService.class);
        for(int i=0;i<4;i++) playlists.createPlaylist(user,new CreatePlaylistRequest("List "+i,null));
        subscriptions.reset(user);
        assertEquals(4,playlists.listPlaylists(user).size());
        try { playlists.createPlaylist(user,new CreatePlaylistRequest("Blocked",null)); fail(); }
        catch(ApiException e) { assertEquals("playlist_limit_reached",e.code()); }
    }
    @Test public void refreshRotatesAndReplayRevokesEntireFamily() {
        AuthService.Session first=user("session");
        AuthService.Session next=auth.refresh(first.refreshToken());
        assertNotEquals(first.refreshToken(),next.refreshToken());
        assertTrue(auth.validIdentity(context.getBean(TokenService.class).verify(next.accessToken())));
        try { auth.refresh(first.refreshToken()); fail(); } catch(ApiException expected) {}
        assertFalse(auth.validIdentity(context.getBean(TokenService.class).verify(next.accessToken())));
        try { auth.refresh(next.refreshToken()); fail(); } catch(ApiException expected) {}
    }
    @Test public void logoutRevokesAccessAndRefreshAndCanBeRepeated() {
        AuthService.Session session=user("logout");
        auth.logout(session.refreshToken()); auth.logout(session.refreshToken());
        assertFalse(auth.validIdentity(context.getBean(TokenService.class).verify(session.accessToken())));
        try { auth.refresh(session.refreshToken()); fail(); } catch(ApiException expected) {}
    }
    @Test public void loginValidationAndExpiredRefresh() {
        AuthService.Session session=user("login");
        assertEquals(session.userId(),auth.login("LOGIN@example.test","TestPassword123!").userId());
        try { auth.login("login@example.test","wrong"); fail(); } catch(ApiException expected) {}
        db.update("UPDATE user_sessions SET created_at=now()-interval '8 days', expires_at=now()-interval '1 day' WHERE user_id=?",session.userId());
        try { auth.refresh(session.refreshToken()); fail(); } catch(ApiException expected) {}
    }
    @Test public void httpAuthValidationAdsAndProfileContract() throws Exception {
        assertEquals(401,http.perform(get("/me/entitlements")).andReturn().getResponse().getStatus());
        assertEquals(401,http.perform(get("/me/profile").header("Authorization","Bearer forged")).andReturn().getResponse().getStatus());
        var session=user("http"); String bearer="Bearer "+session.accessToken();
        assertEquals(400,http.perform(patch("/me/profile").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
            .content("{\"displayName\":\"  \"}")).andReturn().getResponse().getStatus());
        var edited=http.perform(patch("/me/profile").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
            .content("{\"displayName\":\"Tiệp\",\"bio\":\"Music lover\"}")).andReturn().getResponse();
        assertEquals(200,edited.getStatus()); assertTrue(edited.getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains("Tiệp"));
        assertTrue(http.perform(get("/me/ads/policy").header("Authorization",bearer)).andReturn().getResponse().getContentAsString().contains("\"showAppOpen\":true"));
        subscriptions.pay(session.userId(),UUID.randomUUID(),"success");
        assertTrue(http.perform(get("/me/ads/policy").header("Authorization",bearer)).andReturn().getResponse().getContentAsString().contains("\"playBetweenTracks\":false"));
        assertEquals(400,http.perform(post("/demo/payments").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
            .content("{\"idempotencyKey\":\"invalid\",\"outcome\":\"success\"}")).andReturn().getResponse().getStatus());
        auth.logout(session.refreshToken());
        assertEquals(401,http.perform(get("/me/profile").header("Authorization",bearer)).andReturn().getResponse().getStatus());
    }
    @Test public void historyIsLimitedOnServerByPlan() throws Exception {
        var session=user("history"); UUID artist=UUID.randomUUID(),track=UUID.randomUUID();
        db.update("INSERT INTO artists(id,name,slug) VALUES (?,'Artist','history-artist')",artist);
        db.update("INSERT INTO tracks(id,primary_artist_id,title,duration_seconds,audio_object_key,file_size_bytes) VALUES (?,?,'Old song',60,?,100)",track,artist,track+".mp3");
        db.update("""
            INSERT INTO listening_history(client_event_id,user_id,track_id,source_context,started_at,listened_seconds,counted_as_play)
            VALUES (?,?,?,'test',now()-interval '10 days',60,true)
            """,UUID.randomUUID(),session.userId(),track);
        String bearer="Bearer "+session.accessToken();
        assertEquals("[]",http.perform(get("/me/history").header("Authorization",bearer)).andReturn().getResponse().getContentAsString());
        subscriptions.pay(session.userId(),UUID.randomUUID(),"success");
        assertTrue(http.perform(get("/me/history").header("Authorization",bearer)).andReturn().getResponse().getContentAsString().contains("Old song"));
        subscriptions.reset(session.userId());
        assertEquals("[]",http.perform(get("/me/history").header("Authorization",bearer)).andReturn().getResponse().getContentAsString());
    }
}
