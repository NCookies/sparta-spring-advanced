package org.example.expert.config;

import org.example.expert.domain.comment.controller.CommentAdminController;
import org.example.expert.domain.comment.service.CommentAdminService;
import org.example.expert.domain.common.exception.UnauthorizedAdminAccessException;
import org.example.expert.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
