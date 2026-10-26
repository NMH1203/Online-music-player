package vn.edu.usth.online_music_player.data.catalog;

public final class ApiClient {
    // Uses the authenticated client so /home can return personal sections when signed in.
    private static final CatalogApi CATALOG_API =
            vn.edu.usth.online_music_player.data.network.ApiClient.create(CatalogApi.class);

    private ApiClient() {}

    public static CatalogApi catalog() {
        return CATALOG_API;
    }
}
