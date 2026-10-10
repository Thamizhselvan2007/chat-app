package com.chatapp.chatapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:}")
    private String from;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendCode(String to, String code) {
        if (from == null || from.isBlank()) {
            System.out.println("DEV verification code for " + to + ": " + code);
            return;
        }
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(from);
        m.setTo(to);
        m.setSubject("Your ChatApp verification code");
        m.setText("Your verification code is " + code + ". It expires in 10 minutes.");
        mailSender.send(m);
    }
}
