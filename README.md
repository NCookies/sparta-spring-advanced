# 1. 문제 인식 및 정의

## 문제 요약

- 현재 회원가입 및 로그인 성공 시 발급되는 JWT 토큰에 userRole을 포함시킴
- Admin API 인증 시 JWT 토큰의 userRole 사용
- ⇒ **JWT에 담긴 정보와 DB의 최신 상태가 일치하지 않을 수 있음**
- 예시
    - `ADMIN`인 A 유저가 로그인
    - B 유저가 A 유저의 권한을 일반 `USER`로 변경
    - 이후 A 유저가 Admin API에 접근해도 통과 됨

## 발생하는 문제

1. 보안 허점: 권한이 박탈된 사용자가 여전히 민감한 API에 접근이 가능하다.
2. 회수 불가능한 토큰: JWT는 stateless이기 때문에 한 번 발급되면 서버가 직접 회수할 수 없다.
3. 데이터 일관성 문제: 서버의 현재 유저 상태와 JWT가 다름 → 서버의 신뢰성 떨어짐

# 2. 해결 방안

## 2-1. [의사결정 과정]

아래에서 기존에 로그인을 해서 토큰을 가지고 있지만 중간에 권한이 박탈된 유저를 "**유저 A**"라고 칭하겠다.

### 해결방안 1 : Redis 블랙리스트 등록

- Admin 권한을 가진 유저가 로그인 시 발급받은 Access token을 Redis에 저장
- 권한 변경이 이루어지면 해당 토큰을 블랙리스트로 등록
- Redis에 저장 시 데이터 만료 시간은 JWT 만료 시간과 동일
- 장점 : 블랙리스트로 등록해 간편하게 권한이 변경된 유저를 거를 수 있음
- 단점 : 토큰 저장/관리용 Redis와 관련 추가 로직이 필요함, 이러면 세션 방식이랑 비슷해지는게 아닌가…?

### 해결방안 2 : 프론트에서 강제 로그아웃 시키기

- A의 권한이 변경되면 A의 클라이언트에 푸시 메시지, WebSocket 등으로 로그아웃 요청을 보냄
- 요청을 받은 클라이언트는 해당 토큰을 삭제 및 강제 로그아웃
- 장점 : 사용자 입장에서는 자연스럽게 처리된 것으로 보임
- 단점 : 백엔드 단독으로 해당 기능이 실행된다는 것을 보장할 수 없음

### 해결방안 3 : 매 요청마다 DB의 권한 조회

- JWT 토큰의 userRole 값이 ADMIN인 유저가 Admin API 사용 시 DB에서 userRole 재차 검증
- 장점 : Redis 같은 별도의 저장소 필요 없음, 간단하게 구현 가능
- 단점 : 요청마다 DB 조회 필요

여러 해결방안 중에서 3번 방법을 적용하기로 했다. 그 이유는 다음과 같다.

- 블랙리스트 방식은 Redis와 관련된 추가 작업이 필요하다. 그리고 서버에 별도의 저장소를 필요로 하지 않는다는 JWT의 장점이 무색해진다.
- 현재 프로젝트에서는 프론트가 존재하지 않기 때문에 2번 방법은 구현이 사실상 어렵다.
- 매 요청마다 DB 권한을 검사하는 방식은 추가적인 의존성이나 기술이 필요하지 않고, 구현이 간단하다.
- 또한 Admin API는 일반 API에 비해 접근 빈도가 낮기 때문에, 약간의 비효율은 감수할 수 있을 것으로 보인다. → 이 부분은 추측이니 실제 상황에 맞춰 방식을 바꿔야 할 수도 있다.

## 2-2. [해결 과정]

- `/admin` URI로 요청을 날린 경우, 유저 토큰의 userRole 값을 검사하여 1차 검사를 한다. (기존 코드 그대로 사용)
- 필터와 달리 스프링 빈에 접근할 수 있는 인터셉터를 사용해서 DB 접근
- 특정 URI(`/admin/**`)로 접근하는 요청들에 대해서 인터셉터를 등록해서 2차 검사

**AdminApiInterceptor.java**

```java
@Component
public class AdminApiInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;

    public AdminApiInterceptor(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Long userId = (Long) request.getAttribute("userId");

        // 요청한 사용자가 관리자 권한을 가지고 있는지 체크
        if (!userRepository.existsByIdAndRole(userId)) {
            throw new UnauthorizedAdminAccessException();
        }

        return HandlerInterceptor.super.preHandle(request, response, handler);
    }

}
```

