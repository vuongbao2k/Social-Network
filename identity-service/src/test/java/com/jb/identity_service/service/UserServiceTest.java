package com.jb.identity_service.service;

import com.jb.identity_service.dto.request.UserCreationRequest;
import com.jb.identity_service.dto.response.UserResponse;
import com.jb.identity_service.entity.User;
import com.jb.identity_service.exception.AppException;
import com.jb.identity_service.repository.RoleRepository;
import com.jb.identity_service.repository.UserRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@TestPropertySource("/test.properties")
public class UserServiceTest {
    @Autowired
    private UserService userService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private RoleRepository roleRepository;

    private UserCreationRequest request;
    private UserResponse userResponse;
    private LocalDate dateOfBirth;
    private User user;

    @BeforeEach
    void initData() {
        dateOfBirth = LocalDate.of(1990, 1, 1);
        request = UserCreationRequest.builder()
                .username("testuser")
                .password("password123")
                .firstName("Test")
                .lastName("User")
                .dateOfBirth(dateOfBirth)
                .build();

        userResponse = UserResponse.builder()
                .id("12345")
                .username("testuser")
                .firstName("Test")
                .lastName("User")
                .dateOfBirth(dateOfBirth)
                .build();

        user = User.builder()
                .id("12345")
                .username("testuser")
                .firstName("Test")
                .lastName("User")
                .dateOfBirth(dateOfBirth)
                .build();
    }

    @Test
    void createUser_validRequest_success() {
        //GIVEN
        Mockito.when(userRepository.existsByUsername(request.getUsername()))
                .thenReturn(false);
        Mockito.when(userRepository.save(Mockito.any(User.class)))
                .thenReturn(user);
        Mockito.when(roleRepository.findById("USER_ROLE"))
                .thenReturn(Optional.empty()); // Assuming USER_ROLE is not defined in the test context

        //WHEN
        UserResponse response = userService.createUser(request);


        //THEN
        Assertions.assertThat(response.getId()).isEqualTo("12345");
        Assertions.assertThat(response.getUsername()).isEqualTo("testuser");
        Assertions.assertThat(response.getFirstName()).isEqualTo("Test");
        Assertions.assertThat(response.getLastName()).isEqualTo("User");
        Assertions.assertThat(response.getDateOfBirth()).isEqualTo(dateOfBirth);
    }

    @Test
    void createUser_usernameAlreadyExists_throwsException() {
        //GIVEN
        Mockito.when(userRepository.existsByUsername(request.getUsername()))
                .thenReturn(true);

        //WHEN & THEN
        var exception = assertThrows(AppException.class, () -> {
            userService.createUser(request);
        });

        Assertions.assertThat(exception.getErrorCode().getCode()).isEqualTo(1002);
    }

    @Test
    @WithMockUser(username = "testuser")
    void getMyInfo_userExists_returnsUserResponse() {
        //GIVEN
        Mockito.when(userRepository.findByUsername(ArgumentMatchers.anyString()))
                .thenReturn(Optional.of(user));
        //WHEN
        UserResponse response = userService.getMyInfo();
        //THEN
        Assertions.assertThat(response.getId()).isEqualTo("12345");
        Assertions.assertThat(response.getUsername()).isEqualTo("testuser");
        Assertions.assertThat(response.getFirstName()).isEqualTo("Test");
        Assertions.assertThat(response.getLastName()).isEqualTo("User");
        Assertions.assertThat(response.getDateOfBirth()).isEqualTo(dateOfBirth);
    }

    @Test
    @WithMockUser(username = "testuser")
    void getMyInfo_userNotFound_throwsException() {
        //GIVEN
        Mockito.when(userRepository.findByUsername(ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());

        //WHEN & THEN
        var exception = assertThrows(AppException.class, () -> {
            userService.getMyInfo();
        });

        Assertions.assertThat(exception.getErrorCode().getCode()).isEqualTo(1001);
    }
}
