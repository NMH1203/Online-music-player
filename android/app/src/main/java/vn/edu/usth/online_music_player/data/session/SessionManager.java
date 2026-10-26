package vn.edu.usth.online_music_player.data.session;

import android.content.Context;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import vn.edu.usth.online_music_player.BuildConfig;
import vn.edu.usth.online_music_player.data.account.AccountApi;
import vn.edu.usth.online_music_player.data.account.AccountModels.*;
import vn.edu.usth.online_music_player.data.account.EntitlementStore;

public final class SessionManager {
    private static SessionManager instance;
    private final SecureSessionStorage storage;
    private final SecureSessionStorage revocations;
    private final AccountApi rawApi;
    private volatile long generation;
    public static final class Stamp {
        public final long generation;
        Stamp(long generation) { this.generation=generation; }
    }
    public Stamp stamp() { return new Stamp(generation); }
    private SessionManager(Context context) {
        this(context, rawApi());
    }
    SessionManager(Context context, AccountApi api) {
        storage=new SecureSessionStorage(context);
        revocations=new SecureSessionStorage(context, "pending_revocations");
        rawApi=api;
    }
    private static AccountApi rawApi() {
        return new Retrofit.Builder().baseUrl(BuildConfig.BASE_URL)
            .client(new OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).build())
            .addConverterFactory(GsonConverterFactory.create()).build().create(AccountApi.class);
    }
    public static synchronized void initialize(Context context) {
        if(instance==null) instance=new SessionManager(context.getApplicationContext());
    }
    public static synchronized SessionManager get() {
        if(instance==null) throw new IllegalStateException("SessionManager not initialized");
        return instance;
    }
    public synchronized boolean restore() throws IOException {
        flushRevocations();
        if(AccessTokenStore.getAccessToken()!=null) return true;
        return refreshLocked();
    }
    /** Login/register failures the UI shows with its own copy. */
    public static final class AuthFailure extends IOException {
        public enum Kind { INVALID_CREDENTIALS, EMAIL_TAKEN }
        public final Kind kind;
        AuthFailure(Kind kind) { super(kind.name()); this.kind=kind; }
    }
    private final androidx.lifecycle.MutableLiveData<Boolean> expired=new androidx.lifecycle.MutableLiveData<>(false);
    /** True after the server rejected the stored refresh token; the UI asks the user to sign in again. */
    public androidx.lifecycle.LiveData<Boolean> expired() { return expired; }
    public void acknowledgeExpired() { expired.postValue(false); }
    public synchronized void login(Login login, boolean register) throws IOException {
        flushRevocations();
        retrofit2.Response<Session> response=(register ? rawApi.register(login) : rawApi.login(login)).execute();
        if(response.code()==401) throw new AuthFailure(AuthFailure.Kind.INVALID_CREDENTIALS);
        if(register && response.code()==409) throw new AuthFailure(AuthFailure.Kind.EMAIL_TAKEN);
        if(!response.isSuccessful() || response.body()==null) throw apiError(response);
        save(response.body());
        generation++;
        EntitlementStore.clear();
    }
    public synchronized void logout() throws IOException {
        String refresh=storage.read();
        try {
            if(refresh!=null) {
                // Persist revocation before attempting the network, so process death cannot lose it.
                java.util.List<String> queued=queuedRevocations();
                if(!queued.contains(refresh)) queued.add(refresh);
                revocations.write(new com.google.gson.Gson().toJson(queued));
            }
        } catch(Exception e) {
            clear();
            throw new IOException("Signed out on this device, but the server-side sign-out could not be saved.",e);
        }
        clear();
        flushRevocations();
        if(!queuedRevocations().isEmpty()) throw new IOException("Signed out on this device. The server session will be revoked when you are back online.");
    }
    private java.util.List<String> queuedRevocations() {
        String json=revocations.read();
        if(json==null) return new java.util.ArrayList<>();
        return new java.util.ArrayList<>(java.util.Arrays.asList(new com.google.gson.Gson().fromJson(json,String[].class)));
    }
    private void flushRevocations() {
        java.util.List<String> queued=queuedRevocations();
        if(queued.isEmpty()) return;
        try {
            for(String token:new java.util.ArrayList<>(queued)) {
                retrofit2.Response<Void> response=rawApi.logout(new Refresh(token)).execute();
                if(!response.isSuccessful() && response.code()!=401) return;
                queued.remove(token);
                if(queued.isEmpty()) revocations.clear();
                else revocations.write(new com.google.gson.Gson().toJson(queued));
            }
        } catch(Exception ignored) { /* Retain encrypted revocations for the next restore/login. */ }
    }
    private void save(Session session) throws IOException {
        try { storage.write(session.refreshToken); }
        catch(Exception e) { clear(); throw new IOException("Couldn't store the session securely. Sign in again.",e); }
        AccessTokenStore.replaceAccessToken(session.accessToken);
    }
    private boolean refreshLocked() throws IOException {
        String refresh=storage.read();
        if(refresh==null) return false;
        retrofit2.Response<Session> response=rawApi.refresh(new Refresh(refresh)).execute();
        if(response.code()==401 || response.code()==403) { clear(); expired.postValue(true); return false; }
        if(!response.isSuccessful() || response.body()==null) throw apiError(response);
        save(response.body()); return true;
    }
    public synchronized Request authenticate(Response response) throws IOException {
        Stamp stamp=response.request().tag(Stamp.class);
        if(stamp!=null && stamp.generation!=generation) return null;
        int attempts=0; for(Response r=response; r!=null; r=r.priorResponse()) attempts++;
        if(attempts>1) return null;
        String current=AccessTokenStore.getAccessToken();
        String sent=response.request().header("Authorization");
        // Single flight: a second concurrent 401 reuses the token already refreshed by the first.
        if(current!=null && !("Bearer "+current).equals(sent))
            return response.request().newBuilder().header("Authorization","Bearer "+current).build();
        if(!refreshLocked()) return null;
        return response.request().newBuilder().header("Authorization","Bearer "+AccessTokenStore.getAccessToken()).build();
    }
    public synchronized void clear() {
        generation++;
        storage.clear(); AccessTokenStore.clear(); EntitlementStore.clear();
    }
    public static IOException apiError(retrofit2.Response<?> response) {
        String message="Request failed ("+response.code()+").";
        try {
            if(response.errorBody()!=null) {
                com.google.gson.JsonObject error=new com.google.gson.Gson().fromJson(response.errorBody().string(),com.google.gson.JsonObject.class);
                if(error!=null && error.has("message")) message=error.get("message").getAsString();
            }
        } catch(Exception ignored) {}
        return new IOException(message);
    }
}
