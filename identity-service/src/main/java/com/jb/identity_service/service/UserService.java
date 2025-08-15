package com.jb.identity_service.service;

import com.jb.identity_service.dto.request.UserCreationRequest;
import com.jb.identity_service.dto.request.UserUpdateRequest;
import com.jb.identity_service.entity.User;
import com.jb.identity_service.exception.AppException;
import com.jb.identity_service.exception.ErrorCode;
import com.jb.identity_service.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    @Autowired
    UserRepository userRepository;

    public User createUser(UserCreationRequest  request) {
        User user = new User();

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }

        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setDateOfBirth(request.getDateOfBirth());
        return userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(String id) {
        return userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    public void deleteUser(String id) {
        userRepository.deleteById(id);
    }

    public User updateUser(String id, UserUpdateRequest userUpdateRequest) {
        User user = getUserById(id);
        user.setPassword(userUpdateRequest.getPassword());
        user.setFirstName(userUpdateRequest.getFirstName());
        user.setLastName(userUpdateRequest.getLastName());
        user.setDateOfBirth(userUpdateRequest.getDateOfBirth());
        return userRepository.save(user);
    }
}
