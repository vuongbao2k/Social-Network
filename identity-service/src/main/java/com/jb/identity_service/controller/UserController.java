package com.jb.identity_service.controller;

import com.jb.identity_service.dto.request.UserCreationRequest;
import com.jb.identity_service.dto.request.UserUpdateRequest;
import com.jb.identity_service.dto.response.ApiResponse;
import com.jb.identity_service.entity.User;
import com.jb.identity_service.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    public User getUserById(@PathVariable String id) {
        return userService.getUserById(id);
    }

    @PostMapping
    public ApiResponse<User> createUser(@RequestBody @Valid UserCreationRequest request) {
        ApiResponse<User> response = new ApiResponse<>();
        response.setResult(userService.createUser(request));
        return response;
    }

    @DeleteMapping("/{id}")
    public String deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return "User deleted successfully.";
    }

    @PutMapping("/{id}")
    public User updateUser(@PathVariable String id, @RequestBody UserUpdateRequest request) {
        return userService.updateUser(id, request);
    }
}
