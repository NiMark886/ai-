package com.offerpilot.questionbank.web;

import com.offerpilot.common.api.ApiResponse;
import com.offerpilot.questionbank.service.QuestionBankAdminService;
import com.offerpilot.questionbank.web.dto.PageResult;
import com.offerpilot.questionbank.web.dto.QuestionBankAdminRequest;
import com.offerpilot.questionbank.web.dto.QuestionBankAdminView;
import com.offerpilot.questionbank.web.dto.QuestionStatusRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/questions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminQuestionController {

    private final QuestionBankAdminService adminService;

    public AdminQuestionController(QuestionBankAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public ApiResponse<PageResult<QuestionBankAdminView>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long trackId) {
        return ApiResponse.ok(adminService.page(page, size, keyword, trackId));
    }

    @GetMapping("/{id}")
    public ApiResponse<QuestionBankAdminView> get(@PathVariable Long id) {
        return ApiResponse.ok(adminService.get(id));
    }

    @PostMapping
    public ApiResponse<QuestionBankAdminView> create(@Valid @RequestBody QuestionBankAdminRequest request) {
        return ApiResponse.ok(adminService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<QuestionBankAdminView> update(@PathVariable Long id,
                                                     @Valid @RequestBody QuestionBankAdminRequest request) {
        return ApiResponse.ok(adminService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<QuestionBankAdminView> updateStatus(@PathVariable Long id,
                                                           @Valid @RequestBody QuestionStatusRequest request) {
        return ApiResponse.ok(adminService.updateStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminService.delete(id);
        return ApiResponse.ok();
    }
}
