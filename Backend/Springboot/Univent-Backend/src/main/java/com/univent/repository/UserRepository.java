package com.univent.repository;



import com.univent.model.entity.User;
import com.univent.model.enums.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailHash(String emailHash);
    Optional<User> findByAnonymousUsername(String anonymousUsername);
    boolean existsByEmailHash(String emailHash);
    Page<User> findByVerificationStatus(VerificationStatus status, Pageable pageable);
    boolean existsByAnonymousUsername(String username);
}