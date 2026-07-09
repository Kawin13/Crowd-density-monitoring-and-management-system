package com.crowdmonitor.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;

    // @Builder.Default is required: without it, Lombok's builder() ignores this
    // initializer entirely, and AuthService.login()/register()/refreshToken(), which
    // build AuthResponse via .builder()...build() without explicitly setting tokenType,
    // would otherwise return tokenType = null in every auth API response.
    @Builder.Default
    private String tokenType = "Bearer";

    private Long expiresIn;
    private UserResponse user;
}
