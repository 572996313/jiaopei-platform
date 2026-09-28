package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "小程序首页近况概览(当前账号名下全部绑定学员合并)")
public record MiniappHomeOverviewView(
    @Schema(description = "是否已绑定学员档案(false=未绑定,首页引导去绑定或自助建档)")
    Boolean bound,
    @Schema(description = "今日课次(不含已停课),按开始时间升序")
    List<MiniappHomeLessonView> todayLessons,
    @Schema(description = "本周(周一至周日)课次数(不含已停课)")
    Integer weekLessonCount,
    @Schema(description = "本周内下一节待上的课,今天没有课时的预告;为空表示本周之后无课")
    MiniappHomeLessonView nextLesson,
    @Schema(description = "已绑定但当前不在任何班级的学员姓名(空态里提示等机构分班)")
    List<String> studentsWithoutClass
) {
}
