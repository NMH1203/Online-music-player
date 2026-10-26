package vn.edu.usth.online_music_player.ui.profile;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import retrofit2.Response;
import vn.edu.usth.online_music_player.data.account.*;
import vn.edu.usth.online_music_player.data.account.AccountModels.*;
import vn.edu.usth.online_music_player.data.network.ApiClient;
import vn.edu.usth.online_music_player.data.session.*;

public class ProfileViewModel extends AndroidViewModel {
    public static class State {
        public boolean busy, signedIn;
        /** The last action threw; {@link #message} explains why. */
        public boolean failed;
        public String message="";
        public Profile profile;
        public Entitlement entitlement;
        /** From GET /me/stats; null when the server does not offer it yet. */
        public Stats stats;
    }
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final MutableLiveData<State> state=new MutableLiveData<>(new State());
    private final AccountApi api=ApiClient.create(AccountApi.class);
    private final SharedPreferences payments;
    private State current=new State();
    private volatile boolean busy;
    public String appAdShownFor;
    public ProfileViewModel(@NonNull Application app) {
        super(app); payments=app.getSharedPreferences("pending_mock_payments",Context.MODE_PRIVATE); refresh();
    }
    public LiveData<State> state() { return state; }
    private interface Work { String run() throws Exception; }
    private void submit(Work work) {
        if(busy) return; busy=true;
        State loading=copy(current); loading.busy=true; loading.message=""; loading.failed=false; state.setValue(loading);
        worker.execute(() -> {
            String message="";
            boolean failed=false;
            try { message=work.run(); }
            catch(Exception e) { failed=true; message=errorMessage(e, text(vn.edu.usth.online_music_player.R.string.generic_error)); }
            try { load(); }
            catch(Exception e) { if(message.isEmpty()) { failed=true; message=errorMessage(e, text(vn.edu.usth.online_music_player.R.string.profile_load_failed)); } }
            if(AccessTokenStore.getAccessToken()==null) current=new State();
            current.busy=false; current.message=message; current.failed=failed; busy=false; state.postValue(copy(current));
        });
    }
    private String errorMessage(Exception e, String fallback) {
        if(e instanceof SessionManager.AuthFailure) {
            return getApplication().getString(((SessionManager.AuthFailure) e).kind == SessionManager.AuthFailure.Kind.EMAIL_TAKEN
                ? vn.edu.usth.online_music_player.R.string.auth_error_email_taken
                : vn.edu.usth.online_music_player.R.string.auth_error_invalid_credentials);
        }
        // Connection-level failures from OkHttp carry developer text ("Failed to connect to /…").
        if(e instanceof java.net.ConnectException || e instanceof java.net.SocketTimeoutException
                || e instanceof java.net.UnknownHostException) {
            return getApplication().getString(vn.edu.usth.online_music_player.R.string.auth_error_network);
        }
        return e instanceof IOException ? e.getMessage() : fallback;
    }
    private void load() throws IOException {
        if(!SessionManager.get().restore()) { current=new State(); return; }
        Profile profile=require(api.profile().execute());
        Entitlement entitlement=require(api.entitlement().execute());
        Stats stats=null;
        try {
            Response<Stats> response=api.stats().execute();
            if(response.isSuccessful()) stats=response.body();
        } catch(IOException ignored) { /* Stats are optional decoration on Profile. */ }
        current=new State(); current.stats=stats; current.signedIn=true; current.profile=profile; current.entitlement=entitlement;
        EntitlementStore.update(entitlement);
    }
    public void refresh() { submit(() -> ""); }
    public void login(String email,String password,String name,boolean register) {
        submit(() -> { SessionManager.get().login(new Login(email.trim(),password,name.trim()),register); return ""; });
    }
    public void saveName(String name,String bio) {
        submit(() -> { require(api.edit(new Edit(name.trim(),bio)).execute()); return text(vn.edu.usth.online_music_player.R.string.profile_saved); });
    }
    public void logout() {
        submit(() -> { SessionManager.get().logout(); appAdShownFor=null; return text(vn.edu.usth.online_music_player.R.string.profile_signed_out); });
    }
    public void upgrade(String outcome) {
        if(current.profile==null) return;
        String user=current.profile.id;
        submit(() -> {
            String savedOutcome=payments.getString(user+"_outcome",null);
            if(savedOutcome!=null && !savedOutcome.equals(outcome))
                throw new IOException(text(vn.edu.usth.online_music_player.R.string.paywall_pending_other));
            String key=payments.getString(user+"_key",null);
            if(key==null) {
                key=UUID.randomUUID().toString();
                if(!payments.edit().putString(user+"_key",key).putString(user+"_outcome",outcome).commit())
                    throw new IOException(text(vn.edu.usth.online_music_player.R.string.paywall_key_failed));
            }
            Payment result=require(api.pay(new PaymentRequest(key,outcome)).execute());
            payments.edit().remove(user+"_key").remove(user+"_outcome").commit();
            EntitlementStore.update(result.entitlement);
            return "success".equals(result.status) ? text(vn.edu.usth.online_music_player.R.string.paywall_success) : text(vn.edu.usth.online_music_player.R.string.paywall_failed);
        });
    }
    public void reset() {
        submit(() -> { EntitlementStore.update(require(api.reset().execute())); return text(vn.edu.usth.online_music_player.R.string.manage_plan_done); });
    }
    private String text(int resource) { return getApplication().getString(resource); }
    private <T> T require(Response<T> response) throws IOException {
        if(!response.isSuccessful() || response.body()==null) throw SessionManager.apiError(response);
        return response.body();
    }
    private static State copy(State old) {
        State s=new State(); s.busy=old.busy; s.signedIn=old.signedIn; s.profile=old.profile;
        s.entitlement=old.entitlement; s.message=old.message; s.failed=old.failed; s.stats=old.stats; return s;
    }
    @Override protected void onCleared() { worker.shutdownNow(); }
}
