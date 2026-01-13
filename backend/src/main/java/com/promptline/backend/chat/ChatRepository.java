package com.promptline.backend.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ChatRepository extends JpaRepository<ChatEntity, UUID> {
}
    