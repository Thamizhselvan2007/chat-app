package com.chatapp.chatapp.controller;

import com.chatapp.chatapp.model.User;
import com.chatapp.chatapp.repository.UserRepository;
import com.chatapp.chatapp.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    private record Pending(String username, String passwordHash, String code, long expires) {}

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String email = body.get("email");
        String password = body.get("password");

        if (username == null || username.isBlank() || email == null || email.isBlank()
                || password == null || password.length() < 6) {
            return ResponseEntity.badRequest().body("Username, email and a password of 6+ characters are required");
        }
        email = email.trim().toLowerCase();
        username = username.trim();
        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.status(409).body("Username already taken");
        }
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.status(409).body("Email already registered");
        }

        String code = String.format("%06d", new java.security.SecureRandom().nextInt(1000000));
        pending.put(email, new Pending(username, passwordEncoder.encode(password), code,
                System.currentTimeMillis() + 10 * 60 * 1000));
        try {
            emailService.sendCode(email, code);
        } catch (Exception e) {
            pending.remove(email);
            return ResponseEntity.status(500).body("Could not send the email. Check the address and try again.");
        }
        return ResponseEntity.ok("Verification code sent to " + email);
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody Map<String, String> body) {
        String email = body.get("email") == null ? "" : body.get("email").trim().toLowerCase();
        String code = body.get("code") == null ? "" : body.get("code").trim();
        Pending p = pending.get(email);
        if (p == null || System.currentTimeMillis() > p.expires()) {
            pending.remove(email);
            return ResponseEntity.badRequest().body("Code expired. Please register again.");
        }
        if (!p.code().equals(code)) {
            return ResponseEntity.badRequest().body("Wrong code");
        }
        if (userRepository.existsByUsername(p.username()) || userRepository.existsByEmail(email)) {
            pending.remove(email);
            return ResponseEntity.status(409).body("Username or email already taken");
        }
        User user = new User();
        user.setUsername(p.username());
        user.setEmail(email);
        user.setPasswordHash(p.passwordHash());
        userRepository.save(user);
        pending.remove(email);
        return ResponseEntity.ok(Map.of("id", user.getId(), "username", user.getUsername()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body,
                                   HttpServletRequest request, HttpServletResponse response) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) {
            return ResponseEntity.badRequest().body("Username and password are required");
        }
        username = username.trim();

        Optional<User> found = userRepository.findByUsername(username);
        if (found.isEmpty()) found = userRepository.findByEmail(username.toLowerCase());
        if (found.isEmpty() || !passwordEncoder.matches(password, found.get().getPasswordHash())) {
            return ResponseEntity.status(401).body("Invalid username or password");
        }

        Authentication auth = new UsernamePasswordAuthenticationToken(found.get().getUsername(), null, List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);

        return ResponseEntity.ok(Map.of("id", found.get().getId(), "username", found.get().getUsername()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok("Logged out");
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body("Not logged in");
        }
        return ResponseEntity.ok(Map.of("username", auth.getName()));
    }
}
