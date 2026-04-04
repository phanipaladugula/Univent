package com.univent.repository;

import com.univent.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailHash(String emailHash);
    Optional<User> findByAnonymousUsername(String anonymousUsername);
    boolean existsByEmailHash(String emailHash);

    boolean existsByAnonymousUsername(String username);
}