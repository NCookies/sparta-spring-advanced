package org.example.expert.domain.auth.service;

import org.example.expert.config.JwtUtil;
import org.example.expert.config.PasswordEncoder;
import org.example.expert.domain.auth.dto.request.SigninRequest;
import org.example.expert.domain.auth.dto.request.SignupRequest;
import org.example.expert.domain.auth.dto.response.SigninResponse;
import org.example.expert.domain.auth.dto.response.SignupResponse;
import org.example.expert.domain.auth.exception.AuthException;
import org.example.expert.domain.common.exception.InvalidRequestException;
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
class AuthServiceTest {

    @InjectMocks AuthService authService;

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;

    @DisplayName("회원가입")
    @Nested
    class signUp {

        @DisplayName("성공")
        @Test
        void givenSignupRequest_whenSaveUserAndCreateToken_thenReturnSignupResponse() {
            // given
            String password = "password";
            String encodedPassword = "encoded_password";
            String token = "token";

            SignupRequest signupRequest = createSignupRequest();
            User user = new User(
                    signupRequest.getEmail(),
                    encodedPassword,
                    UserRole.USER
            );

            given(userRepository.existsByEmail(signupRequest.getEmail())).willReturn(false);
            given(passwordEncoder.encode(password)).willReturn(encodedPassword);
            given(userRepository.save(any(User.class))).willReturn(user);
            given(jwtUtil.createToken(any(), any(), any())).willReturn(token);

            // when
            SignupResponse signupResponse = authService.signup(signupRequest);

            // then
            assertThat(signupResponse.getBearerToken()).isEqualTo("token");
            verify(passwordEncoder).encode(password);
            verify(jwtUtil).createToken(any(), any(), any());
        }

        @DisplayName("이미 존재하는 이메일로 회원가입 시 예외 발생")
        @Test
        void givenDuplicatedEmail_whenExistsByEmail_thenThrowInvalidRequestException() {
            // given
            SignupRequest signupRequest = createSignupRequest();

            given(userRepository.existsByEmail(any())).willReturn(true);

            // when & then
            assertThatThrownBy(() -> authService.signup(signupRequest))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("이미 존재하는 이메일입니다.");
        }

    }

    @DisplayName("로그인")
    @Nested
    class signin {

        @DisplayName("로그인 성공")
        @Test
        void givenSigninRequest_whenFindUser_thenReturnSigninResponse() {
            // given
            SigninRequest signinRequest = createSigninRequest();
            User user = new User(
                    signinRequest.getEmail(),
                    "encoded_" + signinRequest.getPassword(),
                    UserRole.USER
            );

            given(userRepository.findByEmail(signinRequest.getEmail())).willReturn(Optional.of(user));
            given(passwordEncoder.matches(signinRequest.getPassword(), user.getPassword())).willReturn(true);
            given(jwtUtil.createToken(any(), any(), any())).willReturn("token");

            // when
            SigninResponse response = authService.signin(signinRequest);

            // then
            assertThat(response.getBearerToken()).isEqualTo("token");
            verify(passwordEncoder).matches(any(), any());
        }

        @DisplayName("입력한 이메일로 가입된 유저가 없다면 예외 발생")
        @Test
        void givenInvalidEmail_whenFindByEmail_thenThrowInvalidRequestException() {
            // given
            given(userRepository.findByEmail(any())).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.signin(createSigninRequest()))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("가입되지 않은 유저입니다.");
        }

        @DisplayName("비밀번호가 일치하지 않을 경우 예외 발생")
        @Test
        void givenInvalidPassword_whenMatchPassword_thenThrowInvalidRequestException() {
            // given
            given(userRepository.findByEmail(any())).willReturn(Optional.of(new User()));
            given(passwordEncoder.matches(any(), any())).willReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.signin(createSigninRequest()))
                    .isInstanceOf(AuthException.class)
                    .hasMessage("잘못된 비밀번호입니다.");
        }
        
    }

    private SignupRequest createSignupRequest() {
        return new SignupRequest("user1@example.com", "password", "USER");
    }

    private SigninRequest createSigninRequest() {
        return new SigninRequest("user1@example.com", "password");
    }

}
