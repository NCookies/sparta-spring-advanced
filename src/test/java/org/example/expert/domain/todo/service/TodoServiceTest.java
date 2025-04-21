package org.example.expert.domain.todo.service;


import org.example.expert.client.WeatherClient;
import org.example.expert.domain.common.dto.AuthUser;
import org.example.expert.domain.common.exception.InvalidRequestException;
import org.example.expert.domain.todo.dto.request.TodoSaveRequest;
import org.example.expert.domain.todo.dto.response.TodoResponse;
import org.example.expert.domain.todo.dto.response.TodoSaveResponse;
import org.example.expert.domain.todo.entity.Todo;
import org.example.expert.domain.todo.repository.TodoRepository;
import org.example.expert.domain.user.entity.User;
import org.example.expert.domain.user.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @InjectMocks private TodoService todoService;

    @Mock private TodoRepository todoRepository;
    @Mock private WeatherClient weatherClient;

    @DisplayName("일정 저장 성공")
    @Test
    void givenAuthUserAndTodoSaveRequest_whenSaveTodo_thenReturnTodoSaveResponse() {
        // given
        AuthUser authUser = new AuthUser(1L, "a@a.com", UserRole.USER);
        User user = User.fromAuthUser(authUser);
        TodoSaveRequest todoSaveRequest = new TodoSaveRequest("title", "contents");
        String weather = "Squalls";

        Todo todo = new Todo(todoSaveRequest.getTitle(), todoSaveRequest.getContents(), weather, user);

        given(weatherClient.getTodayWeather()).willReturn(weather);
        given(todoRepository.save(any(Todo.class))).willReturn(todo);

        // when
        TodoSaveResponse res = todoService.saveTodo(authUser, todoSaveRequest);

        // then
        assertThat(res.getUser().getId()).isEqualTo(1L);
        assertThat(res.getTitle()).isEqualTo("title");
        assertThat(res.getWeather()).isEqualTo(weather);
        verify(todoRepository).save(any(Todo.class));
    }

    @DisplayName("일정 목록 조회")
    @Test
    void givenPageAndSize_whenFindAll_thenReturnOrderedTodoList() {
        // given
        User user = new User("user1@example.com", "password", UserRole.USER);
        Pageable pageable = PageRequest.of(0, 10);

        List<Todo> content = List.of(
                new Todo("title1", "contents", "Squalls", user),
                new Todo("title2", "contents", "Squalls", user),
                new Todo("title3", "contents", "Squalls", user)
        );
        PageImpl<Todo> todos = new PageImpl<>(content, pageable, 3);

        given(todoRepository.findAllByOrderByModifiedAtDesc(pageable)).willReturn(todos);

        // when
        Page<TodoResponse> todoResponses = todoService.getTodos(1, 10);

        // then
        List<TodoResponse> resContent = todoResponses.getContent();

        assertThat(resContent.size()).isEqualTo(3);
        assertThat(resContent.get(0).getTitle()).isEqualTo("title1");
        assertThat(resContent.get(2).getTitle()).isEqualTo("title3");
        verify(todoRepository).findAllByOrderByModifiedAtDesc(pageable);
    }

    @DisplayName("일정 단일 조회")
    @Test
    void givenTodoId_whenFindTodo_thenReturnTodoResponse() {
        // given
        long todoId = 1L;

        User user = new User("user1@example.com", "password", UserRole.USER);
        Todo todo = new Todo("title", "contents", "Squalls", user);

        given(todoRepository.findByIdWithUser(todoId)).willReturn(Optional.of(todo));

        // when
        TodoResponse response = todoService.getTodo(todoId);

        // then
        assertThat(response.getTitle()).isEqualTo("title");
        assertThat(response.getUser().getEmail()).isEqualTo("user1@example.com");
    }

    @DisplayName("일정 ID가 유효하지 않은 경우 예외 발생")
    @Test
    void givenInvalidTodoId_whenFindTodo_thenThrowInvalidRequestException() {
        // given
        long todoId = 1L;

        given(todoRepository.findByIdWithUser(todoId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> todoService.getTodo(todoId))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Todo not found");
    }

}
