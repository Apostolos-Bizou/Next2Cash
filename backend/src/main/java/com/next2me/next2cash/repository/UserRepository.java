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

    // For PUT: check if another user (not this one) has the same username
    boolean existsByUsernameAndIdNot(String username, UUID id);

    // List all users for Admin Panel (ordered by username)
    List<User> findAllByOrderByUsernameAsc();
}
