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
        public String message="";
        public Profile profile;
        public Entitlement entitlement;
        public List<History> history=Collections.emptyList();
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
        State loading=copy(current); loading.busy=true; loading.message=""; state.setValue(loading);
        worker.execute(() -> {
            String message="";
            try { message=work.run(); }
            catch(Exception e) { message=e instanceof IOException ? e.getMessage() : "Có lỗi xảy ra. Vui lòng thử lại."; }
            try { load(); }
            catch(Exception e) { if(message.isEmpty()) message=e instanceof IOException ? e.getMessage() : "Không tải được hồ sơ."; }
            if(AccessTokenStore.getAccessToken()==null) current=new State();
            current.busy=false; current.message=message; busy=false; state.postValue(copy(current));
        });
    }
    private void load() throws IOException {
        if(!SessionManager.get().restore()) { current=new State(); return; }
        Profile profile=require(api.profile().execute());
        Entitlement entitlement=require(api.entitlement().execute());
        List<History> history=current.profile!=null && current.profile.id.equals(profile.id)
            && current.entitlement!=null && current.entitlement.planCode.equals(entitlement.planCode)
            ? current.history : Collections.emptyList();
        current=new State(); current.history=history; current.signedIn=true; current.profile=profile; current.entitlement=entitlement;
        EntitlementStore.update(entitlement);
    }
    public void refresh() { submit(() -> ""); }
    public void login(String email,String password,String name,boolean register) {
        submit(() -> { SessionManager.get().login(new Login(email.trim(),password,name.trim()),register); return "Đăng nhập thành công."; });
    }
    public void saveName(String name,String bio) {
        submit(() -> { require(api.edit(new Edit(name.trim(),bio)).execute()); return "Đã lưu hồ sơ."; });
    }
    public void logout() {
        submit(() -> { SessionManager.get().logout(); appAdShownFor=null; return "Đã đăng xuất."; });
    }
    public void upgrade(String outcome) {
        if(current.profile==null) return;
        String user=current.profile.id;
        submit(() -> {
            String savedOutcome=payments.getString(user+"_outcome",null);
            if(savedOutcome!=null && !savedOutcome.equals(outcome))
                throw new IOException("Có giao dịch chưa nhận kết quả. Hãy thử lại đúng lựa chọn trước đó.");
            String key=payments.getString(user+"_key",null);
            if(key==null) {
                key=UUID.randomUUID().toString();
                if(!payments.edit().putString(user+"_key",key).putString(user+"_outcome",outcome).commit())
                    throw new IOException("Không thể lưu mã giao dịch. Vui lòng thử lại.");
            }
            Payment result=require(api.pay(new PaymentRequest(key,outcome)).execute());
            payments.edit().remove(user+"_key").remove(user+"_outcome").commit();
            EntitlementStore.update(result.entitlement);
            return "success".equals(result.status) ? "Premium đã được kích hoạt. Không có khoản tiền nào bị trừ." : "Thanh toán mô phỏng thất bại. Gói sử dụng không thay đổi.";
        });
    }
    public void reset() {
        submit(() -> { EntitlementStore.update(require(api.reset().execute())); return "Đã chuyển về Free. Playlist hiện có được giữ nguyên."; });
    }
    public void loadHistory() {
        if(busy) return;
        submit(() -> { List<History> entries=require(api.history().execute()); load(); current.history=entries;
            return entries.isEmpty() ? "Chưa có lịch sử trong phạm vi gói hiện tại." : "Đã tải lịch sử nghe."; });
    }
    private <T> T require(Response<T> response) throws IOException {
        if(!response.isSuccessful() || response.body()==null) throw SessionManager.apiError(response);
        return response.body();
    }
    private static State copy(State old) {
        State s=new State(); s.busy=old.busy; s.signedIn=old.signedIn; s.profile=old.profile;
        s.entitlement=old.entitlement; s.message=old.message; s.history=old.history; return s;
    }
    @Override protected void onCleared() { worker.shutdownNow(); }
}
