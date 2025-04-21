package org.example.expert.aop;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Arrays;

@Aspect
@Component
@RequiredArgsConstructor
public class AdminApiLoggingAspect {

    private final ObjectMapper objectMapper;
    private static final Logger logger = LogManager.getLogger(AdminApiLoggingAspect.class);

    @Around("@annotation(org.example.expert.aop.LogAdmin)")
    public Object logAdminApi(ProceedingJoinPoint joinPoint) throws Throwable {

        HttpServletRequest httpRequest = getHttpRequest();

        if (httpRequest == null) {
            // request 또는 response 값이 null 이면 중간에 종료
            // @Around는 메서드 실행을 감싸는 래퍼이므로 proceed()를 호출해야 원래 메서드가 실행됨
            logger.warn("HttpServletRequest 객체를 가져올 수 없음");
            return joinPoint.proceed();
        }

        String methodName = joinPoint.getSignature().getName();

        Long userId = (Long) httpRequest.getAttribute("userId");          // 요청한 사용자의 ID
        LocalDateTime requestTime = LocalDateTime.now();                    // API 요청 시각
        String requestURI = httpRequest.getRequestURI();                    // API 요청 URL
        String requestBody = getRequestBody(joinPoint);                     // 요청 본문

        logger.info("[Admin API Logging]");
        logger.info("Request 정보: Method: {}, URI: {}, time: {}, userId: {}, RequestBody: {}",
                methodName,
                requestURI,
                requestTime,
                userId,
                requestBody
        );

        Object result;
        try {
            result = joinPoint.proceed();     // 실제 API 실행
        } catch (Exception e) {
            logger.error("에러 발생!! : Method: {}, URI: {}, message: {}",
                    methodName,
                    requestURI,
                    e.getMessage()
            );
            throw e;    // 처리는 ControllerAdvice 에게 위임
        }

        String responseString = convertObjectToJson(result);
        String responseBody = extractBodyFromJson(responseString);

        logger.info("Response 정보: Method: {}, URI: {}, ResponseBody: {}",
                methodName,
                requestURI,
                responseBody
        );

        return result;
    }

    private HttpServletRequest getHttpRequest() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attr != null ? attr.getRequest() : null;
    }

    private String getRequestBody(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length > 0) {
            try {
                return Arrays.stream(args)
                        .map(this::convertObjectToJson)
                        .reduce((arg1, arg2) -> arg1 + ", " + arg2)
                        .orElse("");
            } catch (Exception e) {
                logger.error("Error serializing request body", e);
            }
        }
        return "";
    }

    private String convertObjectToJson(Object object) {
        if (object == null)
            return "";
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing object to JSON", e);
            return "Error serializing object to JSON";
        }
    }

    private String extractBodyFromJson(String json) {
        if (json == null || json.isBlank()) return "";
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode body = root.path("body");
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(body);
        } catch (JsonProcessingException e) {
            logger.error("Error extracting body from JSON", e);
            return "Error extracting body from JSON";
        }
    }

}
