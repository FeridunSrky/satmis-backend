package com.satmis.auth;

import com.satmis.mail.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

// Async as well as after commit: a known e-mail must not answer slower than an unknown one,
// or response times would reveal which addresses are registered.
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetMailListener {

    private static final String SUBJECT = "satmis.com şifre sıfırlama";
    private static final String BODY = """
            Merhaba %s,

            satmis.com hesabınız için şifre sıfırlama isteği aldık. Yeni şifrenizi aşağıdaki linkten belirleyin:

            %s

            Link %d dakika geçerlidir ve bir kez kullanılabilir. Bu isteği siz yapmadıysanız bu e-postayı
            dikkate almayın; şifreniz değişmez.
            """;

    private final MailService mailService;
    private final PasswordResetProperties properties;

    @Async
    @TransactionalEventListener
    public void onResetRequested(PasswordResetRequestedEvent event) {
        String link = properties.linkBaseUrl() + "?token=" + event.rawToken();
        String body = BODY.formatted(event.firstName(), link, properties.tokenTtl().toMinutes());
        if (mailService.send(event.email(), SUBJECT, body)) {
            log.info("Password reset e-mail sent to user {}", event.userId());
        }
    }
}