**WebConfig.java**

```java
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AdminApiInterceptor adminApiInterceptor;

    ...

    // Interceptor 등록
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminApiInterceptor)
                .addPathPatterns("/admin/**");
    }

}
```

**UserRepository.java**

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.id = :userId AND u.userRole = 'ADMIN'")
    boolean existsByIdAndRole(Long userId);
}
```

**UnauthorizedAdminAccessException.java**

```java
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
```

**GlobalExceptionHandler.java**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    ...

    @ExceptionHandler(UnauthorizedAdminAccessException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorizedAdminAccessException(UnauthorizedAdminAccessException ex) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        return getErrorResponse(status, ex.getMessage());
    }

    public ResponseEntity<Map<String, Object>> getErrorResponse(HttpStatus status, String message) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", status.name());
        errorResponse.put("code", status.value());
        errorResponse.put("message", message);

        return new ResponseEntity<>(errorResponse, status);
    }
}
```

**AdminApiInterceptorTest.java**

```java
@WebMvcTest(CommentAdminController.class)
@Import({AdminApiInterceptor.class, WebConfig.class})
class AdminApiInterceptorTest {

    @Autowired MockMvc mockMvc;

    @MockBean UserRepository userRepository;
    @MockBean CommentAdminService commentAdminService;

    @DisplayName("JWT 토큰 userRole=ADMIN 이지만 실제 DB userRole=USER 인 경우")
    @Test
    void userRole이_USER인_유저가_Admin_API에_접근하면_Forbidden_예외발생() throws Exception {
        // Given
        long commentId = 1L;
        long userId = 1L;

        given(userRepository.existsByIdAndRole(userId)).willReturn(false);

        // When & Then
        mockMvc.perform(delete("/admin/comments/{commentId}", commentId)
                        .requestAttr("userId", userId))
                .andExpect(status().isForbidden())
                .andExpect(result ->
                        assertThat(result.getResolvedException() instanceof UnauthorizedAdminAccessException).isTrue());
    }

    @DisplayName("JWT 토큰과 DB 모두 userRole=ADMIN 인 경우")
    @Test
    void userRole이_ADMIN인_유저는_Admin_API에_정상_접근할_수_있다() throws Exception {
        // Given
        long commentId = 1L;
        long userId = 1L;

        given(userRepository.existsByIdAndRole(userId)).willReturn(true);

        // When & Then
        mockMvc.perform(delete("/admin/comments/{commentId}", commentId)
                        .requestAttr("userId", userId))
                .andExpect(status().isOk());

        verify(commentAdminService).deleteComment(eq(commentId));
    }
}
```

# 3. 해결 완료

## 3-1. 회고

JWT 기반 인증은 빠르고 stateless하다는 장점이 있지만, 발급 후 사용자 상태 변경(특히 권한 변경)이 반영되지 않는 구조적 한계가 있다. 이로 인해 토큰 발급 이후 권한이 변경된 유저가 여전히 관리자 API에 접근할 수 있다는 보안상 허점이 발생할 수 있었다.

이를 해결하기 위해 요청마다 DB를 통해 권한을 재검증하는 방식을 도입했으며, 이는 성능 저하라는 트레이드 오프를 감수한 선택이었다. 하지만 관리자 권한은 보안상 민감한 영역이기 때문에, 성능보다 안정성을 우선시하는 방향이 더 타당하다고 판단했다.

또한, 관리자 전용 API 전반에 중복 없이 권한 검증을 적용하기 위해 `HandlerInterceptor`를 활용했으며, 이를 통해 코드의 일관성과 유지보수성 또한 함께 확보할 수 있었다.

## 3-2. 전후 데이터 비교

| 항목 | 적용 전 | 적용 후 | 트레이드 오프 |
| --- | --- | --- | --- |
| **권한 검증 방식** | 토큰 기반 1차 검증만 수행 | 토큰 + DB 기반 2중 검증 | 보안성 ↑, 성능 ↓ |
| **권한 변경 반영 시점** | 다음 로그인 시 반영 | 실시간 반영 가능 | 응답 속도 ↓ |
| **예외 처리** | 미정의 (보안 허점 존재) | `UnauthorizedAdminAccessException` + 403 Forbidden | - |