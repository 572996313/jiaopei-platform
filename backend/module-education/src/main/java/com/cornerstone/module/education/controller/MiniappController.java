package com.cornerstone.module.education.controller;

import com.cornerstone.module.education.dto.BindStudentRequest;
import com.cornerstone.module.education.dto.MiniappClassmateGroupView;
import com.cornerstone.module.education.dto.MiniappHomeOverviewView;
import com.cornerstone.module.education.dto.MiniappLessonView;
import com.cornerstone.module.education.dto.MiniappStudentFeeView;
import com.cornerstone.module.education.dto.ParentStudentCreateRequest;
import com.cornerstone.module.education.dto.StudentBindSelfRequest;
import com.cornerstone.module.education.dto.StudentBriefView;
import com.cornerstone.module.education.dto.StudentOwnProfileView;
import com.cornerstone.module.education.dto.StudentProfileSaveRequest;
import com.cornerstone.module.education.service.MiniappService;
import com.cornerstone.core.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.cornerstone.core.auth.annotation.RequiresPermission;

@Validated
@RestController
@RequestMapping("/api/miniapp")
@Tag(name = "小程序端服务")
public class MiniappController {

    private final MiniappService miniappService;

    public MiniappController(MiniappService miniappService) {
        this.miniappService = miniappService;
    }

    @Operation(summary = "绑定学员档案",
        description = "通过学员编号或监护人手机号把学员档案关联到当前登录用户(学生/家长均可绑定,同一学员或同一用户不可重复绑定)")
    @RequiresPermission("miniapp:access")
    @PostMapping("/students/bind")
    public ApiResponse<StudentBriefView> bindStudent(@Valid @RequestBody BindStudentRequest request) {
        return ApiResponse.success("绑定成功", miniappService.bindStudent(request));
    }

    @Operation(summary = "家长-代建学员档案",
        description = "家长为尚未持有手机/账号的学生新建档案并自动绑定到本人;机构取当前账号所属机构,学号自动生成")
    @RequiresPermission("miniapp:access")
    @PostMapping("/students/create")
    public ApiResponse<StudentBriefView> createStudentForParent(
            @Valid @RequestBody ParentStudentCreateRequest request) {
        return ApiResponse.success("学员档案已创建", miniappService.createStudentForParent(request));
    }

    @Operation(summary = "家长-学员费用概览",
        description = "只读返回当前账号已绑定学员的收费台账与收款流水,金额由服务端现算;仅能查看自己绑定的学员")
    @RequiresPermission("miniapp:access")
    @GetMapping("/students/{studentId}/fees")
    public ApiResponse<MiniappStudentFeeView> studentFees(@PathVariable Long studentId) {
        return ApiResponse.success("已获取费用信息", miniappService.studentFees(studentId));
    }

    @Operation(summary = "解绑学员档案",
        description = "解除当前用户与该学员的档案关联,仅能解绑自己已绑定的学员;解绑后可重新绑定")
    @RequiresPermission("miniapp:access")
    @DeleteMapping("/students/{studentId}")
    public ApiResponse<Void> unbindStudent(@PathVariable Long studentId) {
        miniappService.unbindStudent(studentId);
        return ApiResponse.success("已解绑", null);
    }

    @Operation(summary = "学生端-我的档案",
        description = "查询当前学生自身档案;未绑定时 bound=false,由前端引导绑定或新建")
    @RequiresPermission("miniapp:access")
    @GetMapping("/student/profile")
    public ApiResponse<StudentOwnProfileView> myStudentProfile() {
        return ApiResponse.success("已获取档案", miniappService.myStudentProfile());
    }

    @Operation(summary = "学生端-绑定自己的档案",
        description = "按学号+姓名绑定已有档案到当前学生账号;绑定后不可换绑,重复调用幂等")
    @RequiresPermission("miniapp:access")
    @PostMapping("/student/bind-self")
    public ApiResponse<StudentOwnProfileView> bindSelfStudent(@Valid @RequestBody StudentBindSelfRequest request) {
        return ApiResponse.success("绑定成功", miniappService.bindSelfStudent(request));
    }

    @Operation(summary = "学生端-新建/编辑本人档案",
        description = "未归属档案时自动创建并归属当前学生(学号自动生成);已归属时仅可编辑姓名/性别/生日/监护人")
    @RequiresPermission("miniapp:access")
    @PostMapping("/student/profile")
    public ApiResponse<StudentOwnProfileView> saveStudentProfile(@Valid @RequestBody StudentProfileSaveRequest request) {
        return ApiResponse.success("保存成功", miniappService.saveStudentProfile(request));
    }

    @Operation(summary = "当前用户已关联的学员列表")
    @RequiresPermission("miniapp:access")
    @GetMapping("/students")
    public ApiResponse<List<StudentBriefView>> listMyStudents() {
        return ApiResponse.success("已获取学员列表", miniappService.listMyStudents());
    }

    @Operation(summary = "学员课表",
        description = "按日期范围返回当前用户某位学员的课次(联查班级/课程/教室,教师只给姓);只有绑定该学员的当前用户能查到,他人无权")
    @RequiresPermission("miniapp:access")
    @GetMapping("/students/{studentId}/lessons")
    public ApiResponse<List<MiniappLessonView>> studentLessons(
            @PathVariable Long studentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ApiResponse.success("已获取课表", miniappService.studentLessons(studentId, start, end));
    }

    @Operation(summary = "学员的同班同学名单",
        description = "按该学员当前所在班级分组返回同学姓名(不含本人);班级不由客户端指定,只给姓名,学号与联系方式一律不下发")
    @RequiresPermission("miniapp:access")
    @GetMapping("/students/{studentId}/classmates")
    public ApiResponse<List<MiniappClassmateGroupView>> studentClassmates(@PathVariable Long studentId) {
        return ApiResponse.success("已获取同学名单", miniappService.studentClassmates(studentId));
    }

    @Operation(summary = "首页今日近况",
        description = "当前账号名下全部绑定学员合并:今日课次、本周节数、下一节预告与未分班名单;停课课次不计")
    @RequiresPermission("miniapp:access")
    @GetMapping("/home/overview")
    public ApiResponse<MiniappHomeOverviewView> homeOverview() {
        return ApiResponse.success("已获取首页近况", miniappService.homeOverview());
    }
}