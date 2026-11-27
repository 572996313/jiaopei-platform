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

/**
 * 公告的小程序码入口。
 *
 * <p>与邀请码那套同构：出图的是微信接口、不是本系统的存储，所以单独一个控制器挂在
 * {@code /api/education/wechat} 下，不混进公告的增删改查。扫码后落到小程序公告详情页，
 * 能不能看到内容仍由"是不是接收人"判定——图片只是入口，不携带访问资格。</p>
 */
@RestController
@RequestMapping("/api/education/wechat/announcements")
@Tag(name = "小程序公告")
public class MiniappAnnouncementController {

    private final MiniappQrcodeService miniappQrcodeService;

    public MiniappAnnouncementController(MiniappQrcodeService miniappQrcodeService) {
        this.miniappQrcodeService = miniappQrcodeService;
    }

    @Operation(summary = "生成公告小程序码",
        description = "按公告ID生成直达小程序公告详情页的小程序码(PNG),scene 携带 a=公告ID;"
            + "只有本机构已发布的公告可生成,同一公告结果固定,服务端缓存30天")
    @RequiresPermission("announcement:write")
    @GetMapping(value = "/{id}/wxacode", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> wxacode(@PathVariable Long id) {
        byte[] png = miniappQrcodeService.createAnnouncementWxacode(id);
        String filename = "wxacode_announcement_" + URLEncoder.encode(String.valueOf(id), StandardCharsets.UTF_8) + ".png";
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
            .body(png);
    }
}
