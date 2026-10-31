package com.satmis.user;

import com.satmis.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // Spring Data escapes LIKE wildcards, so "%" or "_" in the input match literally
    Page<User> findByEmailContaining(String emailPart, Pageable pageable);

    long countByEmailIn(Collection<String> emails);

    @Modifying
    @Query("update User u set u.role = :role where u.email in :emails and u.role <> :role")
    int updateRoleByEmailIn(@Param("emails") Collection<String> emails, @Param("role") Role role);
}
