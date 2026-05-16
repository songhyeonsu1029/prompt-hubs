package com.mediaproject.prompthubs.domain.account.repository;

import com.mediaproject.prompthubs.domain.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<Account> findByProviderAndProviderId(Account.Provider provider, String providerId);
}