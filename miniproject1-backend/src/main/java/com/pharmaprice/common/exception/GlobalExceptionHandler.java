package com.pharmaprice.common.exception;

import com.pharmaprice.common.dto.ErrorResponse;
import com.pharmaprice.common.dto.ErrorResponse.FieldError;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 프로젝트 전체에 적용되는 유일한 예외 처리기(docs/ROADMAP.md T-35).
 *
 * <p>기존에 패키지별로 흩어져 있던 5개 임시 처리기({@code AdminStatsExceptionHandler},
 * {@code AuthExceptionHandler}, {@code MultipartUploadExceptionHandler},
 * {@code PriceReportExceptionHandler}, {@code UploadExceptionHandler})를 전부 이 클래스로
 * 흡수한다. {@code basePackages}를 주지 않는다 — {@code MaxUploadSizeExceededException}은
 * 멀티파트 파싱 단계에서 {@code DispatcherServlet}이 핸들러를 찾기도 전에 던져지므로,
 * 셀렉터가 하나라도 있는 어드바이스는 {@code HandlerTypePredicate}가 무조건 걸러버려
 * 절대 못 잡는다(이전 {@code MultipartUploadExceptionHandler}의 javadoc이 이미 밝혀둔
 * 이유). 이 프로젝트에 전역 처리기가 이거 하나뿐이라 스코프를 좁힐 이유도 없다.</p>
 *
 * <p><b>{@code AccessDeniedException}/{@code AuthenticationException}을 반드시 따로 잡아야
 * 한다.</b> 컨트롤러/서비스 메서드 안에서 던져진 이 두 예외(예:
 * {@code PriceReportController}의 {@code InsufficientAuthenticationException}, 리소스
 * 소유자 검증에서 던지는 {@code AccessDeniedException})는 {@code DispatcherServlet}의
 * 핸들러 호출 안에서 발생하므로 {@code ExceptionHandlerExceptionResolver}가 다른 예외와
 * 똑같이 먼저 가로챈다. 여기서 잡지 않고 {@code Exception.class} 캐치올에만 맡기면 500
 * {@code INTERNAL_ERROR}로 응답이 끝나버려, 원래 이 예외들을 처리해야 할
 * {@code JwtAuthenticationEntryPoint}/{@code JwtAccessDeniedHandler}(둘 다 Security
 * 필터 체인 쪽 — {@code ExceptionTranslationFilter}가 필터 체인 "밖으로" 다시 던져진
 * 예외만 받는다)에는 아예 도달하지 못한다. 실제로 이 프로젝트에서 T-35 도입 직후
 * {@code PriceReportIntegrationTest}/{@code UploadIntegrationTest}의 401/403 테스트가
 * 500으로 깨지면서 발견했다.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String INTERNAL_ERROR_MESSAGE = "서버에 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.";

    /** Bean Validation(`@Valid` 바디) 실패 — 400 VALIDATION_FAILED + fieldErrors. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
            .map(f -> new FieldError(f.getField(), f.getDefaultMessage()))
            .toList();
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
            .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED.name(), "요청 값을 확인해 주세요.", fieldErrors));
    }

    /** `@Validated` 메서드/경로 파라미터 검증 실패 — 400 VALIDATION_FAILED + fieldErrors. */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        List<FieldError> fieldErrors = e.getConstraintViolations().stream()
            .map(v -> new FieldError(v.getPropertyPath().toString(), v.getMessage()))
            .toList();
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
            .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED.name(), "요청 값을 확인해 주세요.", fieldErrors));
    }

    /** 도메인 예외 전체 — 각자 생성 시점에 고정한 ErrorCode를 그대로 응답한다. */
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode code = e.errorCode();
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code.name(), e.getMessage()));
    }

    /**
     * 제약 위반. 지금 이 프로젝트에서 유니크 제약 위반 경로는
     * {@code uq_report_user_pair_day}(같은 날 같은 약국·약품 중복 제보) 하나뿐이라
     * 항상 중복 제보로 해석해도 안전하다 — 새 유니크 제약이 추가되면 이 가정을 다시
     * 검토해야 한다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(ErrorCode.DUPLICATE_REPORT.status())
            .body(ErrorResponse.of(ErrorCode.DUPLICATE_REPORT.name(), "이미 같은 날 같은 약국·약품으로 제보하셨습니다."));
    }

    /**
     * 인증 필요(토큰 없음 등) — 401 UNAUTHENTICATED.
     * {@code JwtAuthenticationEntryPoint}(Security 필터 체인 쪽 401)와 같은 메시지로 맞춘다.
     */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException e) {
        return ResponseEntity.status(ErrorCode.UNAUTHENTICATED.status())
            .body(ErrorResponse.of(ErrorCode.UNAUTHENTICATED.name(), "인증이 필요합니다."));
    }

    /**
     * 인가 실패(권한 부족) — 403 FORBIDDEN.
     * {@code JwtAccessDeniedHandler}(Security 필터 체인 쪽 403)와 같은 메시지로 맞춘다.
     */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.status())
            .body(ErrorResponse.of(ErrorCode.FORBIDDEN.name(), "권한이 없습니다."));
    }

    /** 멀티파트 업로드 용량 초과 — 413 FILE_TOO_LARGE. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(ErrorCode.FILE_TOO_LARGE.status())
            .body(ErrorResponse.of(ErrorCode.FILE_TOO_LARGE.name(), "파일 크기는 5MB를 초과할 수 없습니다."));
    }

    /**
     * 그 외 전부 — 500 INTERNAL_ERROR. 원본 예외 메시지·스택트레이스는 로그에만
     * 남기고 응답에는 절대 노출하지 않는다(docs/ROADMAP.md T-35 5번).
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR.name(), INTERNAL_ERROR_MESSAGE));
    }
}
