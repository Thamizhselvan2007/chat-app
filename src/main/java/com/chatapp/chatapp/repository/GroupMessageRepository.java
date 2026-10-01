package com.chatapp.chatapp.repository;

import com.chatapp.chatapp.model.GroupMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GroupMessageRepository extends JpaRepository<GroupMessage, Long> {
    List<GroupMessage> findByGroupIdOrderBySentAtAsc(Long groupId);
}
