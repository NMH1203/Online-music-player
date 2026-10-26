package vn.edu.usth.online_music_player.ui.common;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.navigation.Navigation;
import java.util.Locale;
import vn.edu.usth.online_music_player.R;
import vn.edu.usth.online_music_player.data.playback.PlaybackManager;
import vn.edu.usth.online_music_player.databinding.ItemAlbumCardBinding;
import vn.edu.usth.online_music_player.databinding.ItemAlbumTileBinding;
import vn.edu.usth.online_music_player.databinding.ItemArtistCircleBinding;
import vn.edu.usth.online_music_player.databinding.ItemSongRowBinding;

/** Inflates the shared Figma rows/cards (M3 layouts) into plain LinearLayout containers. */
public final class CatalogViews {
    private CatalogViews() {}

    public static ItemSongRowBinding songRow(ViewGroup parent, CharSequence title, CharSequence subtitle,
            @Nullable String coverUrl, @DrawableRes int actionIcon, @Nullable Runnable onClick) {
        ItemSongRowBinding row = ItemSongRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        row.songTitle.setText(title);
        row.songSubtitle.setText(subtitle);
        cover(row.songArtwork, coverUrl);
        if (actionIcon == 0) {
            row.songAction.setVisibility(View.GONE);
        } else {
            row.songAction.setImageResource(actionIcon);
            // The row itself is the touch target; the icon only hints at the action.
            row.songAction.setClickable(false);
            row.songAction.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        if (onClick != null) row.getRoot().setOnClickListener(ignored -> onClick.run());
        else row.getRoot().setEnabled(false);
        parent.addView(row.getRoot());
        return row;
    }

    public static ItemAlbumCardBinding albumCard(ViewGroup parent, CharSequence title, CharSequence subtitle,
            @Nullable String coverUrl, @Nullable Runnable onClick) {
        ItemAlbumCardBinding card = ItemAlbumCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        card.albumTitle.setText(title);
        card.albumSubtitle.setText(subtitle);
        cover(card.albumArtwork, coverUrl);
        if (onClick != null) card.getRoot().setOnClickListener(ignored -> onClick.run());
        parent.addView(card.getRoot());
        return card;
    }

    public static ItemAlbumTileBinding albumTile(ViewGroup parent, CharSequence title, CharSequence subtitle,
            @Nullable String coverUrl, @Nullable Runnable onClick) {
        ItemAlbumTileBinding tile = ItemAlbumTileBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        tile.albumTitle.setText(title);
        tile.albumSubtitle.setText(subtitle);
        cover(tile.albumArtwork, coverUrl);
        if (onClick != null) tile.getRoot().setOnClickListener(ignored -> onClick.run());
        parent.addView(tile.getRoot());
        return tile;
    }

    public static void artistCircle(ViewGroup parent, CharSequence name, @Nullable String coverUrl,
            @Nullable Runnable onClick) {
        ItemArtistCircleBinding item = ItemArtistCircleBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        item.artistName.setText(name);
        cover(item.artistPortrait, coverUrl);
        if (coverUrl == null) item.artistPortrait.setImageResource(R.drawable.ic_user);
        if (onClick != null) item.getRoot().setOnClickListener(ignored -> onClick.run());
        parent.addView(item.getRoot());
    }

    /** A horizontal row whose children share the width equally (pairs of cards on Home/Search). */
    public static LinearLayout equalRow(ViewGroup parent, int gapDp) {
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(parent, gapDp);
        parent.addView(row, params);
        return row;
    }

    /** Gives every child of an {@link #equalRow} the same weight and a gap between them. */
    public static void spread(LinearLayout row, int gapDp, int slots) {
        for (int i = 0; i < row.getChildCount(); i++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) params.setMarginStart(dp(row, gapDp));
            row.getChildAt(i).setLayoutParams(params);
        }
        // Keep the last card half-width when a pair is incomplete.
        for (int i = row.getChildCount(); i < slots; i++) {
            View spacer = new View(row.getContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 1, 1f);
            params.setMarginStart(dp(row, gapDp));
            row.addView(spacer, params);
        }
    }

    public static void cover(ImageView view, @Nullable String coverUrl) {
        String url = PlaybackManager.absoluteUrl(coverUrl);
        Artwork.load(view, url == null ? null : Uri.parse(url));
    }

    public static String trackSubtitle(@Nullable String artist, int durationSeconds) {
        String name = artist == null || artist.isEmpty() ? "Unknown artist" : artist;
        return durationSeconds <= 0 ? name
                : name + " · " + String.format(Locale.ROOT, "%d:%02d", durationSeconds / 60, durationSeconds % 60);
    }

    /** Ids of the playable tracks, in order; shuffled for the "Shuffle" buttons when asked. */
    public static java.util.List<String> playableIds(
            @Nullable java.util.List<vn.edu.usth.online_music_player.data.catalog.CatalogModels.Track> tracks,
            boolean shuffle) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        if (tracks != null) {
            for (vn.edu.usth.online_music_player.data.catalog.CatalogModels.Track track : tracks) {
                if (track.playable && track.id != null) ids.add(track.id);
            }
        }
        if (shuffle) java.util.Collections.shuffle(ids);
        return ids;
    }

    public static void open(View from, int destination, String argument, @Nullable String id) {
        if (id == null || id.trim().isEmpty()) return;
        Bundle args = new Bundle();
        args.putString(argument, id);
        Navigation.findNavController(from).navigate(destination, args);
    }

    /**
     * getQuantityString with English plural rules: the copy is English, but on a Vietnamese device
     * Android would always pick the "other" form ("1 results").
     */
    @SuppressWarnings("deprecation")
    public static String plural(android.content.res.Resources res, int pluralsId, int quantity, Object... args) {
        android.content.res.Configuration config = new android.content.res.Configuration(res.getConfiguration());
        config.setLocale(Locale.ENGLISH);
        return new android.content.res.Resources(res.getAssets(), res.getDisplayMetrics(), config)
                .getQuantityString(pluralsId, quantity, args);
    }

    /** "Alex Lane" -> "AL" for the avatar circles on Home and Profile. */
    public static String initials(@Nullable String name) {
        if (name == null || name.trim().isEmpty()) return "";
        String[] parts = name.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(java.util.Locale.ROOT);
    }

    public static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}
