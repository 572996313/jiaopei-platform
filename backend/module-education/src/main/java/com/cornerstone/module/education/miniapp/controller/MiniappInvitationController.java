package com.cornerstone.module.education.miniapp.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.module.education.miniapp.service.MiniappQrcodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/education/wechat/invitations")
@Tag(name = "小程序邀请码")
public class MiniappInvitationController {

    private final MiniappQrcodeService miniappQrcodeService;

    public MiniappInvitationController(MiniappQrcodeService miniappQrcodeService) {
        this.miniappQrcodeService = miniappQrcodeService;
    }

    @Operation(summary = "生成邀请码小程序码",
        description = "按邀请码生成直达小程序注册页的太阳码(PNG),扫码后 scene 携带凭证编码自动预检;同一凭证结果固定,服务端缓存30天")
    @RequiresPermission("education:class:write")
    @GetMapping(value = "/{code}/wxacode", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> wxacode(@PathVariable String code) {
        byte[] png = miniappQrcodeService.createInviteWxacode(code);
        String filename = "wxacode_" + URLEncoder.encode(code, StandardCharsets.UTF_8) + ".png";
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
            .body(png);
    }
}
