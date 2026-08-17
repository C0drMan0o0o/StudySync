package com.sanjith.studysync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import com.sanjith.studysync.security.JwtUtil;
import com.sanjith.studysync.user.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("studysync")
            .withUsername("studysync")
            .withPassword("studysync");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtUtil jwtUtil;

    @Autowired
    protected UserRepository userRepository;

    protected final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    protected String createAndLoginUser(String email) {
        return createAndLoginUser(email, "Test User");
    }

    protected String createAndLoginUser(String email, String name) {
        com.sanjith.studysync.user.User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    com.sanjith.studysync.user.User u = com.sanjith.studysync.user.User.builder()
                            .email(email)
                            .passwordHash("hashed")
                            .name(name)
                            .createdAt(java.time.LocalDateTime.now())
                            .build();
                    return userRepository.save(u);
                });
        return "Bearer " + jwtUtil.generateToken(user.getEmail());
    }
}
