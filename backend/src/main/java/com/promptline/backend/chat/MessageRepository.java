package com.promptline.backend.chat;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {

  @Query("""
    select m
    from MessageEntity m
    join fetch m.chat c
    where c.id = :chatId
    order by m.createdAt asc
  """)
  List<MessageEntity> findMessagesForChat(@Param("chatId") UUID chatId);
}


