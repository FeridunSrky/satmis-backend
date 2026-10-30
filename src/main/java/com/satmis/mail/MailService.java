package com.satmis.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

// Adapter around the SMTP server; callers never see mail library types or failures.
@Slf4j
@Service
@EnableConfigurationProperties(MailProperties.class)
public class MailService {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final MailProperties properties;

    // The sender bean only exists when spring.mail.host is set, which tests without a mail server leave out
    public MailService(ObjectProvider<JavaMailSender> mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    /**
     * Sends a plain-text mail. Returns false instead of throwing on failure: every mail sent today can be
     * requested again by the user, so a mail outage must not fail the request that triggered it.
     */
    public boolean send(String to, String subject, String text) {
        if (!properties.enabled()) {
            log.debug("Mail sending is disabled, skipping '{}'", subject);
            return false;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.error("Mail is enabled but no SMTP server is configured (spring.mail.host)");
            return false;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            sender.send(message);
            return true;
        } catch (MailException ex) {
            // Recipient address is personal data and stays out of the log
            log.error("Sending mail '{}' failed: {}", subject, ex.getMessage());
            return false;
        }
    }
}
