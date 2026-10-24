package vn.edu.usth.online_music_player.data.library;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LibraryRepositoryErrorTest {
    @Test
    public void keepsAuthenticationErrorsDistinctFromServerErrors() {
        assertEquals(LibraryRepository.FailureType.AUTHENTICATION_REQUIRED,
                LibraryRepository.classify(401));
        assertEquals(LibraryRepository.FailureType.SERVER,
                LibraryRepository.classify(500));
    }
}
