package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.AiQueryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiQueryHistoryRepository extends JpaRepository<AiQueryHistory, Long> {

    /** Returns all queries made by a user, most recent first. */
    List<AiQueryHistory> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Total lifetime count for a user (for rate limiting / stats). */
    long countByUserId(UUID userId);
}
