package com.chatapp.chatapp.websocket;

import com.chatapp.chatapp.model.GroupMember;
import com.chatapp.chatapp.model.GroupMessage;
import com.chatapp.chatapp.model.Message;
import com.chatapp.chatapp.model.User;
import com.chatapp.chatapp.repository.GroupMemberRepository;
import com.chatapp.chatapp.repository.GroupMessageRepository;
import com.chatapp.chatapp.repository.MessageRepository;
import com.chatapp.chatapp.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupMessageRepository groupMessageRepository;
    private final JsonMapper mapper;

    public ChatWebSocketHandler(UserRepository userRepository,
                                MessageRepository messageRepository,
                                GroupMemberRepository groupMemberRepository,
                                GroupMessageRepository groupMessageRepository,
                                JsonMapper mapper) {
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupMessageRepository = groupMessageRepository;
        this.mapper = mapper;
    }

    public Set<String> onlineUsernames() {
        Set<String> result = new HashSet<>();
        sessions.forEach((name, set) -> { if (!set.isEmpty()) result.add(name); });
        return result;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (session.getPrincipal() == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        sessions.computeIfAbsent(session.getPrincipal().getName(),
                k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        if (session.getPrincipal() == null) return;
        Set<WebSocketSession> set = sessions.get(session.getPrincipal().getName());
        if (set != null) set.remove(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage text) throws Exception {
        try {
            Map<?, ?> in = mapper.readValue(text.getPayload(), Map.class);
            Object content = in.get("content");
            if (!(content instanceof String) || ((String) content).isBlank()) return;

            User sender = userRepository.findByUsername(session.getPrincipal().getName()).orElseThrow();
            Object gid = in.get("groupId");
            Object rid = in.get("receiverId");

            if (gid instanceof Number) {
                Long groupId = ((Number) gid).longValue();
                if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, sender.getId())) return;

                GroupMessage gm = new GroupMessage();
                gm.setGroupId(groupId);
                gm.setSenderId(sender.getId());
                gm.setContent(((String) content).trim());
                groupMessageRepository.save(gm);

                Map<String, Object> out = new HashMap<>();
                out.put("type", "group");
                out.put("id", gm.getId());
                out.put("groupId", groupId);
                out.put("senderId", sender.getId());
                out.put("content", gm.getContent());
                out.put("sentAt", gm.getSentAt().toString());
                final String json = mapper.writeValueAsString(out);

                for (GroupMember member : groupMemberRepository.findByGroupId(groupId)) {
                    userRepository.findById(member.getUserId())
                            .ifPresent(u -> sendTo(u.getUsername(), json));
                }
            } else if (rid instanceof Number) {
                User receiver = userRepository.findById(((Number) rid).longValue()).orElse(null);
                if (receiver == null) return;

                Message m = new Message();
                m.setSenderId(sender.getId());
                m.setReceiverId(receiver.getId());
                m.setContent(((String) content).trim());
                messageRepository.save(m);

                Map<String, Object> out = new HashMap<>();
                out.put("type", "private");
                out.put("id", m.getId());
                out.put("senderId", m.getSenderId());
                out.put("receiverId", m.getReceiverId());
                out.put("content", m.getContent());
                out.put("sentAt", m.getSentAt().toString());
                String json = mapper.writeValueAsString(out);

                sendTo(receiver.getUsername(), json);
                if (!receiver.getUsername().equals(sender.getUsername())) {
                    sendTo(sender.getUsername(), json);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendTo(String username, String json) {
        Set<WebSocketSession> set = sessions.get(username);
        if (set == null) return;
        for (WebSocketSession s : set) {
            try {
                synchronized (s) {
                    if (s.isOpen()) s.sendMessage(new TextMessage(json));
                }
            } catch (Exception ignored) { }
        }
    }
}
