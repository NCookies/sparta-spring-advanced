package org.example.expert.domain.user.service;

import org.example.expert.domain.common.exception.InvalidRequestException;
import org.example.expert.domain.user.dto.request.UserRoleChangeRequest;
import org.example.expert.domain.user.entity.User;
import org.example.expert.domain.user.enums.UserRole;
import org.example.expert.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @InjectMocks private UserAdminService userAdminService;
    
    @Mock private UserRepository userRepository;
    
    @DisplayName("유저 권한 변경")
    @Nested
    class changeUserRole {

        @DisplayName("성공")
        @Test
        void givenUserIdAndUserRoleChangeRequest_whenUpdateUserRole_thenWorksFine() {
            // given
            long userId = 1L;

            UserRoleChangeRequest userRoleChangeRequest = new UserRoleChangeRequest(UserRole.ADMIN.name());
            User user = new User("user1@example.com", "password", UserRole.USER);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            // when
            userAdminService.changeUserRole(userId, userRoleChangeRequest);

            // then
            assertThat(user.getUserRole()).isEqualTo(UserRole.ADMIN);
        }

        @DisplayName("대상 유저가 존재하지 않는다면 예외 발생")
        @Test
        void givenInvalidUserId_whenFindUser_thenThrowInvalidRequestException() {
            // given
            long userId = 1L;

            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userAdminService.changeUserRole(userId, any(UserRoleChangeRequest.class)))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("User not found");
        }
        
    }
    
}