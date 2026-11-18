package com.satmis.favorite;

import com.jayway.jsonpath.JsonPath;
import com.satmis.TestApi;
import com.satmis.TestJwtSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestApi.class)
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class FavoriteIT {

    private static final String FAVORITE = "/api/listings/{id}/favorite";
    private static final String MY_FAVORITES = "/api/users/me/favorites";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", TestJwtSecret::random);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestApi api;

    @Test
    void add_twice_isIdempotentAndShownInDetail() throws Exception {
        long listingId = api.liveListing(api.verifiedToken());
        String fan = api.verifiedToken();

        api.perform(put(FAVORITE, listingId), fan, null).andExpect(status().isNoContent());
        api.perform(put(FAVORITE, listingId), fan, null).andExpect(status().isNoContent());

        api.perform(get("/api/listings/{id}", listingId), fan, null)
                .andExpect(jsonPath("$.favoriteCount").value(1))
                .andExpect(jsonPath("$.favoritedByMe").value(true));
        mockMvc.perform(get("/api/listings/{id}", listingId))
                .andExpect(jsonPath("$.favoriteCount").value(1))
                .andExpect(jsonPath("$.favoritedByMe").value(false));
    }

    @Test
    void add_hiddenOrMissingListing_returns404() throws Exception {
        String owner = api.verifiedToken();
        long listingId = api.liveListing(owner);
        api.perform(patch("/api/listings/{id}/status", listingId), owner, Map.of("status", "PASSIVE"));
        String fan = api.verifiedToken();

        api.perform(put(FAVORITE, listingId), fan, null).andExpect(status().isNotFound());
        api.perform(put(FAVORITE, Long.MAX_VALUE), fan, null).andExpect(status().isNotFound());
    }

    @Test
    void add_withoutToken_returns401() throws Exception {
        long listingId = api.liveListing(api.verifiedToken());

        mockMvc.perform(put(FAVORITE, listingId)).andExpect(status().isUnauthorized());
    }

    @Test
    void remove_twice_isIdempotent() throws Exception {
        long listingId = api.liveListing(api.verifiedToken());
        String fan = api.verifiedToken();
        api.perform(put(FAVORITE, listingId), fan, null);

        api.perform(delete(FAVORITE, listingId), fan, null).andExpect(status().isNoContent());
        api.perform(delete(FAVORITE, listingId), fan, null).andExpect(status().isNoContent());

        api.perform(get("/api/listings/{id}", listingId), fan, null)
                .andExpect(jsonPath("$.favoriteCount").value(0))
                .andExpect(jsonPath("$.favoritedByMe").value(false));
    }

    @Test
    void getMyFavorites_newestFirstAndOnlyActiveListings() throws Exception {
        String owner = api.verifiedToken();
        long first = api.liveListing(owner);
        long second = api.liveListing(owner);
        long laterHidden = api.liveListing(owner);
        String fan = api.verifiedToken();
        for (long listingId : List.of(first, second, laterHidden)) {
            api.perform(put(FAVORITE, listingId), fan, null).andExpect(status().isNoContent());
        }
        api.perform(patch("/api/listings/{id}/status", laterHidden), owner, Map.of("status", "PASSIVE"));

        assertThat(favoriteIds(fan, "")).containsExactly(second, first);
        api.perform(get(MY_FAVORITES + "?size=1&page=1"), fan, null)
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(first));
    }

    @Test
    void getMyFavorites_otherUsersFavorites_notIncluded() throws Exception {
        long listingId = api.liveListing(api.verifiedToken());
        api.perform(put(FAVORITE, listingId), api.verifiedToken(), null);

        assertThat(favoriteIds(api.verifiedToken(), "")).isEmpty();
    }

    private List<Long> favoriteIds(String token, String query) throws Exception {
        String response = api.perform(get(MY_FAVORITES + query), token, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<List<Number>>read(response, "$.content[*].id").stream().map(Number::longValue).toList();
    }
}
