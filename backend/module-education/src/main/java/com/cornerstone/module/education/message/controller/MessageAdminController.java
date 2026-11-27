package com.cornerstone.module.education.message.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.module.education.message.dto.MessageEventTypeUpsertRequest;
import com.cornerstone.module.education.message.dto.MessageEventTypeView;
import com.cornerstone.module.education.message.dto.MessageReadLedgerSummaryView;
import com.cornerstone.module.education.message.dto.MessageReaderView;
import com.cornerstone.module.education.message.dto.MessageSourceView;
import com.cornerstone.module.education.message.service.MessageEventTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 事件目录运维（系统设置）。
 *
 * <p>读取与写入都挂 system:message:manage：配置会直接改变所有租户的事件产出，
 * 不能跟业务读取一样登录即可。</p>
 */
@Validated
@RestController
@RequestMapping("/api/education/message-admin")
@Tag(name = "消息与事项")
public class MessageAdminController {

    private final MessageEventTypeService eventTypeService;

    public MessageAdminController(MessageEventTypeService eventTypeService) {
        this.eventTypeService = eventTypeService;
    }

    @Operation(summary = "事件类型目录", description = "含条件、模板、级别、可见性、巡检状态")
    @GetMapping("/types")
    @RequiresPermission("system:message:manage")
    public ApiResponse<List<MessageEventTypeView>> types() {
        return ApiResponse.success("事件类型已加载", eventTypeService.listTypes());
    }

    @Operation(summary = "新建事件类型", description = "巡检类型只需选已登记视图并配条件/模板，不需要改代码")
    @PostMapping("/types")
    @RequiresPermission("system:message:manage")
    public ApiResponse<MessageEventTypeView> createType(
        @Valid @RequestBody MessageEventTypeUpsertRequest request) {
        return ApiResponse.success("事件类型已创建", eventTypeService.createType(request));
    }

    @Operation(summary = "编辑事件类型", description = "类型码创建后不可改；保存前会按视图真实列校验条件与模板")
    @PutMapping("/types/{typeCode}")
    @RequiresPermission("system:message:manage")
    public ApiResponse<MessageEventTypeView> updateType(
        @PathVariable String typeCode,
        @Valid @RequestBody MessageEventTypeUpsertRequest request) {
        return ApiResponse.success("事件类型已更新", eventTypeService.updateType(typeCode, request));
    }

    @Operation(summary = "数据源目录", description = "开发在迁移里登记只读视图，运维只在其上配类型")
    @GetMapping("/sources")
    @RequiresPermission("system:message:manage")
    public ApiResponse<List<MessageSourceView>> sources() {
        return ApiResponse.success("数据源已加载", eventTypeService.listSources());
    }

    @Operation(summary = "校验数据源列", description = "实时读取视图列并刷新列契约，排查视图改名/列缺失")
    @PostMapping("/sources/{sourceCode}/validate")
    @RequiresPermission("system:message:manage")
    public ApiResponse<MessageSourceView> validateSource(@PathVariable String sourceCode) {
        return ApiResponse.success("数据源列已校验", eventTypeService.validateSource(sourceCode));
    }

    @Operation(summary = "阅读台账", description = "按类型看产量、当前未结束数、已读人数、订阅覆盖与巡检状态")
    @GetMapping("/read-ledger")
    @RequiresPermission("system:message:manage")
    public ApiResponse<List<MessageReadLedgerSummaryView>> readLedger() {
        return ApiResponse.success("阅读台账已加载", eventTypeService.readLedger());
    }

    @Operation(summary = "事件阅读人", description = "某事件谁读过、何时读；用于漏订阅/漏处理排查")
    @GetMapping("/{eventId}/readers")
    @RequiresPermission("system:message:manage")
    public ApiResponse<List<MessageReaderView>> readers(@PathVariable Long eventId) {
        return ApiResponse.success("阅读人已加载", eventTypeService.readers(eventId));
    }
}
