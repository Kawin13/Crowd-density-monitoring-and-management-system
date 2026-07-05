package com.crowdmonitor.controller;

import com.crowdmonitor.dto.request.CameraRequest;
import com.crowdmonitor.dto.response.ApiResponse;
import com.crowdmonitor.dto.response.CameraResponse;
import com.crowdmonitor.service.CameraService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cameras")
@RequiredArgsConstructor
public class CameraController {

    private final CameraService cameraService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CameraResponse>>> getAllCameras() {
        return ResponseEntity.ok(ApiResponse.success(cameraService.getAllCameras()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CameraResponse>> getCamera(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(cameraService.getCameraById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ResponseEntity<ApiResponse<CameraResponse>> createCamera(@Valid @RequestBody CameraRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Camera created", cameraService.createCamera(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ResponseEntity<ApiResponse<CameraResponse>> updateCamera(@PathVariable Long id,
                                                                     @Valid @RequestBody CameraRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Camera updated", cameraService.updateCamera(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCamera(@PathVariable Long id) {
        cameraService.deleteCamera(id);
        return ResponseEntity.ok(ApiResponse.success("Camera deleted", null));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ResponseEntity<ApiResponse<CameraResponse>> startMonitoring(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Monitoring started", cameraService.startMonitoring(id)));
    }

    @PostMapping("/{id}/stop")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ResponseEntity<ApiResponse<CameraResponse>> stopMonitoring(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Monitoring stopped", cameraService.stopMonitoring(id)));
    }
}
