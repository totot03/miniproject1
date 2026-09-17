package com.pharmaprice.auth.security;

import com.pharmaprice.auth.domain.UserRole;

/**
 * access 토큰 클레임에서 복원한 인증 주체. {@code SecurityContext} 에
 * {@code Authentication} 의 principal 로 담긴다.
 */
public record AuthPrincipal(Long userId, UserRole role) {
}
