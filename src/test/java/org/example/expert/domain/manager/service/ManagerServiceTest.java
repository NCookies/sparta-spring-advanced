package org.example.expert.domain.manager.service;

import org.example.expert.domain.common.dto.AuthUser;
import org.example.expert.domain.common.exception.InvalidRequestException;
import org.example.expert.domain.manager.dto.request.ManagerSaveRequest;
import org.example.expert.domain.manager.dto.response.ManagerResponse;
import org.example.expert.domain.manager.dto.response.ManagerSaveResponse;
import org.example.expert.domain.manager.entity.Manager;
import org.example.expert.domain.manager.repository.ManagerRepository;
import org.example.expert.domain.todo.entity.Todo;
import org.example.expert.domain.todo.repository.TodoRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ManagerServiceTest {

    @InjectMocks
    private ManagerService managerService;

    @Mock
    private ManagerRepository managerRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TodoRepository todoRepository;

    @Nested
    class testSaveManager {

        @Test
        void todo가_정상적으로_등록된다() {
            // given
            AuthUser authUser = new AuthUser(1L, "a@a.com", UserRole.USER);
            User user = User.fromAuthUser(authUser);  // 일정을 만든 유저

            long todoId = 1L;
            Todo todo = createTodo(user);

            long managerUserId = 2L;
            User managerUser = new User("b@b.com", "password", UserRole.USER);  // 매니저로 등록할 유저
            ReflectionTestUtils.setField(managerUser, "id", managerUserId);

            ManagerSaveRequest managerSaveRequest = new ManagerSaveRequest(managerUserId); // request dto 생성

            given(todoRepository.findById(todoId)).willReturn(Optional.of(todo));
            given(userRepository.findById(managerUserId)).willReturn(Optional.of(managerUser));
            given(managerRepository.save(any(Manager.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            ManagerSaveResponse response = managerService.saveManager(authUser, todoId, managerSaveRequest);

            // then
            assertNotNull(response);
            assertEquals(managerUser.getId(), response.getUser().getId());
            assertEquals(managerUser.getEmail(), response.getUser().getEmail());
        }

        @Test
        void todo의_user가_null인_경우_예외가_발생한다() {
            // given
            long todoId = 1L;
            long managerUserId = 2L;

            AuthUser authUser = new AuthUser(1L, "a@a.com", UserRole.USER);

            Todo todo = new Todo();
            ReflectionTestUtils.setField(todo, "user", null);

            ManagerSaveRequest managerSaveRequest = new ManagerSaveRequest(managerUserId);

            given(todoRepository.findById(todoId)).willReturn(Optional.of(todo));

            // when & then
            assertThatThrownBy(() -> managerService.saveManager(authUser, todoId, managerSaveRequest))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("담당자를 등록하려고 하는 유저 또는 일정을 만든 유저가 유효하지 않습니다.");
        }

        @Test
        void manager_등록은_일정_작성자만_가능하다() {
            // given
            long authUserId = 1L;
            long todoCreatorId = 2L;

            AuthUser authUser = new AuthUser(authUserId, "a@a.com", UserRole.USER);

            long todoId = 1L;
            AuthUser todoCreatorAuth = new AuthUser(todoCreatorId, "b@b.com", UserRole.USER);
            User todoCreator = User.fromAuthUser(todoCreatorAuth);
            Todo todo = createTodo(todoCreator);

            given(todoRepository.findById(todoId)).willReturn(Optional.of(todo));

            // when & then
            assertThatThrownBy(() -> managerService.saveManager(authUser, todoId, null))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("담당자를 등록하려고 하는 유저 또는 일정을 만든 유저가 유효하지 않습니다.");
        }

        @Test
        void 일정_작성자는_본인을_담당자로_등록할_수_없다() {
            // given
            long authUserId = 1L;

            AuthUser authUser = new AuthUser(authUserId, "a@a.com", UserRole.USER);
            User user = User.fromAuthUser(authUser);

            long todoId = 1L;
            Todo todo = createTodo(user);

            ManagerSaveRequest managerSaveRequest = new ManagerSaveRequest(authUserId);

            // 일정 작성자와 담당자로 등록하려는 유저가 동일한 경우
            given(todoRepository.findById(todoId)).willReturn(Optional.of(todo));
            given(userRepository.findById(authUserId)).willReturn(Optional.of(user));

            // when & then
            assertThatThrownBy(() -> managerService.saveManager(authUser, todoId, managerSaveRequest))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("일정 작성자는 본인을 담당자로 등록할 수 없습니다.");
        }

    }

    @DisplayName("일정 담당자 목록 조회")
    @Nested
    class testGetManagers {

        @Test // 테스트코드 샘플
        public void manager_목록_조회에_성공한다() {
            // given
            long todoId = 1L;
            User user = createUser();
            Todo todo = createTodo(user);
            ReflectionTestUtils.setField(todo, "id", todoId);

            Manager mockManager = new Manager(todo.getUser(), todo);
            List<Manager> managerList = List.of(mockManager);

            given(todoRepository.findById(todoId)).willReturn(Optional.of(todo));
            given(managerRepository.findByTodoIdWithUser(todoId)).willReturn(managerList);

            // when
            List<ManagerResponse> managerResponses = managerService.getManagers(todoId);

            // then
            assertEquals(1, managerResponses.size());
            assertEquals(mockManager.getId(), managerResponses.get(0).getId());
            assertEquals(mockManager.getUser().getEmail(), managerResponses.get(0).getUser().getEmail());
        }

        @Test
        public void manager_목록_조회_시_Todo가_없다면_InvalidRequestException_에러를_던진다() {
            // given
            long todoId = 1L;
            given(todoRepository.findById(todoId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> managerService.getManagers(todoId))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("Todo not found");
        }

    }
    
    @DisplayName("일정 담당자 삭제")
    @Nested
    class testDeleteManager {

        @DisplayName("일정 담당자 삭제 성공")
        @Test
        void givenValidUserAndTodoAndManager_whenDeleteManager_thenDeleteSuccess() {
            // given
            long userId = 1L;
            long todoId = 1L;
            long managerId = 2L;

            User user = createUser();
            Todo todo = createTodo(user);
            Manager manager = new Manager(user, todo);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(todoRepository.findById(todoId)).willReturn(Optional.of(todo));
            given(todoRepository.findById(todoId)).willReturn(Optional.of(todo));
            given(managerRepository.findById(managerId)).willReturn(Optional.of(manager));

            // when
            managerService.deleteManager(userId, todoId, managerId);

            // then
            then(managerRepository).should().delete(manager);
        }

        @DisplayName("일정의 작성자가 null일 경우 예외 발생")
        @Test
        void givenTodoWithNullUser_whenDeleteManager_thenThrowInvalidRequestException() {
            // given
            long userId = 1L;
            long todoId = 1L;
            long managerId = 2L;

            User user = createUser();
            Todo todo = new Todo();
            ReflectionTestUtils.setField(todo, "user", null);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(todoRepository.findById(todoId)).willReturn(Optional.of(todo));

            // when & then
            assertThatThrownBy(() -> managerService.deleteManager(userId, todoId, managerId))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("해당 일정을 만든 유저가 유효하지 않습니다.");
        }

        @DisplayName("일정 작성자와 유저가 다른 경우 예외 발생")
        @Test
        void givenUserNotCreatorOfTodo_whenDeleteManager_thenThrowInvalidRequestException() {
            // given
            // userId와 일정 작성자가 다름
            long userId = 1L;
            long todoCreatorId = 2L;

            long todoId = 1L;
            long managerId = 1L;

            User user = createUser();
            User todoCreator = createUser();

            ReflectionTestUtils.setField(user, "id", userId);
            ReflectionTestUtils.setField(todoCreator, "id", todoCreatorId);

            Todo todo = createTodo(todoCreator);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(todoRepository.findById(todoId)).willReturn(Optional.of(todo));

            // when & then
            assertThatThrownBy(() -> managerService.deleteManager(userId, todoId, managerId))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("해당 일정을 만든 유저가 유효하지 않습니다.");
        }

        @DisplayName("삭제하려는 담당자가 해당 일정에 등록된 담당자가 아니라면 예외 발생")
        @Test
        void givenManagerNotBelongToTodo_whenDeleteManager_thenThrowInvalidRequestException() {
            // given
            long userId = 1L;

            // todoId와 담당자의 일정 id가 다름
            long todoId = 1L;
            long managedTodoId = 2L;

            long managerId = 1L;

            User user = createUser();
            Todo todo = createTodo(user);
            Todo managedTodo = createTodo(user);

            ReflectionTestUtils.setField(user, "id", userId);
            ReflectionTestUtils.setField(todo, "id", todoId);
            ReflectionTestUtils.setField(managedTodo, "id", managedTodoId);

            Manager manager = new Manager(user, managedTodo);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(todoRepository.findById(todoId)).willReturn(Optional.of(todo));
            given(managerRepository.findById(managerId)).willReturn(Optional.of(manager));

            // when & then
            assertThatThrownBy(() -> managerService.deleteManager(userId, todoId, managerId))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("해당 일정에 등록된 담당자가 아닙니다.");
        }

    }

    private User createUser() {
        return new User("user1@example.com", "password", UserRole.USER);
    }

    private Todo createTodo(User user) {
        return new Todo("Title", "Contents", "Sunny", user);
    }

}
