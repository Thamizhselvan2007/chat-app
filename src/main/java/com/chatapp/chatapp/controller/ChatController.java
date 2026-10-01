package com.chatapp.chatapp.controller;

import com.chatapp.chatapp.model.Message;
import com.chatapp.chatapp.model.User;
import com.chatapp.chatapp.repository.MessageRepository;
import com.chatapp.chatapp.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    public ChatController(UserRepository userRepository, MessageRepository messageRepository) {
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
    }

    @GetMapping("/users")
    public ResponseEntity<?> users(Authentication auth) {
        User me = userRepository.findByUsername(auth.getName()).orElseThrow();
        List<?> others = userRepository.findAll().stream()
                .filter(u -> !u.getId().equals(me.getId()))
                .map(u -> Map.of("id", u.getId(), "username", u.getUsername()))
                .toList();
        return ResponseEntity.ok(others);
    }

    @PostMapping("/messages")
    public ResponseEntity<?> send(@RequestBody Map<String, Object> body, Authentication auth) {
        User me = userRepository.findByUsername(auth.getName()).orElseThrow();
        Object rid = body.get("receiverId");
        Object text = body.get("content");
        if (!(rid instanceof Number) || !(text instanceof String) || ((String) text).isBlank()) {
            return ResponseEntity.badRequest().body("receiverId and content are required");
        }
        Long receiverId = ((Number) rid).longValue();
        if (!userRepository.existsById(receiverId)) {
            return ResponseEntity.status(404).body("Receiver not found");
        }

        Message m = new Message();
        m.setSenderId(me.getId());
        m.setReceiverId(receiverId);
        m.setContent(((String) text).trim());
        messageRepository.save(m);
        return ResponseEntity.ok(m);
    }

    @GetMapping("/messages/{userId}")
    public ResponseEntity<?> history(@PathVariable Long userId, Authentication auth) {
        User me = userRepository.findByUsername(auth.getName()).orElseThrow();
        return ResponseEntity.ok(messageRepository.findConversation(me.getId(), userId));
    }
}
