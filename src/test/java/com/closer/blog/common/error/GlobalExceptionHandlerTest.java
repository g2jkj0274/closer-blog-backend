package com.closer.blog.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest         // 웹 계층(컨트롤러, @RestControllerAdvice)만 띄운다. -> 성능 빠름
@AutoConfigureMockMvc(addFilters = false)           // 보안 필터를 끈다. -> 현재 보안 설정이 없어서 모든 요청이 401로 막히기 때문.
@Import(GlobalExceptionHandlerTest.TestController.class)
public class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;            // 실제 서버를 띄우지 않고 HTTP 요청을 흉내 내 응답을 검사

    // ApiException의 코드, 상태, 직접 넣은 메시지가 그대로 나가고, errors는 빈 배열
    @Test
    void apiException() throws Exception {
        mockMvc.perform(get("/test/api-exception"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POST_NAME_TAKEN"))
                .andExpect(jsonPath("$.message").value("같은 폴더에 pointer.md가 이미 있습니다."))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    // @NotBlank 위반 시 400과 errors[0].field = "name"
    @Test
    void invalidBody() throws Exception {
        mockMvc.perform(post("/test/validation").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    // 깨진 JSON({)은 500이 아니라 400
    @Test
    void malformedBody() throws Exception {
        mockMvc.perform(post("/test/validation").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // 제약 이름 uq_users_username이 409 USERNAME_TAKEN으로 바뀜
    @Test
    void uniqueConstraintViolation() throws Exception {
        mockMvc.perform(get("/test/constraint"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    // 없는 URL은 404 NOT_FOUND
    @Test
    void unknownPath() throws Exception {
        mockMvc.perform(get("/test/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // GET만 있는 경로에 POST를 보내면 405
    @Test
    void methodNotAllowed() throws Exception {
        mockMvc.perform(post("/test/api-exception"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    // 예상 못 한 예외는 500이고, 예외 메시지 "내부 정보"가 응답에 나가지 않음
    @Test
    void unexpectedExceptionHidesDetails() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."));
    }

    @RestController
    static class TestController {

        record NameRequest(@NotBlank String name) {
        }

        @GetMapping("/test/api-exception")
        void apiException() {
            throw new ApiException(ErrorCode.POST_NAME_TAKEN, "같은 폴더에 pointer.md가 이미 있습니다.");
        }

        @PostMapping("/test/validation")
        void validation(@Valid @RequestBody NameRequest request) {
        }

        @GetMapping("/test/constraint")
        void constraint() {
            throw new DataIntegrityViolationException("duplicate",
                    new ConstraintViolationException("duplicate", new SQLException("duplicate"), "uq_users_username"));
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("내부 정보");
        }

    }
}
