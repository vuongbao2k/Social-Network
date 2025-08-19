package com.jb.identity_service.service;

import com.jb.identity_service.dto.request.PermissionRequest;
import com.jb.identity_service.dto.response.PermissionResponse;
import com.jb.identity_service.entity.Permission;
import com.jb.identity_service.mapper.PermissionMapper;
import com.jb.identity_service.repository.PermissionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PermissionService {
    PermissionRepository permissionRepository;
    PermissionMapper permissionMapper;

    public PermissionResponse createPermission(PermissionRequest request) {
        Permission permission = permissionMapper.toPermission(request);
        return permissionMapper.toPermissionResponse(permissionRepository.save(permission));
    }

    public List<PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll().stream()
                .map(permissionMapper::toPermissionResponse)
                .toList();
    }

    public void deletePermission(String id) {
        permissionRepository.deleteById(id);
    }
}
