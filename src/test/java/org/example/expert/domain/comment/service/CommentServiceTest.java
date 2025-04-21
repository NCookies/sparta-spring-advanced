package org.example.expert.domain.comment.service;

import org.example.expert.domain.comment.dto.request.CommentSaveRequest;
import org.example.expert.domain.comment.dto.response.CommentResponse;
import org.example.expert.domain.comment.dto.response.CommentSaveResponse;
import org.example.expert.domain.comment.entity.Comment;
import org.example.expert.domain.comment.repository.CommentRepository;
import org.example.expert.domain.common.dto.AuthUser;
import org.example.expert.domain.common.exception.InvalidRequestException;
import org.example.expert.domain.todo.entity.Todo;
import org.example.expert.domain.todo.repository.TodoRepository;
import org.example.expert.domain.user.entity.User;
import org.example.expert.domain.user.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private TodoRepository todoRepository;
    @InjectMocks
    private CommentService commentService;
    
    @DisplayName("댓글 저장")
    @Nested
    class testSaveComment {

        @Test
        public void comment를_정상적으로_등록한다() {
            // given
            long todoId = 1;
            CommentSaveRequest request = new CommentSaveRequest("contents");
            AuthUser authUser = new AuthUser(1L, "email", UserRole.USER);
            User user = User.fromAuthUser(authUser);
            Todo todo = new Todo("title", "title", "contents", user);
            Comment comment = new Comment(request.getContents(), user, todo);

            given(todoRepository.findById(anyLong())).willReturn(Optional.of(todo));
            given(commentRepository.save(any())).willReturn(comment);

            // when
            CommentSaveResponse result = commentService.saveComment(authUser, todoId, request);

            // then
            assertNotNull(result);
        }

        @Test
        public void comment_등록_중_할일을_찾지_못해_에러가_발생한다() {
            // given
            long todoId = 1;
            CommentSaveRequest request = new CommentSaveRequest("contents");
            AuthUser authUser = new AuthUser(1L, "email", UserRole.USER);

            given(todoRepository.findById(anyLong())).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> commentService.saveComment(authUser, todoId, request))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("Todo not found");
        }

    }
    
    @DisplayName("댓글 목록 조회")
    @Nested
    class testGetComments {

        @DisplayName("댓글 목록 조회 성공")
        @Test
        void givenTodoId_whenFindCommentsByTodoId_thenReturnCommentList() {
            // given
            long todoId = 1L;

            User user = new User("user1@example.com", "password", UserRole.USER);
            Todo todo = new Todo("Title", "Contents", "Sunny", user);

            List<Comment> commentList = List.of(
                    new Comment("contents1", user, todo),
                    new Comment("contents2", user, todo),
                    new Comment("contents3", user, todo)
            );

            given(commentRepository.findByTodoIdWithUser(todoId)).willReturn(commentList);

            // when
            List<CommentResponse> comments = commentService.getComments(todoId);

            // then
            assertThat(comments.size()).isEqualTo(3);
            assertThat(comments.get(0).getContents()).isEqualTo("contents1");
            assertThat(comments.get(1).getContents()).isEqualTo("contents2");
            assertThat(comments.get(2).getContents()).isEqualTo("contents3");
        }
        
    }
    
}
