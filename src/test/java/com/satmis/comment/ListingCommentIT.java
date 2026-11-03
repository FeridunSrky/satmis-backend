package com.satmis.comment;

import com.jayway.jsonpath.JsonPath;
import com.satmis.MailpitInbox;
import com.satmis.TestCommentAttachments;
import com.satmis.TestImages;
import com.satmis.TestJwtSecret;
import com.satmis.TestListings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Mail is on so the owner notification on approval is checked end to end through Mailpit
@SpringBootTest(properties = "app.mail.enabled=true")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class ListingCommentIT {

    private static final String ADMIN_COMMENTS = "/api/admin/comments";
    private static final String MY_COMMENTS = "/api/users/me/comments";
    private static final String PASSWORD = "Sifre1234";
    private static final String BODY = "Araçta ilanda yazmayan boya ve değişen parça var.";
    private static final String REPLY = "Ekspertiz raporu ilanda paylaşıldı, boya kaydı yok.";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    @Container
    static GenericContainer<?> mailpit = MailpitInbox.newContainer();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", TestJwtSecret::random);
        registry.add("spring.mail.host", mailpit::getHost);
        registry.add("spring.mail.port", () -> mailpit.getMappedPort(MailpitInbox.SMTP_PORT));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void create_onLiveListing_waitsForApprovalThenIsPublishedWithMaskedName() throws Exception {
        String admin = adminToken();
        Account seller = verifiedAccount();
        long listingId = liveListing(seller.token());
        Account commenter = verifiedAccount();
        long pdfId = TestCommentAttachments.uploaded(jdbcTemplate, commenter.id(), "PDF");
        long photoId = TestCommentAttachments.uploaded(jdbcTemplate, commenter.id(), "JPEG");

        long commentId = idOf(comment(commenter.token(), listingId, "  " + BODY + "  ", List.of(pdfId, photoId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.body").value(BODY))
                .andExpect(jsonPath("$.listingId").value(listingId))
                .andExpect(jsonPath("$.parentId").value(nullValue()))
                .andExpect(jsonPath("$.attachments[*].id", contains((int) pdfId, (int) photoId)))
                .andExpect(jsonPath("$.attachments[0].type").value("PDF")));
        assertThat(TestCommentAttachments.statusOf(jdbcTemplate, pdfId)).isEqualTo("ATTACHED");

        mockMvc.perform(get("/api/listings/{id}/comments", listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
        perform(get(MY_COMMENTS), commenter.token(), null)
                .andExpect(jsonPath("$.content[0].id").value(commentId))
                .andExpect(jsonPath("$.content[0].status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.content[0].listingTitle").value("Temiz aile aracı"));
        assertThat(queueIds(admin)).contains(commentId);

        approve(admin, commentId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/listings/{id}/comments", listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].authorName").value("Ayşe Y."))
                .andExpect(jsonPath("$.content[0].body").value(BODY))
                .andExpect(jsonPath("$.content[0].attachments", hasSize(2)))
                .andExpect(jsonPath("$.content[0].reply").value(nullValue()))
                .andExpect(jsonPath("$.content[0].canReply").value(false));
        perform(get("/api/listings/{id}/comments", listingId), seller.token(), null)
                .andExpect(jsonPath("$.content[0].canReply").value(true));
        assertThat(queueIds(admin)).doesNotContain(commentId);

        String mail = MailpitInbox.textMailedTo(mailpit, seller.email(), "yeni yorum");
        assertThat(mail).contains("http://localhost:5173/ilan/" + listingId).contains("Temiz aile aracı");
    }

    @Test
    void create_slotsPerListingIncludeRejectedAndDeletedComments() throws Exception {
        String admin = adminToken();
        String seller = verifiedAccount().token();
        long listingId = liveListing(seller);
        long otherListingId = liveListing(seller);
        String commenter = verifiedAccount().token();

        long rejectedId = idOf(comment(commenter, listingId, BODY, List.of()).andExpect(status().isCreated()));
        reject(admin, rejectedId, "Kanıtsız iddia").andExpect(status().isOk());
        long deletedId = idOf(comment(commenter, listingId, BODY, List.of()).andExpect(status().isCreated()));
        perform(delete("/api/comments/{id}", deletedId), commenter, null).andExpect(status().isNoContent());

        comment(commenter, listingId, BODY, List.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMENT_LIMIT_REACHED"));
        perform(get(MY_COMMENTS + "/quota").param("listingId", String.valueOf(listingId)), commenter, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxComments").value(2))
                .andExpect(jsonPath("$.usedComments").value(2))
                .andExpect(jsonPath("$.remainingComments").value(0));
        perform(get(MY_COMMENTS), commenter, null)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.content[0].rejectionReason").value("Kanıtsız iddia"));

        comment(commenter, otherListingId, BODY, List.of()).andExpect(status().isCreated());
    }

    @Test
    void create_invalidRequests_areRejectedWithoutUsingASlot() throws Exception {
        String seller = verifiedAccount().token();
        long liveListingId = liveListing(seller);
        long pendingListingId = createListing(seller);
        Account commenter = verifiedAccount();
        long othersAttachment = TestCommentAttachments.uploaded(jdbcTemplate, verifiedAccount().id(), "PDF");
        List<Long> sixAttachments = java.util.stream.IntStream.range(0, 6)
                .mapToObj(index -> TestCommentAttachments.uploaded(jdbcTemplate, commenter.id(), "PNG")).toList();

        comment(seller, liveListingId, BODY, List.of()).andExpect(status().isUnprocessableContent());
        comment(commenter.token(), pendingListingId, BODY, List.of()).andExpect(status().isNotFound());
        comment(null, liveListingId, BODY, List.of()).andExpect(status().isUnauthorized());
        comment(unverifiedToken(), liveListingId, BODY, List.of())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        comment(commenter.token(), liveListingId, "      kısa yorum         ", List.of())
                .andExpect(status().isBadRequest());
        comment(commenter.token(), liveListingId, BODY, sixAttachments).andExpect(status().isBadRequest());
        comment(commenter.token(), liveListingId, BODY, List.of(othersAttachment))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_NOT_AVAILABLE"));

        comment(commenter.token(), liveListingId, BODY, List.of(sixAttachments.getFirst()))
                .andExpect(status().isCreated());
        // The attachment is used now, so it cannot go into a second comment
        comment(commenter.token(), liveListingId, BODY, List.of(sixAttachments.getFirst()))
                .andExpect(status().isUnprocessableContent());
        comment(commenter.token(), liveListingId, BODY, List.of()).andExpect(status().isCreated());
    }

    @Test
    void reply_onlyOwnerOncePerPublishedComment_andARejectedReplyLeavesOneMoreAttempt() throws Exception {
        String admin = adminToken();
        Account seller = verifiedAccount();
        long listingId = liveListing(seller.token());
        String commenter = verifiedAccount().token();
        long commentId = idOf(comment(commenter, listingId, BODY, List.of()).andExpect(status().isCreated()));

        reply(seller.token(), commentId, List.of()).andExpect(status().isNotFound());
        approve(admin, commentId).andExpect(status().isOk());
        reply(commenter, commentId, List.of()).andExpect(status().isForbidden());

        long firstReply = idOf(reply(seller.token(), commentId, List.of())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(commentId))
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL")));
        reply(seller.token(), commentId, List.of()).andExpect(status().isConflict());
        perform(get("/api/listings/{id}/comments", listingId), seller.token(), null)
                .andExpect(jsonPath("$.content[0].reply").value(nullValue()))
                .andExpect(jsonPath("$.content[0].canReply").value(false));
        reject(admin, firstReply, "Hakaret içeriyor").andExpect(status().isOk());

        long photoId = TestCommentAttachments.uploaded(jdbcTemplate, seller.id(), "WEBP");
        long secondReply = idOf(reply(seller.token(), commentId, List.of(photoId)).andExpect(status().isCreated()));
        approve(admin, secondReply).andExpect(status().isOk());
        mockMvc.perform(get("/api/listings/{id}/comments", listingId))
                .andExpect(jsonPath("$.content[0].reply.id").value(secondReply))
                .andExpect(jsonPath("$.content[0].reply.body").value(REPLY))
                .andExpect(jsonPath("$.content[0].reply.attachments[0].type").value("WEBP"));

        perform(delete("/api/comments/{id}", secondReply), seller.token(), null).andExpect(status().isNoContent());
        reply(seller.token(), commentId, List.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPLY_LIMIT_REACHED"));
        assertThat(TestCommentAttachments.statusOf(jdbcTemplate, photoId)).isEqualTo("DELETED");
    }

    @Test
    void approve_replyUnderATakenDownComment_isRefused() throws Exception {
        String admin = adminToken();
        String seller = verifiedAccount().token();
        long listingId = liveListing(seller);
        long commentId = idOf(comment(verifiedAccount().token(), listingId, BODY, List.of())
                .andExpect(status().isCreated()));
        approve(admin, commentId).andExpect(status().isOk());
        long replyId = idOf(reply(seller, commentId, List.of()).andExpect(status().isCreated()));

        reject(admin, commentId, "Yanıltıcı").andExpect(status().isOk());

        approve(admin, replyId).andExpect(status().isUnprocessableContent());
        approve(admin, commentId).andExpect(status().isUnprocessableContent());
        mockMvc.perform(get("/api/listings/{id}/comments", listingId)).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void moderation_staleVersionOrMissingReason_isRejected() throws Exception {
        String admin = adminToken();
        long listingId = liveListing(verifiedAccount().token());
        long commentId = idOf(comment(verifiedAccount().token(), listingId, BODY, List.of())
                .andExpect(status().isCreated()));

        perform(post(ADMIN_COMMENTS + "/{id}/approve", commentId), admin, Map.of("version", versionOf(commentId) + 1))
                .andExpect(status().isConflict());
        perform(post(ADMIN_COMMENTS + "/{id}/reject", commentId), admin, Map.of("version", versionOf(commentId)))
                .andExpect(status().isBadRequest());
        perform(post(ADMIN_COMMENTS + "/{id}/approve", 99_999_999), admin, Map.of("version", 0))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_commentRemovesItsReplyAndReleasesAttachments() throws Exception {
        String admin = adminToken();
        Account seller = verifiedAccount();
        long listingId = liveListing(seller.token());
        Account commenter = verifiedAccount();
        long commentFile = TestCommentAttachments.uploaded(jdbcTemplate, commenter.id(), "PDF");
        long replyFile = TestCommentAttachments.uploaded(jdbcTemplate, seller.id(), "PDF");
        long commentId = idOf(comment(commenter.token(), listingId, BODY, List.of(commentFile))
                .andExpect(status().isCreated()));
        approve(admin, commentId).andExpect(status().isOk());
        long replyId = idOf(reply(seller.token(), commentId, List.of(replyFile)).andExpect(status().isCreated()));

        perform(delete("/api/comments/{id}", commentId), seller.token(), null).andExpect(status().isNotFound());
        perform(delete("/api/comments/{id}", commentId), commenter.token(), null).andExpect(status().isNoContent());

        assertThat(commentExists(commentId)).isFalse();
        assertThat(commentExists(replyId)).isFalse();
        assertThat(TestCommentAttachments.statusOf(jdbcTemplate, commentFile)).isEqualTo("DELETED");
        assertThat(TestCommentAttachments.statusOf(jdbcTemplate, replyFile)).isEqualTo("DELETED");
    }

    @Test
    void deleteListing_removesCommentsAndReleasesTheirAttachments() throws Exception {
        String admin = adminToken();
        String seller = verifiedAccount().token();
        long listingId = liveListing(seller);
        Account commenter = verifiedAccount();
        long fileId = TestCommentAttachments.uploaded(jdbcTemplate, commenter.id(), "JPEG");
        long commentId = idOf(comment(commenter.token(), listingId, BODY, List.of(fileId))
                .andExpect(status().isCreated()));
        approve(admin, commentId).andExpect(status().isOk());

        perform(delete("/api/listings/{id}", listingId), seller, null).andExpect(status().isNoContent());

        assertThat(commentExists(commentId)).isFalse();
        assertThat(TestCommentAttachments.statusOf(jdbcTemplate, fileId)).isEqualTo("DELETED");
    }

    @Test
    void soldListing_keepsCommentsVisible_acceptsRepliesButNoNewComments() throws Exception {
        String admin = adminToken();
        String seller = verifiedAccount().token();
        long listingId = liveListing(seller);
        long commentId = idOf(comment(verifiedAccount().token(), listingId, BODY, List.of())
                .andExpect(status().isCreated()));
        approve(admin, commentId).andExpect(status().isOk());

        perform(post("/api/listings/{id}/sale", listingId), seller,
                Map.of("salePrice", 700_000, "version", listingVersionOf(listingId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/listings/{id}/comments", listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
        comment(verifiedAccount().token(), listingId, BODY, List.of()).andExpect(status().isNotFound());
        reply(seller, commentId, List.of()).andExpect(status().isCreated());
    }

    @Test
    void getPublished_hidesCommentsOfBlockedAuthorsAndOfHiddenListings() throws Exception {
        String admin = adminToken();
        String seller = verifiedAccount().token();
        long listingId = liveListing(seller);
        Account commenter = verifiedAccount();
        long commentId = idOf(comment(commenter.token(), listingId, BODY, List.of()).andExpect(status().isCreated()));
        approve(admin, commentId).andExpect(status().isOk());

        jdbcTemplate.update("update users set enabled = false where id = ?", commenter.id());
        mockMvc.perform(get("/api/listings/{id}/comments", listingId)).andExpect(jsonPath("$.content", hasSize(0)));
        jdbcTemplate.update("update users set enabled = true where id = ?", commenter.id());
        mockMvc.perform(get("/api/listings/{id}/comments", listingId)).andExpect(jsonPath("$.content", hasSize(1)));

        jdbcTemplate.update("update listings set status = 'PASSIVE' where id = ?", listingId);
        mockMvc.perform(get("/api/listings/{id}/comments", listingId)).andExpect(status().isNotFound());
    }

    @Test
    void moderationCounts_reportsPendingListingsCommentsAndReportedListings() throws Exception {
        String admin = adminToken();
        Map<String, Number> before = counts(admin);
        String seller = verifiedAccount().token();
        createListing(seller);
        long listingId = liveListing(seller);
        comment(verifiedAccount().token(), listingId, BODY, List.of()).andExpect(status().isCreated());
        perform(post("/api/listings/{id}/reports", listingId), verifiedAccount().token(),
                Map.of("reason", "FRAUD_SUSPECTED")).andExpect(status().isCreated());

        Map<String, Number> after = counts(admin);

        assertThat(after.get("pendingListings").longValue()).isEqualTo(before.get("pendingListings").longValue() + 1);
        assertThat(after.get("pendingComments").longValue()).isEqualTo(before.get("pendingComments").longValue() + 1);
        assertThat(after.get("reportedListings").longValue()).isEqualTo(before.get("reportedListings").longValue() + 1);
    }

    @Test
    void adminEndpoints_forNonAdmins_return401Or403() throws Exception {
        String user = verifiedAccount().token();

        mockMvc.perform(get(ADMIN_COMMENTS)).andExpect(status().isUnauthorized());
        perform(get(ADMIN_COMMENTS), user, null).andExpect(status().isForbidden());
        perform(post(ADMIN_COMMENTS + "/{id}/approve", 1), user, Map.of("version", 0)).andExpect(status().isForbidden());
        perform(get("/api/admin/moderation/counts"), user, null).andExpect(status().isForbidden());
        mockMvc.perform(get(MY_COMMENTS)).andExpect(status().isUnauthorized());
    }

    private ResultActions comment(String token, long listingId, String body, List<Long> attachmentIds)
            throws Exception {
        return perform(post("/api/listings/{id}/comments", listingId), token,
                Map.of("body", body, "attachmentIds", attachmentIds));
    }

    private ResultActions reply(String token, long commentId, List<Long> attachmentIds) throws Exception {
        return perform(post("/api/comments/{id}/reply", commentId), token,
                Map.of("body", REPLY, "attachmentIds", attachmentIds));
    }

    private ResultActions approve(String admin, long commentId) throws Exception {
        return perform(post(ADMIN_COMMENTS + "/{id}/approve", commentId), admin,
                Map.of("version", versionOf(commentId)));
    }

    private ResultActions reject(String admin, long commentId, String reason) throws Exception {
        return perform(post(ADMIN_COMMENTS + "/{id}/reject", commentId), admin,
                Map.of("version", versionOf(commentId), "reason", reason));
    }

    private List<Long> queueIds(String admin) throws Exception {
        String response = perform(get(ADMIN_COMMENTS).param("size", "50"), admin, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<List<Number>>read(response, "$.content[*].id").stream().map(Number::longValue).toList();
    }

    private Map<String, Number> counts(String admin) throws Exception {
        String response = perform(get("/api/admin/moderation/counts"), admin, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$");
    }

    private long versionOf(long commentId) {
        return jdbcTemplate.queryForObject("select version from listing_comments where id = ?", Long.class, commentId);
    }

    private long listingVersionOf(long listingId) {
        return jdbcTemplate.queryForObject("select version from listings where id = ?", Long.class, listingId);
    }

    private boolean commentExists(long commentId) {
        return jdbcTemplate.queryForObject("select count(*) from listing_comments where id = ?", Integer.class,
                commentId) > 0;
    }

    private long liveListing(String ownerToken) throws Exception {
        long listingId = createListing(ownerToken);
        TestListings.approve(jdbcTemplate, listingId);
        return listingId;
    }

    private long createListing(String ownerToken) throws Exception {
        Map<String, Object> vehicle = new HashMap<>();
        vehicle.put("seriesId", jdbcTemplate.queryForObject("select min(id) from vehicle_series", Long.class));
        vehicle.put("modelYear", 2020);
        vehicle.put("kilometer", 45_000);
        vehicle.put("fuelType", "GASOLINE");
        vehicle.put("gearType", "AUTOMATIC");
        vehicle.put("vehicleCondition", "USED");
        vehicle.put("fromWho", "OWNER");
        vehicle.put("hasDamageRecord", false);
        vehicle.put("exchangeable", true);
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Temiz aile aracı");
        body.put("description", "Bakımları yetkili serviste yapıldı.");
        body.put("price", 750_000);
        body.put("currency", "TRY");
        body.put("districtId", jdbcTemplate.queryForObject("select min(id) from districts", Long.class));
        body.put("vehicle", vehicle);
        body.put("imageIds", TestImages.uploaded(jdbcTemplate, userIdOf(ownerToken), 1));
        String response = perform(post("/api/listings"), ownerToken, body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(response, "$.id").longValue();
    }

    // Authorities are reloaded from the database on every request, so the token works as admin right away
    private String adminToken() throws Exception {
        Account account = verifiedAccount();
        jdbcTemplate.update("update users set role = 'ADMIN' where id = ?", account.id());
        return account.token();
    }

    // Commenting and listing need a verified e-mail, so it is set directly in the database
    private Account verifiedAccount() throws Exception {
        String email = uniqueEmail();
        String token = register(email);
        jdbcTemplate.update("update users set email_verified = true where email = ?", email);
        return new Account(token, userIdOf(token), email);
    }

    private String unverifiedToken() throws Exception {
        return register(uniqueEmail());
    }

    private String register(String email) throws Exception {
        Map<String, String> body = Map.of("email", email, "password", PASSWORD,
                "firstName", "Ayşe", "lastName", "Yılmaz", "phone", "+905321234567");
        String response = perform(post("/api/auth/register"), null, body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    private long userIdOf(String token) throws Exception {
        String response = perform(get("/api/users/me"), token, null).andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(response, "$.id").longValue();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    private static long idOf(ResultActions result) throws Exception {
        return JsonPath.<Number>read(result.andReturn().getResponse().getContentAsString(), "$.id").longValue();
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@satmis.com";
    }

    private record Account(String token, long id, String email) {
    }
}
