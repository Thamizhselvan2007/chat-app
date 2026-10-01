package com.chatapp.chatapp.controller;

import com.chatapp.chatapp.model.User;
import com.chatapp.chatapp.repository.UserRepository;
import com.chatapp.chatapp.websocket.ChatWebSocketHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class OnlineController {

    private final ChatWebSocketHandler handler;
    private final UserRepository userRepository;

    public OnlineController(ChatWebSocketHandler handler, UserRepository userRepository) {
        this.handler = handler;
        this.userRepository = userRepository;
    }

    @GetMapping("/online")
    public ResponseEntity<?> online() {
        Set<String> names = handler.onlineUsernames();
        List<Long> ids = userRepository.findAll().stream()
                .filter(u -> names.contains(u.getUsername()))
                .map(User::getId)
                .toList();
        return ResponseEntity.ok(ids);
    }
}
