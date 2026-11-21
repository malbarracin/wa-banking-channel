package com.bcra.users.ws.user.controller;

import java.util.UUID;
import com.bcra.users.ws.user.dto.ChangeStatusRequest;
import com.bcra.users.ws.user.dto.UserResponse;
import com.bcra.users.ws.user.service.UserStatusService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserStatusController {
    private final UserStatusService service;

    public UserStatusController(UserStatusService service) {
        this.service = service;
    }

    @PatchMapping("/api/v1/users/{id}/status")
    public UserResponse change(@PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request) {
        return service.change(id, request);
    }
}
