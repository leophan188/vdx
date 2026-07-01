package com.bpm;

import com.bpm.application.UserAccountService;
import com.bpm.domain.UserAccount;
import com.bpm.infrastructure.AuditEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndUserAdminIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserAccountService userAccountService;
    @Autowired AuditEventRepository auditRepo;
    @Autowired ObjectMapper json;

    private String aliceId;

    @BeforeEach
    void seed() {
        // Lưu ý: KHÔNG deleteAll() bảng audit — append-only (AD-6, Story 1.8). Test đếm theo delta before/after.
        // Tài khoản test (idempotent: bỏ qua nếu đã có do create-drop mỗi context)
        if (userAccountService.listAccounts().stream().noneMatch(a -> a.getUsername().equals("alice"))) {
            UserAccount alice = userAccountService.createAccount("alice", "Secret123", "Alice", "USER", "system");
            aliceId = alice.getId();
            userAccountService.createAccount("admin1", "Admin123", "Quản trị", "ADMIN", "system");
            UserAccount bob = userAccountService.createAccount("bob", "Secret123", "Bob", "USER", "system");
            userAccountService.setLocked(bob.getId(), true, "system");
        } else {
            aliceId = userAccountService.listAccounts().stream()
                    .filter(a -> a.getUsername().equals("alice")).findFirst().orElseThrow().getId();
        }
    }

    private MockHttpSession loginSession(String user, String pw) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", user, "password", pw))))
                .andExpect(status().isOk())
                .andReturn();
        HttpSession session = res.getRequest().getSession(false);
        MockHttpSession mock = new MockHttpSession();
        if (session != null) {
            mock = (MockHttpSession) session;
        }
        return mock;
    }

    @Test
    void login_success_returns200() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "alice", "password", "Secret123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void me_withoutSession_returns401() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withSession_returnsCurrentUser() throws Exception {
        MockHttpSession session = loginSession("alice", "Secret123");
        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void login_wrongPassword_returns401_andAuditsFailure() throws Exception {
        long before = auditRepo.count();
        mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "alice", "password", "WRONGpw1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_FAILED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
        long after = auditRepo.count();
        assertThat(after).isGreaterThan(before);
        assertThat(auditRepo.findAll()).anyMatch(e -> e.getAction().equals("LOGIN_FAILED"));
    }

    @Test
    void login_lockedAccount_returns401() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "bob", "password", "Secret123"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_canCreateUser_returns201() throws Exception {
        MockHttpSession session = loginSession("admin1", "Admin123");
        mvc.perform(post("/api/v1/users").session(session).contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "username", "carol", "password", "Secret123", "fullName", "Carol", "role", "USER"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("carol"));
    }

    @Test
    void nonAdmin_cannotCreateUser_returns403() throws Exception {
        MockHttpSession session = loginSession("alice", "Secret123");
        mvc.perform(post("/api/v1/users").session(session).contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "username", "dave", "password", "Secret123", "fullName", "Dave", "role", "USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_cannotListUsers_returns401() throws Exception {
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    }
}
