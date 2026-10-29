package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.UserNoticeDismissal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Repository for UserNoticeDismissal (table user_notice_dismissals). */
@Repository
public interface UserNoticeDismissalRepository extends JpaRepository<UserNoticeDismissal, UUID> {

    boolean existsByUserIdAndNoticeKey(UUID userId, String noticeKey);
}
