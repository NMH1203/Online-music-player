package vn.edu.usth.online_music_player.ui.profile;

import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import vn.edu.usth.online_music_player.BuildConfig;
import vn.edu.usth.online_music_player.R;
import vn.edu.usth.online_music_player.data.account.AccountModels.Entitlement;
import vn.edu.usth.online_music_player.data.account.AccountModels.Profile;
import vn.edu.usth.online_music_player.databinding.FragmentProfileBinding;
import vn.edu.usth.online_music_player.databinding.ItemProfileRowBinding;
import vn.edu.usth.online_music_player.ui.common.CatalogViews;

/** Figma "Profile – Free / Premium": account header, plan card, menu and sign out. */
public class ProfileFragment extends Fragment {
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault());
    private FragmentProfileBinding binding;
    private ProfileViewModel model;
    @Nullable private ProfileViewModel.State current;
    @Nullable private ProfileViewModel.State lastMessageState;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle saved) {
        binding = FragmentProfileBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        model = new ViewModelProvider(requireActivity()).get(ProfileViewModel.class);
        row(binding.rowAccount, R.drawable.ic_user, R.string.profile_account_details, this::editProfile);
        row(binding.rowNotifications, R.drawable.ic_bell, R.string.profile_notifications,
                () -> info(R.string.profile_notifications, R.string.profile_info_notifications));
        row(binding.rowHistory, R.drawable.ic_history, R.string.profile_listening_history,
                () -> Navigation.findNavController(view).navigate(R.id.history_fragment));
        row(binding.rowHelp, R.drawable.ic_help, R.string.profile_help,
                () -> info(R.string.profile_help, R.string.profile_info_help));
        row(binding.rowPrivacy, R.drawable.ic_shield_check, R.string.profile_privacy,
                () -> info(R.string.profile_privacy, R.string.profile_info_privacy));
        row(binding.rowSignOut, R.drawable.ic_log_out, R.string.profile_sign_out, model::logout);
        binding.rowSignOut.rowChevron.setVisibility(View.GONE);
        binding.profileVersion.setText(getString(R.string.profile_version, BuildConfig.VERSION_NAME));
        binding.upgradeButton.setOnClickListener(ignored -> paywall());
        binding.managePlanButton.setOnClickListener(ignored -> managePlan());
        binding.profileState.stateAction.setVisibility(View.VISIBLE);
        binding.profileState.stateAction.setOnClickListener(ignored -> model.refresh());
        model.state().observe(getViewLifecycleOwner(), this::render);
    }

    @Override
    public void onResume() {
        super.onResume();
        model.refresh();
    }

    private void render(ProfileViewModel.State state) {
        if (binding == null) return;
        current = state;
        binding.profileProgress.setVisibility(state.busy ? View.VISIBLE : View.GONE);
        if (!state.busy && !state.message.isEmpty() && state != lastMessageState) {
            lastMessageState = state;
            Snackbar.make(binding.getRoot(), state.message, Snackbar.LENGTH_LONG).show();
        }
        boolean ready = state.signedIn && state.profile != null && state.entitlement != null;
        binding.accountPanel.setVisibility(ready ? View.VISIBLE : View.GONE);
        binding.profileState.getRoot().setVisibility(!ready && !state.busy && state.failed ? View.VISIBLE : View.GONE);
        binding.profileState.stateIcon.setImageResource(R.drawable.ic_wifi_off);
        binding.profileState.stateTitle.setText(R.string.profile_load_failed);
        binding.profileState.stateMessage.setText(state.message);
        if (!ready) return;

        Profile p = state.profile;
        Entitlement e = state.entitlement;
        boolean premium = "PREMIUM".equals(e.planCode);
        binding.profileAvatar.setText(CatalogViews.initials(p.displayName));
        binding.profileName.setText(p.displayName);
        binding.profileEmail.setText(p.email);
        binding.profilePlanLabel.setText(premium ? R.string.profile_plan_premium : R.string.profile_plan_free);
        binding.profileStats.setVisibility(state.stats == null ? View.GONE : View.VISIBLE);
        if (state.stats != null) {
            binding.profileStats.setText(getString(R.string.profile_stats,
                    state.stats.validPlays, state.stats.minutesListened));
        }

        binding.planName.setText(premium ? R.string.profile_premium_card_title : R.string.profile_free_card_title);
        binding.planActive.setVisibility(premium ? View.VISIBLE : View.GONE);
        binding.planBenefits.setText(premium ? R.string.profile_premium_card_message : R.string.profile_free_card_message);
        binding.planDates.setVisibility(premium ? View.VISIBLE : View.GONE);
        binding.planStart.setText(date(e.startsAt));
        binding.planEnd.setText(e.expiresAt == null ? getString(R.string.profile_no_end_date) : date(e.expiresAt));
        binding.upgradeButton.setVisibility(premium ? View.GONE : View.VISIBLE);
        binding.managePlanButton.setVisibility(premium ? View.VISIBLE : View.GONE);
        binding.upgradeButton.setEnabled(!state.busy);
        binding.managePlanButton.setEnabled(!state.busy);
    }

    private void paywall() {
        if (current == null || current.entitlement == null) return;
        if (!current.entitlement.demoEnabled) {
            info(R.string.paywall_title, R.string.paywall_unavailable);
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.paywall_title)
                .setMessage(R.string.paywall_message)
                .setPositiveButton(R.string.paywall_activate, (d, w) -> model.upgrade("success"))
                .setNeutralButton(R.string.paywall_fail, (d, w) -> model.upgrade("failed"))
                .setNegativeButton(R.string.not_now, null)
                .show();
    }

    private void managePlan() {
        if (current == null || current.entitlement == null) return;
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_manage_plan)
                .setNegativeButton(R.string.not_now, null);
        if (current.entitlement.demoEnabled) {
            dialog.setMessage(R.string.manage_plan_message)
                    .setPositiveButton(R.string.manage_plan_reset, (d, w) -> model.reset());
        } else {
            dialog.setMessage(R.string.paywall_unavailable);
        }
        dialog.show();
    }

    private void editProfile() {
        if (current == null || current.profile == null) return;
        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = CatalogViews.dp(binding.getRoot(), 24);
        form.setPadding(pad, 0, pad, 0);
        EditText name = new EditText(requireContext());
        name.setHint(R.string.profile_edit_name);
        name.setSingleLine(true);
        name.setText(current.profile.displayName);
        name.setFilters(new InputFilter[] {new InputFilter.LengthFilter(100)});
        EditText bio = new EditText(requireContext());
        bio.setHint(R.string.profile_edit_bio);
        bio.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        bio.setFilters(new InputFilter[] {new InputFilter.LengthFilter(500)});
        bio.setText(current.profile.bio);
        form.addView(name);
        form.addView(bio);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_edit_title)
                .setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_save, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String value = name.getText().toString().trim();
                    if (value.isEmpty()) {
                        name.setError(getString(R.string.profile_edit_name_required));
                        return;
                    }
                    model.saveName(value, bio.getText().toString());
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void info(int title, int message) {
        new MaterialAlertDialogBuilder(requireContext()).setTitle(title).setMessage(message)
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private static void row(ItemProfileRowBinding row, int icon, int title, Runnable onClick) {
        row.rowIcon.setImageResource(icon);
        row.rowTitle.setText(title);
        row.getRoot().setOnClickListener(ignored -> onClick.run());
    }

    private String date(@Nullable String value) {
        if (value == null) return "—";
        try {
            return DATE.format(Instant.parse(value));
        } catch (Exception e) {
            return value;
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
