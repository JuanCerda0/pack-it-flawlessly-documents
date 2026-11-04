package dev.noby.documents.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
class DocumentControllerSecurityTests {

    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void requiresAuthenticationForListingAndDownload() throws Exception {
        mvc.perform(get("/api/v1/packages/42/documents")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/documents/1/file")).andExpect(status().isUnauthorized());
    }

    @Test
    void refusesUnrecognizedGroup() throws Exception {
        mvc.perform(get("/api/v1/packages/42/documents").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void permitsSupervisorAndValidatesIds() throws Exception {
        var supervisor = jwt().authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));
        mvc.perform(get("/api/v1/packages/42/documents").with(supervisor))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/packages/0/documents")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/documents/999999/file")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound());
    }
}
