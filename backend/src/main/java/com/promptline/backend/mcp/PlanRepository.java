package com.promptline.backend.mcp;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface PlanRepository extends JpaRepository<PlanEntity, UUID> {

    @Modifying
    @Transactional
    @Query("""
        update PlanEntity p
        set p.status = com.promptline.backend.mcp.PlanStatus.SUPERSEDED
        where p.chat.id = :chatId
          and p.status = com.promptline.backend.mcp.PlanStatus.PROPOSED
    """)
    int supersedeAllProposed(@Param("chatId") UUID chatId);
}
