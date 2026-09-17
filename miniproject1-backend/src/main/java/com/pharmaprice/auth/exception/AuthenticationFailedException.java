package com.pharmaprice.auth.exception;

/**
 * 로그인 자격 불일치 또는 refresh 토큰 무효(없음/만료/이미 revoke)일 때 던진다.
 * {@code AuthExceptionHandler} 가 {@code 401 UNAUTHENTICATED} 로 변환한다.
 *
 * <p>로그인 실패의 경우 이메일이 존재하지 않는지 비밀번호가 틀렸는지를
 * 구분하지 않고 항상 이 예외 하나로만 던져야 한다 — 계정 존재 여부 노출 방지.</p>
 */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
