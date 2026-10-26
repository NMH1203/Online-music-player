package vn.edu.usth.online_music_player.data.account;

import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.usth.online_music_player.data.account.AccountModels.History;
import vn.edu.usth.online_music_player.data.network.ApiClient;

/** GET /me/history; the server already limits Free accounts to their history window. */
public final class HistoryRepository {
    public interface LoadCallback {
        void onLoaded(List<History> entries);
        void onFailed();
    }

    private final AccountApi api = ApiClient.create(AccountApi.class);
    private Call<List<History>> active;

    public void load(LoadCallback callback) {
        cancel();
        active = api.history();
        active.enqueue(new Callback<List<History>>() {
            @Override
            public void onResponse(Call<List<History>> call, Response<List<History>> response) {
                if (response.isSuccessful() && response.body() != null) callback.onLoaded(response.body());
                else callback.onFailed();
            }

            @Override
            public void onFailure(Call<List<History>> call, Throwable error) {
                if (!call.isCanceled()) callback.onFailed();
            }
        });
    }

    public void cancel() {
        if (active != null) active.cancel();
    }
}
