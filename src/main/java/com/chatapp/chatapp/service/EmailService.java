package com.chatapp.chatapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class EmailService {

    @Value("${app.mail.from:}")
    private String from;

    @Value("${app.brevo.key:}")
    private String apiKey;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public void sendCode(String to, String code) throws Exception {
        if (from == null || from.isBlank() || apiKey == null || apiKey.isBlank()) {
            System.out.println("DEV verification code for " + to + ": " + code);
            return;
        }
        String json = "{\"sender\":{\"name\":\"ChatApp\",\"email\":\"" + esc(from) + "\"},"
                + "\"to\":[{\"email\":\"" + esc(to) + "\"}],"
                + "\"subject\":\"Your ChatApp verification code\","
                + "\"textContent\":\"Your verification code is " + code + ". It expires in 10 minutes.\"}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                .timeout(Duration.ofSeconds(15))
                .header("api-key", apiKey)
                .header("content-type", "application/json")
                .header("accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 300) {
            System.out.println("Brevo error " + res.statusCode() + ": " + res.body());
            throw new RuntimeException("Brevo error " + res.statusCode());
        }
    }
}
