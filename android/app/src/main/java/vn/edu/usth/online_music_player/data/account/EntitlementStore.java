package vn.edu.usth.online_music_player.data.account;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/** Cache for presentation only; all permission decisions still come from the API. */
public final class EntitlementStore {
    private static final MutableLiveData<AccountModels.Entitlement> state=new MutableLiveData<>();
    private static volatile AccountModels.Entitlement latest;
    private EntitlementStore() {}
    public static LiveData<AccountModels.Entitlement> observe() { return state; }
    public static AccountModels.Entitlement get() { return latest; }
    public static void update(AccountModels.Entitlement value) { latest=value; state.postValue(value); }
    public static void clear() { update(null); }
}
