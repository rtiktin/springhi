package com.springhi.user.controller;

import com.springhi.user.dto.SignupRequest;
import com.springhi.user.model.User;
import com.springhi.user.repository.UserIpAddressRepository;
import com.springhi.user.repository.UserRepository;
import com.springhi.user.service.AuthService;
import com.springhi.user.service.GoogleOAuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@RecordApplicationEvents
public class AuthControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserIpAddressRepository userIpAddressRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private ApplicationEvents events;

    @MockitoBean
    private GoogleOAuthService googleOAuthService;

    private String testUsername;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @Transactional
    public void testSignup() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        SignupRequest request = new SignupRequest();
        testUsername = "testuser_" + suffix;
        request.setUsername(testUsername);
        request.setEmail("test_" + suffix + "@example.com");
        request.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
        assertEquals(request.getEmail(), events.stream(AuthService.NewAccountCreated.class).findFirst().orElseThrow().email());
        assertEquals(1, events.stream(AuthService.NewAccountCreated.class).count());
    }

    @Test
    @Transactional
    public void testGoogleSignupSendsWelcomeOnlyOnce() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "google_" + suffix + "@example.com";
        when(googleOAuthService.exchange("test-code"))
                .thenReturn(new GoogleOAuthService.GoogleUserInfo("sub_" + suffix, email, "Test", "User"));

        authService.googleLogin("test-code", null, null);
        testUsername = userRepository.findByEmail(email).orElseThrow().getUsername();
        authService.googleLogin("test-code", null, null);

        assertEquals(1, events.stream(AuthService.NewAccountCreated.class).count());
        assertEquals(email, events.stream(AuthService.NewAccountCreated.class).findFirst().orElseThrow().email());
    }

    @Test
    @Transactional
    public void testUsersCanShareIpAddress() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User first = new User();
        testUsername = "ip_first_" + suffix;
        first.setUsername(testUsername);
        first.setEmail("ip_first_" + suffix + "@example.com");
        first.setPassword("test-password");
        first = userRepository.saveAndFlush(first);

        User second = new User();
        second.setUsername("ip_second_" + suffix);
        second.setEmail("ip_second_" + suffix + "@example.com");
        second.setPassword("test-password");
        second = userRepository.saveAndFlush(second);

        String ipAddress = "127.0.0.1";
        userIpAddressRepository.record(first.getId(), ipAddress);
        userIpAddressRepository.record(first.getId(), ipAddress);
        userIpAddressRepository.record(second.getId(), ipAddress);

        var firstEntries = userIpAddressRepository.findByUserIdOrderByLastSeenDesc(first.getId());
        var secondEntries = userIpAddressRepository.findByUserIdOrderByLastSeenDesc(second.getId());
        assertEquals(1, firstEntries.size());
        assertEquals(2, firstEntries.get(0).getRequestCount());
        assertEquals(1, secondEntries.size());
        assertEquals(1, secondEntries.get(0).getRequestCount());
    }

    @AfterTransaction
    public void testUserIsRemoved() {
        assertTrue(userRepository.findByUsername(testUsername).isEmpty());
    }
}
