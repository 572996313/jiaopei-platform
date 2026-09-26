package com.cornerstone.core.common.web;

import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 查询参数缺失或类型不符要渲染成中文 400。
 *
 * <p>这两类异常原先没有专属分支，会掉进兜底的 {@code Exception} 处理器：响应变成 500「服务器内部错误」，
 * 真实原因（少带了 lessonDate）只留在日志堆栈里，操作员在页面上看不到。
 */
class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new ProbeController())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @Test
    void missingRequiredParameterRendersChineseBadRequest() throws Exception {
        mockMvc.perform(get("/probe/day"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").value("缺少必填参数：lessonDate"));
    }

    @Test
    void mismatchedParameterTypeRendersChineseBadRequest() throws Exception {
        mockMvc.perform(get("/probe/day").param("lessonDate", "2026-09-21").param("pageSize", "abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").value("参数 pageSize 取值格式不正确：abc"));
    }

    @Test
    void mismatchedQueryObjectFieldRendersChineseBadRequest() throws Exception {
        // 分页查询走的是绑定对象（PageQuery），字段类型不符时 Spring 给的是英文技术消息
        mockMvc.perform(get("/probe/page").param("pageSize", "abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("参数 pageSize 取值格式不正确：abc"));
    }

    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        @Operation(summary = "探测用查询接口")
        @GetMapping("/day")
        public String day(@RequestParam String lessonDate, @RequestParam Integer pageSize) {
            return lessonDate + "/" + pageSize;
        }

        @Operation(summary = "探测用分页接口")
        @GetMapping("/page")
        public String page(ProbeQuery query) {
            return String.valueOf(query.getPageSize());
        }
    }

    /** 与底座 PageQuery 同形：Integer 字段 + 绑定异常路径。 */
    static class ProbeQuery {

        private Integer pageSize;

        public Integer getPageSize() {
            return pageSize;
        }

        public void setPageSize(Integer pageSize) {
            this.pageSize = pageSize;
        }
    }
}
