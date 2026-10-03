package com.api.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends emails (password reset links, order updates).
 *
 *  - Real sending needs app.mail.enabled=true and an SMTP server (spring.mail.host, port, username, password),
 *    set in the server's environment (see DEPLOY.md). Without them the email is only written to the log, so
 *    everything still works on a developer's computer.
 *  - Sending happens in the background AFTER the database change is saved, so a slow mail server never slows
 *    a click down, and nothing is sent for a change that was rolled back.
 *  - A failed email is logged and never breaks the action that caused it.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    /** Background senders: a mail server that hangs never blocks a request. */
    private static final ExecutorService SENDER = Executors.newVirtualThreadPerTaskExecutor();

    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    @Value("${app.mail.from:DK/Phar <no-reply@dkphar.bt>}")
    private String from;

    /** Write the body of unsent emails to the log (handy on a developer's computer; false on a server). */
    @Value("${app.mail.log-body:true}")
    private boolean logBody;

    public EmailService(ObjectProvider<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
    }

    public void sendEmail(String to, String subject, String body) {
        if (to == null || to.isBlank() || !to.contains("@")) {
            return;
        }
        afterCommit(() -> deliver(to.trim(), subject, body));
    }

    public boolean isEnabled() {
        return enabled && mailSender.getIfAvailable() != null;
    }

    private void deliver(String to, String subject, String body) {
        JavaMailSender sender = enabled ? mailSender.getIfAvailable() : null;
        if (sender == null) {
            log.info("Email not sent (mail is switched off) to {}: {}", to, subject);
            if (logBody) {
                log.info("Email body:\n{}", body);
            }
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
        } catch (Exception e) {
            log.warn("Email to {} failed: {}", to, e.getMessage());
        }
    }

    /** Runs the task after the current transaction commits (or straight away when there is none), in the background. */
    static void afterCommit(Runnable task) {
        Runnable background = () -> SENDER.execute(task);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    background.run();
                }
            });
        } else {
            background.run();
        }
    }
}
