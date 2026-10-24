package vn.edu.usth.online_music_player.ui;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import vn.edu.usth.online_music_player.MainActivity;
import vn.edu.usth.online_music_player.R;

@RunWith(AndroidJUnit4.class)
public class LibraryScreenInstrumentedTest {
    @Test
    public void libraryDestinationShowsRetryAfterNetworkFailure() throws InterruptedException {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(appContext, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);

        AtomicReference<NavHostFragment> hostReference = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            NavHostFragment navHost = (NavHostFragment) ((MainActivity) activity)
                    .getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment);
            assertNotNull("The navigation host must be attached", navHost);
            navHost.getNavController().navigate(R.id.navigation_library);
            hostReference.set(navHost);
        });

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        AtomicReference<View> retryReference = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            NavHostFragment navHost = hostReference.get();
            View navHostView = navHost.getView();
            assertNotNull("The navigation host must render a destination", navHostView);
            assertTrue("The navigation host must have visible height", navHostView.getHeight() > 0);
            View libraryTitle = navHostView.findViewById(R.id.library_title);
            assertNotNull("The Library title must be part of the rendered destination", libraryTitle);
            assertTrue("The Library title must be visible while its API request is failing",
                    libraryTitle.isShown());
            retryReference.set(navHostView.findViewById(R.id.library_retry));
        });

        AtomicBoolean retryVisible = new AtomicBoolean(false);
        for (int attempt = 0; attempt < 30 && !retryVisible.get(); attempt++) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                View retryButton = retryReference.get();
                retryVisible.set(retryButton != null && retryButton.isShown());
            });
            if (!retryVisible.get()) {
                Thread.sleep(500);
            }
        }
        assertTrue("A network failure should expose a retry action", retryVisible.get());

        activity.finish();
    }
}
