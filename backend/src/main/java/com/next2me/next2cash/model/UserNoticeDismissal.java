package com.next2me.next2cash.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One dismissed in-app notice per user (Oct 2026). Backed by table
 * `user_notice_dismissals` (manual migration; UNIQUE (user_id, notice_key)).
 *
 * Used for one-time instruction banners such as the Polaris OPEX / raises
 * intro: a row means "this user closed this notice", for good.
 */
@Entity
@Table(name = "user_notice_dismissals",
       uniqueConstraints = @UniqueConstraint(name = "ux_user_notice", columnNames = { "user_id", "notice_key" }))
@Data
@NoArgsConstructor
public class UserNoticeDismissal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "notice_key", nullable = false, length = 60)
    private String noticeKey;

    @Column(name = "dismissed_at", nullable = false, updatable = false)
    private LocalDateTime dismissedAt;

    @PrePersist
    protected void onCreate() {
        this.dismissedAt = LocalDateTime.now();
    }
}
