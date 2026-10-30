package com.satmis.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// Mails are sent on Boot's application task executor so SMTP latency never reaches the HTTP response
@Configuration
@EnableAsync
public class AsyncConfig {
}
