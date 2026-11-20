package com.cornerstone.module.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.dto.TeacherAccountLinkCandidateResult;
import com.cornerstone.module.education.dto.TeacherAccountLinkCandidateView;
import com.cornerstone.module.education.dto.TeacherAccountLinkRequest;
import com.cornerstone.module.education.dto.TeacherAccountLinkResult;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.service.TeacherAccountLinkService;
import com.cornerstone.module.organization.service.support.CampusScopeGuard;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 按手机号把教师档案和本机构已有的登录账号对上。
 *
 * <p>手机号能做匹配键，靠的是两个唯一键：{@code uk_jp_teacher_phone(institution_id, phone)} 保证
 * 一个号在本机构至多一位教师，账号侧本机构内的同号也只会指向一个人。所以这里的匹配不是模糊猜测，
 * 是机构内一对一的等值比对。</p>
 *
 * <p>为什么不在建档时就顺手开号：开账号要 {@code system:user:manage} 权限、要走密码策略，
 * 那是底座 {@code /api/system/user-import} 的职责（见 AGENTS「底座边界」）。捆在一起等于逼教务
 * 操作员申请系统管理权限，而且会变成两条各自校验的写账号路径。</p>
 *
 * <p>绑定不触碰会话：权限快照写在 JWT 里，而档案关联不改变角色与权限码，本人登录后的可见范围
 * 由查询侧现算，所以既不需要 {@code invalidateUserSessions}，也不会出现旧权限残留。</p>
 */
@Service
public class TeacherAccountLinkServiceImpl implements TeacherAccountLinkService {

    private static final String ACTIVE = "active";
    private static final int MESSAGE_SAMPLES = 5;

    private final TeacherMapper teacherMapper;
    private final UserAccountMapper userAccountMapper;

    public TeacherAccountLinkServiceImpl(TeacherMapper teacherMapper, UserAccountMapper userAccountMapper) {
        this.teacherMapper = teacherMapper;
        this.userAccountMapper = userAccountMapper;
    }

    @Override
    public TeacherAccountLinkCandidateResult previewCandidates() {
        Long institutionId = requireInstitution();
        LambdaQueryWrapper<TeacherEntity> wrapper = new LambdaQueryWrapper<TeacherEntity>()
            .eq(TeacherEntity::getInstitutionId, institutionId)
            .isNull(TeacherEntity::getUserId)
            .orderByAsc(TeacherEntity::getTeacherCode);
        // 与教师列表同口径：机构通用（未分配校区）+ 可访问校区
        CampusScopeGuard.applyIncludingNull(wrapper, TeacherEntity::getCampusId);
        List<TeacherEntity> unlinked = teacherMapper.selectList(wrapper);
        if (unlinked.isEmpty()) {
            return new TeacherAccountLinkCandidateResult(0, 0, 0, List.of());
        }

        List<TeacherEntity> withPhone = unlinked.stream().filter(teacher -> phone(teacher) != null).toList();
        Map<String, List<UserAccountEntity>> accountsByPhone = loadAccountsByPhone(institutionId, withPhone);
        Set<Long> boundUserIds = loadBoundUserIds(accountsByPhone);

        List<TeacherAccountLinkCandidateView> rows = new ArrayList<>();
        for (TeacherEntity teacher : unlinked) {
            rows.addAll(candidatesOf(teacher, accountsByPhone, boundUserIds));
        }
        return new TeacherAccountLinkCandidateResult(unlinked.size(), withPhone.size(), rows.size(), rows);
    }

    /** 一位教师对应零到多行候选：没填手机号/没命中是一行，命中多个账号就拆成多行让人挑 */
    private List<TeacherAccountLinkCandidateView> candidatesOf(TeacherEntity teacher,
                                                               Map<String, List<UserAccountEntity>> accountsByPhone,
                                                               Set<Long> boundUserIds) {
        String phone = phone(teacher);
        if (phone == null) {
            return List.of(row(teacher, null, false, "档案未填手机号，无法自动匹配；可让本人补全手机号后再来，或先在用户管理里改账号手机号"));
        }
        List<UserAccountEntity> matched = accountsByPhone.get(phone);
        if (matched == null || matched.isEmpty()) {
            return List.of(row(teacher, null, false,
                "本机构没有使用该手机号的登录账号，请先到「用户管理 → 批量导入账号」开号"));
        }
        if (matched.size() > 1) {
            String prefix = "该手机号在本机构命中 " + matched.size() + " 个账号，请按显示名称人工确认";
            List<TeacherAccountLinkCandidateView> rows = new ArrayList<>(matched.size());
            for (UserAccountEntity account : matched) {
                boolean free = !boundUserIds.contains(account.getId());
                rows.add(row(teacher, account, free, free ? prefix : prefix + "；该账号已绑定其他教师档案，不可选择"));
            }
            return rows;
        }
        UserAccountEntity account = matched.get(0);
        if (boundUserIds.contains(account.getId())) {
            return List.of(row(teacher, account, false, "该账号已绑定其他教师档案，请先解除原关联"));
        }
        String notice = ACTIVE.equals(account.getStatus()) ? null : "该账号已停用，绑定后本人仍无法登录";
        return List.of(row(teacher, account, true, notice));
    }

