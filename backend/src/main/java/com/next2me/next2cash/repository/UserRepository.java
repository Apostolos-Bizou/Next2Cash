package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    // List all users, ordered by username (for Admin Panel listing)
    List<User> findAllByOrderByUsernameAsc();
}
