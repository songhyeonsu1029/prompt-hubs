package com.mediaproject.prompthubs.domain.account.controller;

import com.mediaproject.prompthubs.domain.account.dto.*;
import com.mediaproject.prompthubs.domain.account.dto.UpdateProfileRequest;
import com.mediaproject.prompthubs.domain.account.service.AuthService;
import com.mediaproject.prompthubs.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "Authentication API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Sign up", description = "Create a new account and receive JWT tokens")
    @PostMapping("/signup")
    public ResponseEntity<TokenResponse> signup(@Valid @RequestBody SignupRequest request) {
        TokenResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Login", description = "Authenticate and receive JWT tokens")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Refresh token", description = "Get new access token using refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get current user", description = "Get authenticated user's information")
    @GetMapping("/me")
    public ResponseEntity<AccountResponse> getCurrentUser(@AuthenticationPrincipal CustomUserDetails userDetails) {
        AccountResponse response = authService.getCurrentUser(userDetails.getAccountId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update profile", description = "Update current user's name or avatar")
    @PatchMapping("/me")
    public ResponseEntity<AccountResponse> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {
        AccountResponse response = authService.updateProfile(userDetails.getAccountId(), request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Logout", description = "Invalidate refresh token")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal CustomUserDetails userDetails) {
        authService.logout(userDetails.getAccountId());
        return ResponseEntity.noContent().build();
    }
}