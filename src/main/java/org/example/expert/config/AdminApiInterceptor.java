package org.example.expert.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.expert.domain.common.exception.UnauthorizedAdminAccessException;
import org.example.expert.domain.user.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

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
