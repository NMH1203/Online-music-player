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
import vn.edu.usth.online_music_player.databinding.FragmentCreateAccountBinding;
import vn.edu.usth.online_music_player.ui.profile.ProfileViewModel;

/** Figma "Create account". Registers through the activity-scoped {@link ProfileViewModel}. */
public class CreateAccountFragment extends Fragment {
    private FragmentCreateAccountBinding binding;
    private ProfileViewModel model;
    private boolean submitted;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentCreateAccountBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(requireActivity()).get(ProfileViewModel.class);
        binding.createError.errorBannerTitle.setText(R.string.auth_create_failed_title);
        binding.createSubmit.setOnClickListener(ignored -> submit());
        binding.createPassword.setOnEditorActionListener((field, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            submit();
            return true;
        });
        binding.createGoSignIn.setOnClickListener(ignored ->
                Navigation.findNavController(view).navigateUp());
        model.state().observe(getViewLifecycleOwner(), this::render);
    }

    private void submit() {
        String name = text(binding.createName);
        String email = text(binding.createEmail);
        String password = text(binding.createPassword);
        int nameError = AuthValidator.displayName(name);
        int emailError = AuthValidator.email(email);
        int passwordError = AuthValidator.password(password);
        AuthForm.showError(binding.createNameLayout, nameError);
        AuthForm.showError(binding.createEmailLayout, emailError);
        AuthForm.showError(binding.createPasswordLayout, passwordError);
        if (passwordError == 0) {
            binding.createPasswordLayout.setHelperText(getString(R.string.auth_password_helper));
        }
        if (nameError != 0 || emailError != 0 || passwordError != 0) return;
        submitted = true;
        model.login(email, password, name, true);
    }

    private void render(ProfileViewModel.State state) {
        if (binding == null) return;
        AuthForm.renderBusy(binding.createSubmit, binding.createProgress, state.busy,
                R.string.auth_action_create_account);
        boolean showError = submitted && !state.busy && state.failed && !state.signedIn;
        AuthForm.showBanner(binding.createError.getRoot(), binding.createError.errorBannerMessage,
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
