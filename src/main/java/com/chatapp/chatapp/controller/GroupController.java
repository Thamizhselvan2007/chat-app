package com.chatapp.chatapp.controller;

import com.chatapp.chatapp.model.*;
import com.chatapp.chatapp.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupMessageRepository messageRepository;

    public GroupController(UserRepository userRepository, GroupRepository groupRepository,
                           GroupMemberRepository memberRepository,
                           GroupMessageRepository messageRepository) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
    }

    private User me(Authentication auth) {
        return userRepository.findByUsername(auth.getName()).orElseThrow();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> body, Authentication auth) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body("Group name is required");
        }
        User me = me(auth);
        ChatGroup g = new ChatGroup();
        g.setGroupName(name.trim());
        g.setCreatedBy(me.getId());
        groupRepository.save(g);

        GroupMember gm = new GroupMember();
        gm.setGroupId(g.getId());
        gm.setUserId(me.getId());
        memberRepository.save(gm);

        return ResponseEntity.ok(Map.of("id", g.getId(), "name", g.getGroupName()));
    }

    @GetMapping
    public ResponseEntity<?> myGroups(Authentication auth) {
        User me = me(auth);
        List<?> groups = memberRepository.findByUserId(me.getId()).stream()
                .map(m -> groupRepository.findById(m.getGroupId()).orElse(null))
                .filter(g -> g != null)
                .map(g -> Map.of("id", g.getId(), "name", g.getGroupName()))
                .toList();
        return ResponseEntity.ok(groups);
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<?> addMember(@PathVariable Long id,
                                       @RequestBody Map<String, Object> body,
                                       Authentication auth) {
        User me = me(auth);
        if (!memberRepository.existsByGroupIdAndUserId(id, me.getId())) {
            return ResponseEntity.status(403).body("You are not a member of this group");
        }
        Object uid = body.get("userId");
        if (!(uid instanceof Number)) {
            return ResponseEntity.badRequest().body("userId is required");
        }
        Long userId = ((Number) uid).longValue();
        if (!userRepository.existsById(userId)) {
            return ResponseEntity.status(404).body("User not found");
        }
        if (memberRepository.existsByGroupIdAndUserId(id, userId)) {
            return ResponseEntity.status(409).body("Already a member");
        }
        GroupMember gm = new GroupMember();
        gm.setGroupId(id);
        gm.setUserId(userId);
        memberRepository.save(gm);
        return ResponseEntity.ok("Member added");
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<?> send(@PathVariable Long id,
                                  @RequestBody Map<String, String> body,
                                  Authentication auth) {
        User me = me(auth);
        if (!memberRepository.existsByGroupIdAndUserId(id, me.getId())) {
            return ResponseEntity.status(403).body("You are not a member of this group");
        }
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body("content is required");
        }
        GroupMessage m = new GroupMessage();
        m.setGroupId(id);
        m.setSenderId(me.getId());
        m.setContent(content.trim());
        messageRepository.save(m);
        return ResponseEntity.ok(m);
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<?> history(@PathVariable Long id, Authentication auth) {
        User me = me(auth);
        if (!memberRepository.existsByGroupIdAndUserId(id, me.getId())) {
            return ResponseEntity.status(403).body("You are not a member of this group");
        }
        return ResponseEntity.ok(messageRepository.findByGroupIdOrderBySentAtAsc(id));
    }
}
