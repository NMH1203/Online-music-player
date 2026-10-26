package vn.edu.usth.online_music_player.ui.history;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import vn.edu.usth.online_music_player.MainActivity;
import vn.edu.usth.online_music_player.R;
import vn.edu.usth.online_music_player.data.account.AccountModels.Entitlement;
import vn.edu.usth.online_music_player.data.account.AccountModels.History;
import vn.edu.usth.online_music_player.data.account.HistoryRepository;
import vn.edu.usth.online_music_player.ui.common.CatalogViews;
import vn.edu.usth.online_music_player.databinding.FragmentHistoryBinding;
import vn.edu.usth.online_music_player.ui.profile.ProfileViewModel;

/** Figma "Listening history": plays grouped by day, the Free window note, empty and error states. */
public class HistoryFragment extends Fragment {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("EEE · h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM d · h:mm a", Locale.ENGLISH);
    private final HistoryRepository repository = new HistoryRepository();
    private FragmentHistoryBinding binding;
    @Nullable private Entitlement entitlement;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.historyTopBar.topBarTitle.setText(R.string.history_title);
        binding.historyTopBar.topBarBack.setOnClickListener(ignored -> Navigation.findNavController(view).navigateUp());
        binding.historyPremium.setOnClickListener(ignored -> Navigation.findNavController(view).navigateUp());
        binding.historyState.stateAction.setVisibility(View.VISIBLE);
        new ViewModelProvider(requireActivity()).get(ProfileViewModel.class).state()
                .observe(getViewLifecycleOwner(), state -> entitlement = state.entitlement);
        load();
    }

    private void load() {
        binding.historyProgress.setVisibility(View.VISIBLE);
        binding.historyState.getRoot().setVisibility(View.GONE);
        binding.historyList.removeAllViews();
        binding.historyFreeCard.setVisibility(View.GONE);
        repository.load(new HistoryRepository.LoadCallback() {
            @Override
            public void onLoaded(List<History> entries) {
                if (binding == null) return;
                binding.historyProgress.setVisibility(View.GONE);
                if (entries.isEmpty()) {
                    state(R.drawable.ic_history, R.string.history_empty_title, R.string.history_empty_message,
                            R.string.queue_empty_action, () -> Navigation.findNavController(binding.getRoot())
                                    .navigate(R.id.navigation_home, null, new NavOptions.Builder()
                                            .setPopUpTo(R.id.navigation_home, false).build()));
                } else {
                    render(entries);
                }
                Integer days = entitlement == null ? null : entitlement.historyDays;
                binding.historyFreeCard.setVisibility(days == null ? View.GONE : View.VISIBLE);
                if (days != null) binding.historyFreeMessage.setText(getString(R.string.history_free_message, days));
            }

            @Override
            public void onFailed() {
                if (binding == null) return;
                binding.historyProgress.setVisibility(View.GONE);
                state(R.drawable.ic_wifi_off, R.string.history_error_title, R.string.detail_error_message,
                        R.string.now_playing_retry, HistoryFragment.this::load);
            }
        });
    }

    private void render(List<History> entries) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        List<String> trackIds = new ArrayList<>();
        for (History entry : entries) trackIds.add(entry.track_id);
        int lastSection = 0;
        for (int i = 0; i < entries.size(); i++) {
            History entry = entries.get(i);
            ZonedDateTime played = parse(entry.started_at, zone);
            int section = played == null ? R.string.history_older : HistoryGroups.titleFor(played.toLocalDate(), today);
            if (section != lastSection) {
                lastSection = section;
                TextView header = (TextView) getLayoutInflater().inflate(
                        R.layout.view_history_header, binding.historyList, false);
                header.setText(section);
                binding.historyList.addView(header);
            }
            String when = played == null ? "" : (section == R.string.history_today || section == R.string.history_yesterday
                    ? TIME : section == R.string.history_this_week ? DAY_TIME : DATE_TIME).format(played);
            String subtitle = (entry.artist_name == null ? "" : entry.artist_name) + (when.isEmpty() ? "" : " · " + when);
            int start = i;
            CatalogViews.songRow(binding.historyList, entry.title, subtitle, entry.cover_url, R.drawable.ic_play,
                    entry.track_id == null ? null
                            : () -> ((MainActivity) requireActivity()).playTrackIds(trackIds, start));
        }
    }

    @Nullable
    private static ZonedDateTime parse(@Nullable String value, ZoneId zone) {
        try {
            return value == null ? null : Instant.parse(value).atZone(zone);
        } catch (Exception e) {
            return null;
        }
    }

    private void state(int icon, int title, int message, int action, Runnable onAction) {
        binding.historyState.getRoot().setVisibility(View.VISIBLE);
        binding.historyState.stateIcon.setImageResource(icon);
        binding.historyState.stateTitle.setText(title);
        binding.historyState.stateMessage.setText(message);
        binding.historyState.stateAction.setText(action);
        binding.historyState.stateAction.setOnClickListener(ignored -> onAction.run());
    }

    @Override
    public void onDestroyView() {
        repository.cancel();
        binding = null;
        super.onDestroyView();
    }
}
