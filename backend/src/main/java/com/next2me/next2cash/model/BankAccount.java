package com.next2me.next2cash.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bank_accounts")
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "account_label")
    private String accountLabel;

    @Column(name = "account_type")
    private String accountType;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "current_balance", precision = 15, scale = 2)
    private BigDecimal currentBalance;

    @Column(name = "balance_date")
    private LocalDate balanceDate;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // === Phase 2 (Session #49) — Auto-compute fields ===

    /**
     * Anchor balance at opening_date. Auto-computed balance =
     * opening_balance + SUM(income transactions where paymentMethod = accountLabel
     *                       AND docDate >= opening_date AND paymentStatus IN ('paid','received'))
     *                 - SUM(expense transactions where paymentMethod = accountLabel
     *                       AND docDate >= opening_date AND paymentStatus = 'paid')
     */
    @Column(name = "opening_balance", precision = 15, scale = 2)
    private BigDecimal openingBalance;

    /** Date from which transactions are considered for auto-compute. */
    @Column(name = "opening_date")
    private LocalDate openingDate;

    /** Last successful auto-recompute run timestamp. */
    @Column(name = "last_recomputed_at")
    private LocalDateTime lastRecomputedAt;

    /** Manual FX rate for conversion to EUR (1.0 for EUR accounts). */
    @Column(name = "fx_rate_to_eur", precision = 10, scale = 6)
    private BigDecimal fxRateToEur;

    /** TRUE for system-managed virtual accounts like Ανεκχώρητο. */
    @Column(name = "is_virtual")
    private Boolean isVirtual = false;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getEntityId() { return entityId; }
    public void setEntityId(UUID entityId) { this.entityId = entityId; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getAccountLabel() { return accountLabel; }
    public void setAccountLabel(String accountLabel) { this.accountLabel = accountLabel; }

    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getCurrentBalance() { return currentBalance; }
    public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }

    public LocalDate getBalanceDate() { return balanceDate; }
    public void setBalanceDate(LocalDate balanceDate) { this.balanceDate = balanceDate; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public BigDecimal getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; }

    public LocalDate getOpeningDate() { return openingDate; }
    public void setOpeningDate(LocalDate openingDate) { this.openingDate = openingDate; }

    public LocalDateTime getLastRecomputedAt() { return lastRecomputedAt; }
    public void setLastRecomputedAt(LocalDateTime lastRecomputedAt) { this.lastRecomputedAt = lastRecomputedAt; }

    public BigDecimal getFxRateToEur() { return fxRateToEur; }
    public void setFxRateToEur(BigDecimal fxRateToEur) { this.fxRateToEur = fxRateToEur; }

    public Boolean getIsVirtual() { return isVirtual; }
    public void setIsVirtual(Boolean isVirtual) { this.isVirtual = isVirtual; }
}
