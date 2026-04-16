package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    List<BankAccount> findByEntityIdAndIsActiveTrueOrderBySortOrderAsc(UUID entityId);

    List<BankAccount> findByIsActiveTrueOrderByEntityIdAscSortOrderAsc();
}
