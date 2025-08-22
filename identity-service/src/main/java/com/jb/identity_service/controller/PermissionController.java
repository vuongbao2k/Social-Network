package com.jb.identity_service.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.jb.identity_service.dto.request.PermissionRequest;
import com.jb.identity_service.dto.response.ApiResponse;
import com.jb.identity_service.dto.response.PermissionResponse;
import com.jb.identity_service.service.PermissionService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RestController
@RequestMapping("/permissions")
public class PermissionController {
    PermissionService permissionService;

    @PostMapping
    public ApiResponse<PermissionResponse> createPermission(@RequestBody PermissionRequest request) {
        PermissionResponse response = permissionService.createPermission(request);
        return ApiResponse.<PermissionResponse>builder().result(response).build();
    }

    @GetMapping
    public ApiResponse<List<PermissionResponse>> getAllPermissions() {
        List<PermissionResponse> permissions = permissionService.getAllPermissions();
        return ApiResponse.<List<PermissionResponse>>builder()
                .result(permissions)
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> deletePermission(@PathVariable String id) {
        permissionService.deletePermission(id);
        return ApiResponse.<String>builder()
                .result("Permission deleted successfully")
                .build();
    }
}
