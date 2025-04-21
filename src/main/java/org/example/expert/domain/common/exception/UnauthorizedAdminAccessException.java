package org.example.expert.domain.common.exception;

/**
 * JwtFilter에서 토큰의 userRole을 기반으로 1차 관리자 권한을 검사하지만,
 * 토큰 발급 이후 권한이 변경되었을 가능성에 대비해 DB에서 userRole을 다시 확인한다.
 * DB 기준으로 관리자 권한이 없는 경우 이 예외를 발생시킨다.
 */
public class UnauthorizedAdminAccessException extends RuntimeException {
    public UnauthorizedAdminAccessException() {
        super("관리자 권한이 필요합니다. 로그인 후 다시 시도해주세요.");
    }
}
