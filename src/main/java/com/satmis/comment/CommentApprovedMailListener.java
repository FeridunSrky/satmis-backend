package com.satmis.comment;

import com.satmis.mail.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

// After commit: no mail for an approval that rolled back. Async: a slow mail server never delays the admin.
// A lost mail is acceptable: the comment is on the listing page either way.
@Slf4j
@Component
@RequiredArgsConstructor
public class CommentApprovedMailListener {

    private static final String SUBJECT = "satmis.com ilanınıza yeni yorum yapıldı";
    private static final String BODY = """
            Merhaba %s,

            "%s" başlıklı ilanınıza yapılan bir yorum yayınlandı. Yorumu okumak ve cevap vermek için:

            %s

            Her yoruma bir cevap hakkınız vardır; cevabınız da yayınlanmadan önce incelenir.
            """;

    private final MailService mailService;
    private final ListingCommentProperties properties;

    @Async
    @TransactionalEventListener
    public void onCommentApproved(CommentApprovedEvent event) {
        String link = properties.listingLinkBaseUrl() + "/" + event.listingId();
        String body = BODY.formatted(event.ownerFirstName(), event.listingTitle(), link);
        if (mailService.send(event.ownerEmail(), SUBJECT, body)) {
            log.info("New comment e-mail sent to the owner of listing {}", event.listingId());
        }
    }
}
