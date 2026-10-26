package vn.edu.usth.online_music_player.ui.auth;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.google.android.material.textfield.TextInputLayout;

/** View helpers shared by the Sign in and Create account screens. */
final class AuthForm {
    private AuthForm() {}

    static void showError(TextInputLayout field, int messageRes) {
        field.setError(messageRes == 0 ? null : field.getContext().getString(messageRes));
    }

    static void renderBusy(Button submit, View progress, boolean busy, int labelRes) {
        submit.setEnabled(!busy);
        // The spinner sits on top of the button, so drop the label while the request runs.
        submit.setText(busy ? "" : submit.getContext().getString(labelRes));
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    static void showBanner(View banner, TextView message, String text) {
        boolean visible = text != null && !text.isEmpty();
        message.setText(visible ? text : "");
        banner.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
