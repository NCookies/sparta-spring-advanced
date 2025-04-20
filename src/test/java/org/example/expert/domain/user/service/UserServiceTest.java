package org.example.expert.domain.user.service;

import org.example.expert.config.PasswordEncoder;
import org.example.expert.domain.common.exception.InvalidRequestException;
import org.example.expert.domain.user.dto.request.UserChangePasswordRequest;
import org.example.expert.domain.user.dto.response.UserResponse;
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

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks UserService userService;

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;

    @DisplayName("유저 조회")
    @Nested
    class getUser {

        @DisplayName("성공")
        @Test
        void givenUserId_whenFindById_thenReturnUserResponse() {
            // given
            long userId = 1L;

            User user = createUser();

            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            // when
            UserResponse response = userService.getUser(userId);

            // then
            assertThat(response.getEmail()).isEqualTo("user1@example.com");
        }

        @DisplayName("유효하지 않은 유저 ID로 조회 시 예외 발생")
        @Test
        void givenInvalidUserId_whenFindById_thenThrowInvalidRequestException() {
            // given
            long userId = 1L;

            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.getUser(userId))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("User not found");
        }

    }

    @DisplayName("비밀번호 변경")
    @Nested
    class changePassword {

        @DisplayName("성공")
        @Test
        void givenUserIdAndUserChangePasswordRequest_whenChangePassword_thenWorksFine() {
            // given
            long userId = 1L;
            User user = createUser();
            UserChangePasswordRequest request = createUserChangePasswordRequest();

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(request.getNewPassword(), user.getPassword())).willReturn(false);
            given(passwordEncoder.matches(request.getOldPassword(), user.getPassword())).willReturn(true);
            given(passwordEncoder.encode(request.getNewPassword())).willReturn("new_password");

            // when
            userService.changePassword(userId, request);

            // then
            assertThat(user.getPassword()).isEqualTo("new_password");
        }

        @DisplayName("새 비밀번호가 기존 비밀번호와 같을 경우 예외 발생")
        @Test
        void givenSameNewPassword_whenMatchesPassword_thenThrowInvalidRequestException() {
            // given
            long userId = 1L;
            User user = createUser();
            UserChangePasswordRequest request = createUserChangePasswordRequest();

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(request.getNewPassword(), user.getPassword())).willReturn(true);

            // when & then
            assertThatThrownBy(() -> userService.changePassword(userId, request))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("새 비밀번호는 기존 비밀번호와 같을 수 없습니다.");
        }

        @DisplayName("기존 비밀번호가 틀렸을 경우 예외 발생")
        @Test
        void givenInvalidOldPassword_whenMatchesPassword_thenThrowInvalidRequestException() {
            // given
            long userId = 1L;
            User user = createUser();
            UserChangePasswordRequest request = createUserChangePasswordRequest();

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(any(), any())).willReturn(false);
            given(passwordEncoder.matches(request.getOldPassword(), user.getPassword())).willReturn(false);

            // when & then
            assertThatThrownBy(() -> userService.changePassword(userId, request))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("잘못된 비밀번호입니다.");
        }

    }

    private User createUser() {
        return new User("user1@example.com", "password", UserRole.USER);
    }

    private UserChangePasswordRequest createUserChangePasswordRequest() {
        return new UserChangePasswordRequest("old_password", "new_password");
    }

}
