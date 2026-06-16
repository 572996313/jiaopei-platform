package com.jiaopei.platform.education.controller;

import com.jiaopei.platform.auth.annotation.RequiresPermission;
import com.jiaopei.platform.common.api.ApiResponse;
import com.jiaopei.platform.common.web.ExternalIdParser;
import com.jiaopei.platform.education.domain.entity.ClassChangeNoticeEntity;
import com.jiaopei.platform.education.service.ClassChangeNoticeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/education/class-change-notices", "/api/education/class_change_notice"})
@Tag(name = "Education")
public class ClassChangeNoticeController {

    private final ClassChangeNoticeService classChangeNoticeService;

    public ClassChangeNoticeController(ClassChangeNoticeService classChangeNoticeService) {
        this.classChangeNoticeService = classChangeNoticeService;
    }

    @GetMapping
    @RequiresPermission("education:read")
    public ApiResponse<List<ClassChangeNoticeEntity>> list(@RequestParam(required = false) String institutionId,
                                                           @RequestParam(required = false) String classId,
                                                           @RequestParam(required = false) String noticeStatus) {
        return ApiResponse.success("Class change notice list loaded", classChangeNoticeService.listNotices(
            ExternalIdParser.parseOptional(institutionId, "institutionId"),
            ExternalIdParser.parseOptional(classId, "classId"),
            noticeStatus
        ));
    }
}
