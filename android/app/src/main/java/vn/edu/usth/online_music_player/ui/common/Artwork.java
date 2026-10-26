package vn.edu.usth.online_music_player.ui.common;

import android.content.res.ColorStateList;
import android.net.Uri;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import com.bumptech.glide.Glide;
import java.util.Locale;
import vn.edu.usth.online_music_player.R;

/** Cover art and small formatting helpers shared by the player screens. */
public final class Artwork {
    private Artwork() {}

    /** Loads a cover, falling back to the music-note placeholder (demo seed has no images yet). */
    public static void load(ImageView view, @Nullable Uri uri) {
        if (uri == null) {
            Glide.with(view).clear(view);
            view.setImageResource(R.drawable.ic_music);
            view.setScaleType(ImageView.ScaleType.CENTER);
            ImageViewCompat.setImageTintList(view, ColorStateList.valueOf(
                    ContextCompat.getColor(view.getContext(), R.color.icon_secondary)));
            return;
        }
        // The placeholder tint must not darken real cover images.
        ImageViewCompat.setImageTintList(view, null);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Glide.with(view).load(uri).placeholder(R.drawable.ic_music).error(R.drawable.ic_music).into(view);
    }

    /** "3:58" from milliseconds; "0:00" for unknown durations. */
    public static String time(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
