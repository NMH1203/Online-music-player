package vn.edu.usth.online_music_player.data.account;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/** Server-verified UI cache; a bounded grace period is used only for local ad playback. */
public final class EntitlementStore {
    private static final MutableLiveData<AccountModels.Entitlement> state=new MutableLiveData<>();
    private static volatile AccountModels.Entitlement latest;
    private static volatile long verifiedAt;
    public static long ageMs() { return android.os.SystemClock.elapsedRealtime() - verifiedAt; }
    private EntitlementStore() {}
    public static LiveData<AccountModels.Entitlement> observe() { return state; }
    public static AccountModels.Entitlement get() { return latest; }
    public static void update(AccountModels.Entitlement value) { verifiedAt=android.os.SystemClock.elapsedRealtime(); latest=value; state.postValue(value); }
    public static void clear() { update(null); }
}
