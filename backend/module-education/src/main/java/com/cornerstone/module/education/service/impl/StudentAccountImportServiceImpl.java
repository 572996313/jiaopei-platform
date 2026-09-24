package com.cornerstone.module.education.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.domain.UserRoleRelEntity;
import com.cornerstone.core.auth.dto.ActivationCodeCreateRequest;
import com.cornerstone.core.auth.dto.InvitationView;
import com.cornerstone.core.auth.dto.UserCreateRequest;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.auth.mapper.UserRoleRelMapper;
import com.cornerstone.core.auth.service.AuthInvitationService;
import com.cornerstone.core.auth.service.SystemUserService;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.UserStudentRelEntity;
import com.cornerstone.module.education.dto.StudentAccountActivationRequest;
import com.cornerstone.module.education.dto.StudentAccountActivationView;
import com.cornerstone.module.education.dto.StudentAccountImportResult;
import com.cornerstone.module.education.dto.StudentAccountImportRow;
import com.cornerstone.module.education.dto.StudentAccountImportRowView;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.UserStudentRelMapper;
import com.cornerstone.module.education.service.StudentAccountImportService;
import com.cornerstone.module.education.service.support.StudentAccountImportTemplate;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StudentAccountImportServiceImpl implements StudentAccountImportService {

    private static final String PENDING = AuthInvitationService.STATUS_PENDING_ACTIVATION;
    private static final String KIND_PARENT = "家长";
    private static final String KIND_STUDENT = "学生本人";
    private static final int MAX_ROWS = 2000;
    private static final Pattern PHONE = Pattern.compile("^1\\d{10}$");
    /** Excel 把手机号/学号存成数值时，EasyExcel 会回给科学计数法或带 .0 的形态，需要先还原成整串数字 */
    private static final Pattern NUMERIC_FORM = Pattern.compile("^\\d+(\\.\\d*)?[Ee][-+]?\\d+$|^\\d+\\.0+$");
    private static final char[] PASSWORD_ALPHABET =
        "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StudentMapper studentMapper;
    private final UserStudentRelMapper userStudentRelMapper;
    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final SystemUserService systemUserService;
    private final AuthInvitationService authInvitationService;

    public StudentAccountImportServiceImpl(StudentMapper studentMapper,
                                          UserStudentRelMapper userStudentRelMapper,
                                          UserAccountMapper userAccountMapper,
                                          RoleMapper roleMapper,
                                          UserRoleRelMapper userRoleRelMapper,
                                          SystemUserService systemUserService,
                                          AuthInvitationService authInvitationService) {
        this.studentMapper = studentMapper;
        this.userStudentRelMapper = userStudentRelMapper;
        this.userAccountMapper = userAccountMapper;
        this.roleMapper = roleMapper;
        this.userRoleRelMapper = userRoleRelMapper;
        this.systemUserService = systemUserService;
        this.authInvitationService = authInvitationService;
    }

    @Override
    public byte[] buildTemplate() {
        return StudentAccountImportTemplate.build();
    }

    @Override
    public byte[] buildRoster(Long institutionId, Long campusId) {
        assertInstitution(institutionId);
        Set<Long> boundStudentIds = userStudentRelMapper.selectList(new LambdaQueryWrapper<UserStudentRelEntity>()
                .eq(UserStudentRelEntity::getInstitutionId, institutionId))
            .stream()
            .map(UserStudentRelEntity::getStudentId)
            .collect(Collectors.toSet());
        // 学生本人账号占 jp_student.user_id，家长账号占 jp_user_student_rel，两处都没沾才算"没账号"
        List<StudentAccountImportTemplate.RosterRow> rows = studentMapper.selectList(
                new LambdaQueryWrapper<StudentEntity>()
                    .eq(StudentEntity::getInstitutionId, institutionId)
                    .eq(campusId != null, StudentEntity::getCampusId, campusId)
                    .orderByAsc(StudentEntity::getStudentNo))
            .stream()
            .filter(student -> student.getUserId() == null && !boundStudentIds.contains(student.getId()))
            .map(student -> new StudentAccountImportTemplate.RosterRow(
                student.getStudentNo(), student.getStudentName()))
            .toList();
        if (rows.size() > MAX_ROWS) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "待开账号的学员有 " + rows.size() + " 人，超过单次导入上限 " + MAX_ROWS + "，请按校区分批导出");
        }
        return StudentAccountImportTemplate.build(rows);
    }

    @Override
    public StudentAccountImportResult preview(MultipartFile file, Long institutionId) {
        assertInstitution(institutionId);
        return validate(parse(file), institutionId);
    }

    @Override
    @Transactional
    public StudentAccountImportResult commit(MultipartFile file, Long institutionId) {
        assertInstitution(institutionId);
        List<Plan> plans = parse(file);
        StudentAccountImportResult result = validate(plans, institutionId);
        if (result.errorCount() > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "存在 " + result.errorCount() + " 行校验不通过，整批未导入，请修正后重新上传");
        }
        return persist(plans, institutionId, result);
    }

    @Override
    @Transactional
    public List<StudentAccountActivationView> createActivationCodes(StudentAccountActivationRequest request,
                                                                    Long institutionId) {
        assertInstitution(institutionId);
        List<Long> userIds = request.userIds().stream().filter(Objects::nonNull).distinct().toList();
        List<StudentAccountActivationView> views = new ArrayList<>(userIds.size());
        for (Long userId : userIds) {
            UserAccountEntity account = userAccountMapper.selectById(userId);
            if (account == null) {
                views.add(new StudentAccountActivationView(userId, null, null, null, null, null, "账号不存在"));
                continue;
            }
            if (!Objects.equals(account.getInstitutionId(), institutionId)) {
                views.add(toView(account, null, null, "账号不属于当前机构"));
                continue;
            }
            if (!PENDING.equals(account.getStatus())) {
                views.add(toView(account, null, null,
                    "active".equals(account.getStatus()) ? "账号已激活，请直接登录" : "账号状态不支持生成激活码"));
                continue;
            }
            InvitationView invitation = authInvitationService
                .createActivationCode(new ActivationCodeCreateRequest(userId, request.validDays()));
            views.add(toView(account, invitation.code(), invitation.expiresAt(), "已生成激活码"));
        }
        return views;
    }

    // ------------------------------------------------------------------ 解析与校验

    private List<Plan> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择要导入的 xlsx 文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只支持 xlsx 格式，请先下载模板填写");
        }
        List<StudentAccountImportRow> rows;
        try (InputStream in = file.getInputStream()) {
            rows = EasyExcel.read(in).head(StudentAccountImportRow.class).sheet().doReadSync();
        } catch (IOException | RuntimeException exception) {
            // 文件损坏、被加密、表头行缺失都到这里，给出可操作的提示而不是 500
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "文件解析失败：" + rootMessage(exception) + "。请确认使用系统模板且内容在第一个工作表");
        }
        List<Plan> plans = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            Plan plan = Plan.from(rows.get(index), index + 2);
            if (plan.isEmpty()) {
                continue;
            }
            plans.add(plan);
        }
        if (plans.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "未读到有效数据行，请填写模板后重新上传");
        }
        if (plans.size() > MAX_ROWS) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "单次导入不超过 " + MAX_ROWS + " 行");
        }
        return plans;
    }

    private StudentAccountImportResult validate(List<Plan> plans, Long institutionId) {
        Map<String, StudentEntity> studentByNo = loadStudents(institutionId, plans);
        Map<String, UserAccountEntity> accountByUsername = loadAccounts(plans);
        Map<String, Long> roleIds = loadRoleIds();

        // 一行 = 一条「账号 ↔ 学员」配对：同一账号可以多行（家长带两个孩子），
        // 但一个学员只能有一个"学生本人"账号，这一条必须在整批范围内判，
        // 否则后落库的那行会静默少一条归属、页面上看不出来。
        Set<String> seenPairs = new HashSet<>();
        Map<String, String> studentOwnClaims = new HashMap<>();
        for (Plan plan : plans) {
            UserAccountEntity found = plan.username == null ? null : accountByUsername.get(plan.username);
            // 登录账号是全局唯一键，别的机构占了同号时不能当"自家账号已存在"来复用，
            // 否则会把本机构学员绑到别人家的账号上。
            boolean foreignOwned = found != null && !institutionId.equals(found.getInstitutionId());
            plan.existing = foreignOwned ? null : found;
            plan.userId = plan.existing == null ? null : plan.existing.getId();
            if (foreignOwned) {
                plan.errors.add("登录账号 " + plan.username + " 已被其他机构占用，请在登录账号列换一个");
            } else if (plan.existing != null && !PENDING.equals(plan.existing.getStatus())) {
                plan.errors.add("登录账号 " + plan.username + " 已存在且不是待激活状态，请到用户管理处理");
            } else if (plan.existing != null) {
                plan.notices.add("账号已存在，本次只补绑定关系");
            }

            if (plan.studentNo == null) {
                plan.errors.add("学员编号不能为空");
            } else {
                StudentEntity student = studentByNo.get(plan.studentNo);
                if (student == null) {
                    plan.errors.add("未找到学员编号 " + plan.studentNo + " 对应的学员档案");
                } else {
                    plan.student = student;
                    plan.studentId = student.getId();
                    if (plan.studentName != null && !plan.studentName.equals(student.getStudentName())) {
                        plan.errors.add("学员姓名与档案不一致，档案为 " + student.getStudentName());
                    }
                    checkStudentOwnership(plan, studentOwnClaims);
                }
            }
            if (plan.studentId != null && plan.username != null
                && !seenPairs.add(plan.username + "#" + plan.studentId)) {
                plan.errors.add("与前面某行的账号+学员组合完全重复");
            }

            if (plan.accountKind == null) {
                plan.errors.add("账号类型不能为空");
            } else if (!KIND_PARENT.equals(plan.accountKind) && !KIND_STUDENT.equals(plan.accountKind)) {
                plan.errors.add("账号类型只能填 " + KIND_PARENT + " 或 " + KIND_STUDENT);
            } else if (roleIds.get(kindRoleCode(plan.accountKind)) == null) {
                plan.errors.add("系统未配置 " + plan.accountKind + " 对应角色，请先初始化角色");
            }
            if (plan.displayName == null) {
                plan.errors.add("账号姓名不能为空");
            }
            // 手机号只是账号上的联系字段：登录只认登录账号，激活码按账号ID发，
            // 小程序认家长走的是家长联系人表，所以没手机的学生不必硬凑一个号
            if (plan.phone != null && !PHONE.matcher(plan.phone).matches()) {
                plan.errors.add("手机号需为 11 位手机号");
            }
            if (plan.username != null && plan.username.length() > 64) {
                plan.errors.add("登录账号长度不能超过 64 个字符");
            }
        }
        return StudentAccountImportResult.of(plans.stream().map(Plan::toView).toList());
    }

    /**
     * 学员归属校验。只有"学生本人"账号才占 jp_student.user_id，
     * 家长账号天然可以绑多个学员，两个家长绑同一个学员也是正常诉求（爸爸和妈妈）。
     *
     * @param studentOwnClaims 出参，学员ID → 已在本次文件里声明学生本人归属的登录账号
     */
    private void checkStudentOwnership(Plan plan, Map<String, String> studentOwnClaims) {
        Long ownerId = plan.student.getUserId();
        if (ownerId != null && ownerId.equals(plan.userId)) {
            plan.notices.add("该学员已归属此账号");
        } else if (ownerId != null) {
            plan.notices.add("该学员已归属账号ID " + ownerId);
        }
        if (!KIND_STUDENT.equals(plan.accountKind)) {
            return;
        }
        String claimedBy = studentOwnClaims.put(String.valueOf(plan.studentId), plan.username);
        if (claimedBy != null && !claimedBy.equals(plan.username)) {
            plan.errors.add("该学员在本次导入里已被登录账号 " + claimedBy + " 声明为学生本人账号");
        }
        if (ownerId != null && !ownerId.equals(plan.userId)) {
            plan.errors.add("该学员已被其他账号归属");
        }
    }

    private Map<String, StudentEntity> loadStudents(Long institutionId, List<Plan> plans) {
        Set<String> numbers = plans.stream()
            .map(plan -> plan.studentNo)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (numbers.isEmpty()) {
            return Map.of();
        }
        return studentMapper.selectList(new LambdaQueryWrapper<StudentEntity>()
                .eq(StudentEntity::getInstitutionId, institutionId)
                .in(StudentEntity::getStudentNo, numbers))
            .stream()
            .collect(Collectors.toMap(StudentEntity::getStudentNo, student -> student, (first, second) -> first));
    }

    private Map<String, UserAccountEntity> loadAccounts(List<Plan> plans) {
        Set<String> usernames = plans.stream()
            .map(plan -> plan.username)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (usernames.isEmpty()) {
            return Map.of();
        }
        return userAccountMapper.selectList(new LambdaQueryWrapper<UserAccountEntity>()
                .in(UserAccountEntity::getUsername, usernames))
            .stream()
            .collect(Collectors.toMap(UserAccountEntity::getUsername, account -> account, (first, second) -> first));
    }

    private Map<String, Long> loadRoleIds() {
        return roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>()
                .in(RoleEntity::getRoleCode, List.of(
                    MiniappPortalResolver.ROLE_CODE_PARENT, MiniappPortalResolver.ROLE_CODE_STUDENT)))
            .stream()
            .collect(Collectors.toMap(RoleEntity::getRoleCode, RoleEntity::getId, (first, second) -> first));
    }

    // ------------------------------------------------------------------ 落库

    private StudentAccountImportResult persist(List<Plan> plans, Long institutionId,
                                               StudentAccountImportResult checked) {
        Map<String, Long> roleIds = loadRoleIds();
        Map<String, Long> resolvedUserIds = new HashMap<>();
        Map<Long, Set<Long>> boundStudents = new HashMap<>();
        Set<String> attachedRoles = new HashSet<>();
        int created = 0;
        int bound = 0;
        int skipped = 0;

        for (Plan plan : plans) {
            Long userId = resolvedUserIds.get(plan.username);
            if (userId == null) {
                if (plan.existing != null) {
                    userId = plan.existing.getId();
                } else {
                    // 待激活账号必须凭激活码自设密码，这里的随机口令只是占位，永不外发且激活时被覆盖
                    userId = systemUserService.createUser(new UserCreateRequest(institutionId, plan.username,
                        plan.displayName, plan.phone, randomPassword(), PENDING,
                        plan.remark == null ? "批量导入待激活账号" : plan.remark)).id();
                    created++;
                }
                resolvedUserIds.put(plan.username, userId);
                boundStudents.put(userId, loadBoundStudentIds(userId));
            }
            // 导入结果里的账号ID：前端按它勾选"本次新建的待激活账号"去批量发码
            plan.userId = userId;
            // 同一账号的多个学员行会重复走到这里，角色只查一次库、补一次
            if (attachedRoles.add(userId + "#" + kindRoleCode(plan.accountKind))) {
                attachRole(userId, kindRoleCode(plan.accountKind), roleIds);
            }
            if (bind(userId, plan, institutionId, boundStudents.get(userId))) {
                bound++;
            } else {
                skipped++;
            }
        }
        return new StudentAccountImportResult(checked.total(), checked.validCount(), checked.errorCount(),
            created, bound, skipped, plans.stream().map(Plan::toView).toList());
    }

    /** 已存在的绑定不再重插，返回该账号当前绑定的学员集合供后续行复用 */
    private Set<Long> loadBoundStudentIds(Long userId) {
        return userStudentRelMapper.selectList(new LambdaQueryWrapper<UserStudentRelEntity>()
                .eq(UserStudentRelEntity::getUserId, userId))
            .stream()
            .map(UserStudentRelEntity::getStudentId)
            .collect(Collectors.toCollection(HashSet::new));
    }

    private boolean bind(Long userId, Plan plan, Long institutionId, Set<Long> boundStudentIds) {
        if (!boundStudentIds.add(plan.studentId)) {
            return false;
        }
        UserStudentRelEntity relation = new UserStudentRelEntity();
        relation.setInstitutionId(institutionId);
        relation.setUserId(userId);
        relation.setStudentId(plan.studentId);
        try {
            userStudentRelMapper.insert(relation);
        } catch (DuplicateKeyException exception) {
            // 并发下另一批已绑过，唯一键兜住，不算失败
            return false;
        }
        if (KIND_STUDENT.equals(plan.accountKind) && plan.student.getUserId() == null) {
            StudentEntity owned = new StudentEntity();
            owned.setId(plan.studentId);
            owned.setUserId(userId);
            studentMapper.updateById(owned);
            plan.student.setUserId(userId);
        }
        return true;
    }

    private void attachRole(Long userId, String roleCode, Map<String, Long> roleIds) {
        Long roleId = roleIds.get(roleCode);
        if (roleId == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "角色未初始化：" + roleCode);
        }
        Long existing = userRoleRelMapper.selectCount(new LambdaQueryWrapper<UserRoleRelEntity>()
            .eq(UserRoleRelEntity::getUserId, userId)
            .eq(UserRoleRelEntity::getRoleId, roleId));
        if (existing != null && existing > 0) {
            return;
        }
        UserRoleRelEntity relation = new UserRoleRelEntity();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        try {
            userRoleRelMapper.insert(relation);
        } catch (DuplicateKeyException exception) {
            // 同一账号多行时可能并发补同一角色，已存在即可
        }
    }

    // ------------------------------------------------------------------ 工具

    private void assertInstitution(Long institutionId) {
        if (institutionId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "机构ID不能为空");
        }
        Long orgId = AuthContext.requireUser().orgId();
        if (orgId != null && !orgId.equals(institutionId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能导入到当前账号所属机构");
        }
    }

    private StudentAccountActivationView toView(UserAccountEntity account, String code,
                                                LocalDateTime expiresAt, String message) {
        return new StudentAccountActivationView(account.getId(), account.getUsername(),
            account.getDisplayName(), account.getPhone(), code, expiresAt, message);
    }

    private static String kindRoleCode(String accountKind) {
        return KIND_STUDENT.equals(accountKind)
            ? MiniappPortalResolver.ROLE_CODE_STUDENT
            : MiniappPortalResolver.ROLE_CODE_PARENT;
    }

    private String randomPassword() {
        StringBuilder builder = new StringBuilder(24);
        for (int index = 0; index < 24; index++) {
            builder.append(PASSWORD_ALPHABET[RANDOM.nextInt(PASSWORD_ALPHABET.length)]);
        }
        return builder.toString();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    /** 导入的一行，承载原始值、规范化后的值与校验结论 */
    private static final class Plan {

        private final int rowNum;
        private final String studentNo;
        private final String studentName;
        private final String accountKind;
        private final String displayName;
        private final String phone;
        private final String username;
        private final String remark;

        private StudentEntity student;
        private UserAccountEntity existing;
        private Long studentId;
        private Long userId;
        private final List<String> errors = new ArrayList<>();
        private final List<String> notices = new ArrayList<>();

        private Plan(int rowNum, String studentNo, String studentName, String accountKind,
                     String displayName, String phone, String username, String remark) {
            this.rowNum = rowNum;
            this.studentNo = studentNo;
            this.studentName = studentName;
            this.accountKind = accountKind;
            this.displayName = displayName;
            this.phone = phone;
            // 很多学生没有自己的手机号，所以登录账号按 手机号→学号 兜底，业务员一格都不用多填。
            // 学号只在机构内唯一、登录账号是全局唯一键，撞了别的机构时在 validate 里报出来让人改名。
            this.username = username != null ? username : (phone != null ? phone : studentNo);
            this.remark = remark;
        }

        static Plan from(StudentAccountImportRow row, int rowNum) {
            return new Plan(rowNum, text(row.getStudentNo()), text(row.getStudentName()),
                text(row.getAccountKind()), text(row.getDisplayName()), text(row.getPhone()),
                text(row.getUsername()), text(row.getRemark()));
        }

        boolean isEmpty() {
            return studentNo == null && studentName == null && accountKind == null
                && displayName == null && phone == null && username == null && remark == null;
        }

        StudentAccountImportRowView toView() {
            return new StudentAccountImportRowView(rowNum, studentNo,
                student == null ? null : student.getStudentName(), accountKind, displayName, username, phone,
                userId, studentId, errors.isEmpty(), join(errors), join(notices));
        }

        private static String join(List<String> values) {
            return values.isEmpty() ? null : String.join("；", values);
        }

        private static String text(String raw) {
            if (raw == null) {
                return null;
            }
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            // 纯数字串（如以 0 开头的学号）原样保留，只还原被 Excel 写成数值的形态
            return NUMERIC_FORM.matcher(trimmed).matches()
                ? new BigDecimal(trimmed).stripTrailingZeros().toPlainString()
                : trimmed;
        }
    }
}
