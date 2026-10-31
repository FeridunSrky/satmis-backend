package com.satmis.user;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

// Comes from the environment (APP_ADMIN_EMAILS), so no admin address is ever committed
@ConfigurationProperties("app.admin")
public record AdminProperties(@DefaultValue List<String> emails) {
}
