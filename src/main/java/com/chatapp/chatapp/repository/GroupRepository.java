package com.chatapp.chatapp.repository;

import com.chatapp.chatapp.model.ChatGroup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<ChatGroup, Long> {
}
