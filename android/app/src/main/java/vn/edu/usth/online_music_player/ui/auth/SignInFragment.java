package vn.edu.usth.online_music_player.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import vn.edu.usth.online_music_player.R;
import vn.edu.usth.online_music_player.databinding.FragmentSignInBinding;
import vn.edu.usth.online_music_player.ui.profile.ProfileViewModel;

/** Figma "Sign in". Session state lives in the activity-scoped {@link ProfileViewModel}. */
public class SignInFragment extends Fragment {
    private FragmentSignInBinding binding;
    private ProfileViewModel model;
    private boolean submitted;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentSignInBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(requireActivity()).get(ProfileViewModel.class);
        binding.signInError.errorBannerTitle.setText(R.string.auth_sign_in_failed_title);
        binding.signInSubmit.setOnClickListener(ignored -> submit());
        binding.signInPassword.setOnEditorActionListener((field, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            submit();
            return true;
        });
        binding.signInGoCreate.setOnClickListener(ignored ->
                Navigation.findNavController(view).navigate(R.id.action_sign_in_to_create_account));
        model.state().observe(getViewLifecycleOwner(), this::render);
    }

    private void submit() {
        String email = text(binding.signInEmail);
        String password = text(binding.signInPassword);
        int emailError = AuthValidator.email(email);
        int passwordError = AuthValidator.password(password);
        AuthForm.showError(binding.signInEmailLayout, emailError);
        AuthForm.showError(binding.signInPasswordLayout, passwordError);
        if (emailError != 0 || passwordError != 0) return;
        submitted = true;
        model.login(email, password, "", false);
    }

    private void render(ProfileViewModel.State state) {
        if (binding == null) return;
        AuthForm.renderBusy(binding.signInSubmit, binding.signInProgress, state.busy,
                R.string.auth_action_sign_in);
        // Only show failures from this screen's own attempt, not e.g. a background refresh.
        boolean showError = submitted && !state.busy && state.failed && !state.signedIn;
        AuthForm.showBanner(binding.signInError.getRoot(), binding.signInError.errorBannerMessage,
                showError ? state.message : null);
        if (!state.busy) submitted = false;
    }

    private static String text(android.widget.EditText field) {
        return field.getText() == null ? "" : field.getText().toString();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
