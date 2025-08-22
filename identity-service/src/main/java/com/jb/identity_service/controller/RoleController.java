package com.jb.identity_service.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.jb.identity_service.dto.request.RoleRequest;
import com.jb.identity_service.dto.response.ApiResponse;
import com.jb.identity_service.dto.response.RoleResponse;
import com.jb.identity_service.service.RoleService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RestController
@RequestMapping("/roles")
public class RoleController {
    RoleService roleService;

    @PostMapping
    public ApiResponse<RoleResponse> createRole(@RequestBody RoleRequest request) {
        return ApiResponse.<RoleResponse>builder()
                .result(roleService.createRole(request))
                .build();
    }

    @GetMapping
    public ApiResponse<List<RoleResponse>> getAllRoles() {
        return ApiResponse.<List<RoleResponse>>builder()
                .result(roleService.getAllRoles())
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteRole(@PathVariable String id) {
        roleService.deleteRole(id);
        return ApiResponse.<String>builder().result("Role deleted successfully").build();
    }
}
