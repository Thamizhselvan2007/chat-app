package com.chatapp.chatapp.repository;

import com.chatapp.chatapp.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("select m from Message m where (m.senderId = :a and m.receiverId = :b) or (m.senderId = :b and m.receiverId = :a) order by m.sentAt asc")
    List<Message> findConversation(@Param("a") Long a, @Param("b") Long b);
}
