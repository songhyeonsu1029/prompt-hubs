package com.mediaproject.prompthubs.domain.account.service;

import com.mediaproject.prompthubs.domain.account.dto.*;
import com.mediaproject.prompthubs.domain.account.entity.Account;
import com.mediaproject.prompthubs.domain.account.repository.AccountRepository;
import com.mediaproject.prompthubs.global.config.JwtConfig;
import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import com.mediaproject.prompthubs.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtConfig jwtConfig;

    @Transactional
    public TokenResponse signup(SignupRequest request) {
        // Check if email already exists
        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        // Create new account
        Account account = Account.builder()
                .email(request.getEmail())
                .name(request.getName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .provider(Account.Provider.LOCAL)
                .build();

        Account savedAccount = accountRepository.save(account);
        log.info("New account created: {}", savedAccount.getEmail());

        // Generate tokens
        return generateTokens(savedAccount);
    }

    public TokenResponse login(LoginRequest request) {
        // Find account by email
        Account account = accountRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // Verify password (only for LOCAL provider)
        if (account.getProvider() != Account.Provider.LOCAL) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
                    "Please use " + account.getProvider().name() + " login");
        }

        if (!passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        log.info("User logged in: {}", account.getEmail());

        // Generate tokens
        return generateTokens(account);
    }

    public TokenResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        // Get account ID from refresh token
        UUID accountId = jwtTokenProvider.getAccountIdFromToken(refreshToken);

        // Validate refresh token (checks Redis storage)
        if (!jwtTokenProvider.validateRefreshToken(refreshToken, accountId)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "Invalid or expired refresh token");
        }

        // Find account
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        // Delete old refresh token and generate new tokens
        jwtTokenProvider.deleteRefreshToken(accountId);

        log.info("Token refreshed for user: {}", account.getEmail());

        return generateTokens(account);
    }

    public AccountResponse getCurrentUser(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse updateProfile(UUID accountId, UpdateProfileRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        account.updateProfile(request.getName(), request.getAvatarUrl());

        log.info("Profile updated for user: {}", account.getEmail());

        return AccountResponse.from(account);
    }

    @Transactional
    public void logout(UUID accountId) {
        jwtTokenProvider.deleteRefreshToken(accountId);
        log.info("User logged out: {}", accountId);
    }

    private TokenResponse generateTokens(Account account) {
        String accessToken = jwtTokenProvider.createAccessToken(account.getId(), account.getEmail());
        String refreshToken = jwtTokenProvider.createRefreshToken(account.getId());

        return TokenResponse.of(
                accessToken,
                refreshToken,
                jwtConfig.getAccessExpiration() / 1000  // Convert to seconds
        );
    }
}