    private TeacherAccountLinkCandidateView row(TeacherEntity teacher, UserAccountEntity account,
                                                boolean actionable, String notice) {
        return new TeacherAccountLinkCandidateView(teacher.getId(), teacher.getTeacherCode(),
            teacher.getTeacherName(), teacher.getPhone(),
            account == null ? null : account.getId(),
            account == null ? null : account.getUsername(),
            account == null ? null : account.getDisplayName(),
            account == null ? null : account.getStatus(),
            account == null ? null : Objects.equals(teacher.getTeacherName(), account.getDisplayName()),
            actionable, notice);
    }

    // ------------------------------------------------------------------ 绑定

    @Override
    @Transactional
    public TeacherAccountLinkResult link(TeacherAccountLinkRequest request) {
        Long institutionId = requireInstitution();
        List<String> errors = new ArrayList<>();
        Set<Long> seenTeacherIds = new HashSet<>();

        for (TeacherAccountLinkRequest.Link link : request.links()) {
            if (!seenTeacherIds.add(link.teacherId())) {
                errors.add("教师档案 " + link.teacherId() + " 在本次提交里出现多次");
                continue;
            }
            TeacherEntity teacher = teacherMapper.selectById(link.teacherId());
            if (teacher == null || !Objects.equals(teacher.getInstitutionId(), institutionId)) {
                // 不按 ID 直查他机构档案：归属不符就等价于不存在，不给探测别家数据的回显
                errors.add("教师档案不属于当前机构或不存在");
                continue;
            }
            CampusScopeGuard.assertNullableCampus(teacher.getCampusId(), "教师账号关联");
            if (teacher.getUserId() != null) {
                errors.add("教师 " + teacher.getTeacherName() + " 已绑定账号，需先解除原关联");
                continue;
            }
            UserAccountEntity account = userAccountMapper.selectById(link.userId());
            if (account == null || !Objects.equals(account.getInstitutionId(), institutionId)) {
                errors.add("登录账号不属于当前机构或不存在：" + teacher.getTeacherName());
                continue;
            }
            TeacherEntity holder = teacherMapper.selectOne(new LambdaQueryWrapper<TeacherEntity>()
                .eq(TeacherEntity::getUserId, account.getId())
                .last("LIMIT 1"));
            // 只说"已绑定"不说绑给了谁、在哪个机构，避免拿关联关系探测别家
            if (holder != null) {
                errors.add("账号 " + account.getUsername() + " 已绑定其他教师档案");
                continue;
            }
            int updated = bind(teacher.getId(), account.getId());
            if (updated == 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "教师 " + teacher.getTeacherName() + " 的关联已被其他操作改变，请刷新后重试");
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, joinErrors(errors));
        }
        return new TeacherAccountLinkResult(request.links().size());
    }

    /**
     * 带 {@code user_id IS NULL} 条件的更新：上面的 selectOne 只能挡住串行请求，
     * 并发下两次匹配都会通过前置校验，靠这条守卫让后到的那次改不动（返回 0 行）而不是静默覆盖。
     */
    private int bind(Long teacherId, Long userId) {
        return teacherMapper.update(null, new UpdateWrapper<TeacherEntity>()
            .eq("id", teacherId)
            .isNull("user_id")
            .set("user_id", userId));
    }

    // ------------------------------------------------------------------ 取数

    private Long requireInstitution() {
        AuthenticatedUser current = AuthContext.requireUser();
        if (current.orgId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前登录账号未绑定机构，无法匹配教师账号");
        }
        return current.orgId();
    }

    private Map<String, List<UserAccountEntity>> loadAccountsByPhone(Long institutionId,
                                                                     List<TeacherEntity> teachers) {
        Set<String> phones = new LinkedHashSet<>();
        teachers.forEach(teacher -> {
            String phone = phone(teacher);
            if (phone != null) {
                phones.add(phone);
            }
        });
        if (phones.isEmpty()) {
            return Map.of();
        }
        Map<String, List<UserAccountEntity>> grouped = new LinkedHashMap<>();
        userAccountMapper.selectList(new LambdaQueryWrapper<UserAccountEntity>()
                .eq(UserAccountEntity::getInstitutionId, institutionId)
                .in(UserAccountEntity::getPhone, phones))
            .forEach(account -> grouped
                .computeIfAbsent(account.getPhone(), key -> new ArrayList<>())
                .add(account));
        return grouped;
    }

    /**
     * 候选账号里已经被档案占用的那部分。
     *
     * <p>故意不带机构条件：{@code uk_jp_teacher_user_id} 是全表唯一键，别的机构占住同一个账号
     * 也会让本机构绑不上，只按本机构查就会把这种行当成"可绑定"发出去，落库时撞成 1062。</p>
     */
    private Set<Long> loadBoundUserIds(Map<String, List<UserAccountEntity>> accountsByPhone) {
        Set<Long> candidateUserIds = new HashSet<>();
        accountsByPhone.values().forEach(list -> list.forEach(account -> candidateUserIds.add(account.getId())));
        if (candidateUserIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> bound = new HashSet<>();
        teacherMapper.selectList(new LambdaQueryWrapper<TeacherEntity>()
                .in(TeacherEntity::getUserId, candidateUserIds))
            .forEach(teacher -> bound.add(teacher.getUserId()));
        return bound;
    }

    private static String phone(TeacherEntity teacher) {
        String phone = teacher.getPhone();
        return phone == null || phone.isBlank() ? null : phone.trim();
    }

    private String joinErrors(List<String> errors) {
        if (errors.size() <= MESSAGE_SAMPLES) {
            return String.join("；", errors);
        }
        return String.join("；", errors.subList(0, MESSAGE_SAMPLES))
            + "；等 " + errors.size() + " 处问题，全部修正后重新提交";
    }
}
