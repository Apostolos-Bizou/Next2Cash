package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.CompanyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CompanyEntityRepository extends JpaRepository<CompanyEntity, UUID> {
}
