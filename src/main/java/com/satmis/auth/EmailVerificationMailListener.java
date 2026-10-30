package com.satmis.auth;

import com.satmis.mail.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

// After commit: no mail for a registration that rolled back. Async: a slow mail server never delays the response.
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationMailListener {

    private static final String SUBJECT = "satmis.com e-posta adresinizi doğrulayın";
    private static final String BODY = """
            Merhaba %s,

            satmis.com hesabınızı tamamlamak için e-posta adresinizi aşağıdaki linkten doğrulayın:

            %s

            Link %d saat geçerlidir. Bu hesabı siz oluşturmadıysanız bu e-postayı dikkate almayın.
            """;

    private final MailService mailService;
    private final EmailVerificationProperties properties;

    @Async
    @TransactionalEventListener
    public void onVerificationRequested(EmailVerificationRequestedEvent event) {
        String link = properties.linkBaseUrl() + "?token=" + event.rawToken();
        String body = BODY.formatted(event.firstName(), link, properties.tokenTtl().toHours());
        if (mailService.send(event.email(), SUBJECT, body)) {
            log.info("Verification e-mail sent to user {}", event.userId());
        }
    }
}
