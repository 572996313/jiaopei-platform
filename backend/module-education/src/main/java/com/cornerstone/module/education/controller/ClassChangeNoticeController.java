package com.cornerstone.module.education.controller;

import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.web.ExternalIdParser;
import com.cornerstone.module.education.domain.entity.ClassChangeNoticeEntity;
import com.cornerstone.module.education.service.ClassChangeNoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/education/class-change-notices", "/api/education/class_change_notice"})
@Tag(name = "教务管理")
public class ClassChangeNoticeController {

    private final ClassChangeNoticeService classChangeNoticeService;

    public ClassChangeNoticeController(ClassChangeNoticeService classChangeNoticeService) {
        this.classChangeNoticeService = classChangeNoticeService;
    }

    @Operation(summary = "获取班级变动通知列表")
    @GetMapping
    public ApiResponse<List<ClassChangeNoticeEntity>> list(@RequestParam(required = false) String institutionId,
                                                           @RequestParam(required = false) String classId,
                                                           @RequestParam(required = false) String noticeStatus) {
        return ApiResponse.success("班级变动通知列表已加载", classChangeNoticeService.listNotices(
            ExternalIdParser.parseOptional(institutionId, "institutionId"),
            ExternalIdParser.parseOptional(classId, "classId"),
            noticeStatus
        ));
    }
}
