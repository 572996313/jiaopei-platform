package com.cornerstone.core.auth.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.domain.RoleEntity;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.dto.UserAccountImportResult;
import com.cornerstone.core.auth.dto.UserAccountImportRow;
import com.cornerstone.core.auth.dto.UserAccountImportRowView;
import com.cornerstone.core.auth.dto.UserCreateRequest;
import com.cornerstone.core.auth.dto.UserRoleUpdateRequest;
import com.cornerstone.core.auth.dto.UserView;
import com.cornerstone.core.auth.mapper.RoleMapper;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.auth.service.PasswordValidator;
import com.cornerstone.core.auth.service.SystemUserService;
import com.cornerstone.core.auth.service.UserAccountImportService;
import com.cornerstone.core.auth.service.support.UserAccountImportTemplate;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户账号批量导入：下载模板 → 预览校验 → 确认新建。三条不看这里就读不出来的口径：
 *
 * <ol>
 *   <li><b>机构只认登录态。</b>归属机构一律取 {@code AuthContext.requireUser().orgId()}，模板没有机构列、
 *       接口也不收 institutionId 参数。批量入口不能照 {@code SystemUserServiceImpl} 那样让调用方报机构，
 *       一处能指定机构就等于能往别人机构里整批写账号。</li>
 *   <li><b>初始口令必填。</b>{@code SystemUserServiceImpl#initialPassword} 对空值有一个公共兜底口令
 *       （{@code Init@123456}）。单个建号时那是便利，批量时等于给一整批人发同一个号且全程无提示，
 *       所以空口令一律判错，不复用那条兜底。</li>
 *   <li><b>角色按编码解析。</b>雪花角色 ID 不该让人手抄进 Excel，编码查不到就报错并列出全部可用编码。
 *       {@code sys_role} 没有机构列（角色是全局的），所以这里只保证编码存在，不判断"本机构能不能拥有该角色"。</li>
 * </ol>
 *
 * <p>登录账号是全系统唯一键（{@code uk_jp_user_username}），所以同号要分两种结论：本机构已有→跳过新建，
 * 他机构占用→报错换号。合成一句"已存在"会让操作员以为账号在自己名下了。</p>
 */
@Service
public class UserAccountImportServiceImpl implements UserAccountImportService {

    private static final String ACTIVE = "active";
    private static final String DISABLED = "disabled";
    private static final int MAX_ROWS = 500;
    private static final int USERNAME_MAX = 64;
    private static final int DISPLAY_NAME_MAX = 128;
    private static final int PHONE_MAX = 32;
    private static final int REMARK_MAX = 500;
    private static final Pattern PHONE = Pattern.compile("^1\\d{10}$");
    /** Excel 把账号/手机号存成数值时，EasyExcel 会回科学计数法或带 .0 的形态，需要先还原成整串数字 */
    private static final Pattern NUMERIC_FORM = Pattern.compile("^\\d+(\\.\\d*)?[Ee][-+]?\\d+$|^\\d+\\.0+$");

    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;
    private final SystemUserService systemUserService;
    private final PasswordValidator passwordValidator;

    public UserAccountImportServiceImpl(UserAccountMapper userAccountMapper, RoleMapper roleMapper,
                                        SystemUserService systemUserService, PasswordValidator passwordValidator) {
        this.userAccountMapper = userAccountMapper;
        this.roleMapper = roleMapper;
        this.systemUserService = systemUserService;
        this.passwordValidator = passwordValidator;
    }

    @Override
    public byte[] buildTemplate() {
        return UserAccountImportTemplate.build();
    }

    @Override
    public UserAccountImportResult preview(MultipartFile file) {
        return validate(parse(file), requireInstitution());
    }

    @Override
    @Transactional
    public UserAccountImportResult commit(MultipartFile file) {
        Long institutionId = requireInstitution();
        List<Plan> plans = parse(file);
        UserAccountImportResult checked = validate(plans, institutionId);
        if (checked.errorCount() > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "存在 " + checked.errorCount() + " 行校验不通过，整批未导入，请修正后重新上传");
        }
        return persist(institutionId, plans, checked);
    }

    /** 归属机构只从登录态取；没绑机构的账号没有可写入的目标机构，直接拒绝而不是让他传参 */
    private Long requireInstitution() {
        AuthenticatedUser current = AuthContext.requireUser();
        if (current.orgId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前登录账号未绑定机构，无法批量导入账号");
        }
        return current.orgId();
    }

    // ------------------------------------------------------------------ 解析

    private List<Plan> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择要导入的 xlsx 文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只支持 xlsx 格式，请先下载模板填写");
        }
        List<UserAccountImportRow> rows;
        try (InputStream in = file.getInputStream()) {
            rows = EasyExcel.read(in).head(UserAccountImportRow.class).sheet().doReadSync();
        } catch (IOException | RuntimeException exception) {
            // 文件损坏、被加密、表头行缺失都汇到这里，给可操作的提示而不是 500
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "文件解析失败：" + rootMessage(exception) + "。请确认使用系统模板且内容在第一个工作表");
        }
        List<Plan> plans = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            Plan plan = Plan.of(rows.get(index), index + 2);
            if (!plan.empty) {
                plans.add(plan);
            }
        }
        if (plans.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "未读到有效数据行，请填写模板后重新上传");
        }
        if (plans.size() > MAX_ROWS) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "单次导入不超过 " + MAX_ROWS + " 行");
        }
        return plans;
    }

    // ------------------------------------------------------------------ 校验

    private UserAccountImportResult validate(List<Plan> plans, Long institutionId) {
        Map<String, UserAccountEntity> accountByUsername = loadAccounts(plans);
        Map<String, Long> roleIdsByCode = loadRoleIds();
        Set<String> seenUsernames = new LinkedHashSet<>();

        for (Plan plan : plans) {
            checkUsername(plan, seenUsernames);
            // 账号列可能整列没填，空 Map 的 get(null) 会直接 NPE，这里先判再查
            checkExistingAccount(plan,
                plan.username == null ? null : accountByUsername.get(plan.username), institutionId);
            checkDisplayName(plan);
            checkPassword(plan);
            checkPhone(plan);
            checkRoleCodes(plan, roleIdsByCode);
            checkStatus(plan);
            checkRemark(plan);
        }
        return UserAccountImportResult.of(plans.stream().map(Plan::toView).toList());
    }

    private void checkUsername(Plan plan, Set<String> seenUsernames) {
        if (plan.username == null) {
            plan.errors.add("登录账号不能为空");
            return;
        }
        if (plan.username.length() > USERNAME_MAX) {
            plan.errors.add("登录账号长度不能超过 " + USERNAME_MAX + " 个字符");
        }
        if (!seenUsernames.add(plan.username)) {
            plan.errors.add("登录账号 " + plan.username + " 在本次文件里出现多行");
        }
    }

    private void checkExistingAccount(Plan plan, UserAccountEntity existing, Long institutionId) {
        if (existing == null) {
            return;
        }
        if (Objects.equals(existing.getInstitutionId(), institutionId)) {
            plan.existingUserId = existing.getId();
            plan.notices.add("该登录账号在本机构已存在，本次跳过新建");
        } else {
            // 只说"被其他机构占用"，不报对方机构名：那等于让任何机构拿字典去探测别家账号
            plan.errors.add("登录账号 " + plan.username + " 已被其他机构使用，请换一个");
        }
    }

    private void checkDisplayName(Plan plan) {
        if (plan.displayName == null) {
            plan.errors.add("显示名称不能为空");
        } else if (plan.displayName.length() > DISPLAY_NAME_MAX) {
            plan.errors.add("显示名称长度不能超过 " + DISPLAY_NAME_MAX + " 个字符");
        }
    }

    /** 空口令必须报错而不是走 {@code SystemUserServiceImpl} 的公共兜底口令，理由见类注释第 2 条 */
    private void checkPassword(Plan plan) {
        if (plan.initialPassword == null) {
            plan.errors.add("初始口令不能为空（留空不会被补成默认口令）");
        } else if (!passwordValidator.meetsRequirements(plan.initialPassword)) {
            plan.errors.add("初始口令不满足系统的密码策略（长度与复杂度），请换一个");
        }
    }

    private void checkPhone(Plan plan) {
        if (plan.phone == null) {
            return;
        }
        if (plan.phone.length() > PHONE_MAX) {
            plan.errors.add("手机号长度不能超过 " + PHONE_MAX + " 个字符");
        } else if (!PHONE.matcher(plan.phone).matches()) {
            plan.errors.add("手机号需为 11 位手机号；登录只认登录账号，没有手机号可以留空");
        }
    }

    private void checkRoleCodes(Plan plan, Map<String, Long> roleIdsByCode) {
        if (plan.roleCodes == null) {
            plan.errors.add("角色编码不能为空");
            return;
        }
        List<String> unknown = new ArrayList<>();
        List<Long> resolved = new ArrayList<>();
        for (String code : splitCodes(plan.roleCodes)) {
            Long roleId = roleIdsByCode.get(code);
            if (roleId == null) {
                unknown.add(code);
            } else if (!resolved.contains(roleId)) {
                resolved.add(roleId);
            }
        }
        if (!unknown.isEmpty()) {
            plan.errors.add("未找到角色编码 " + String.join("、", unknown)
                + "；可用编码：" + String.join("、", roleIdsByCode.keySet()));
        }
        plan.roleIds = List.copyOf(resolved);
    }

    private void checkStatus(Plan plan) {
        if (plan.status == null) {
            plan.status = ACTIVE;
            return;
        }
        if (!ACTIVE.equals(plan.status) && !DISABLED.equals(plan.status)) {
            plan.errors.add("状态只能填 " + ACTIVE + "（启用）或 " + DISABLED + "（停用），留空按启用");
        }
    }

    private void checkRemark(Plan plan) {
        if (plan.remark != null && plan.remark.length() > REMARK_MAX) {
            plan.errors.add("备注长度不能超过 " + REMARK_MAX + " 个字符");
        }
    }

    private Map<String, UserAccountEntity> loadAccounts(List<Plan> plans) {
        Set<String> usernames = plans.stream()
            .map(plan -> plan.username)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (usernames.isEmpty()) {
            return Map.of();
        }
        // 故意不带机构条件：登录账号的唯一键是全系统范围的，按机构过滤就看不见别人占的同号，
        // 那样只会在落库时撞成一条 DuplicateKeyException，客户拿不到"换个号"这个可读结论
        return userAccountMapper.selectList(new LambdaQueryWrapper<UserAccountEntity>()
                .in(UserAccountEntity::getUsername, usernames))
            .stream()
            .collect(Collectors.toMap(UserAccountEntity::getUsername, account -> account, (first, second) -> first));
    }

    private Map<String, Long> loadRoleIds() {
        return roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>()
                .orderByAsc(RoleEntity::getRoleCode))
            .stream()
            .collect(Collectors.toMap(RoleEntity::getRoleCode, RoleEntity::getId,
                (first, second) -> first, LinkedHashMap::new));
    }

    // ------------------------------------------------------------------ 落库

    private UserAccountImportResult persist(Long institutionId, List<Plan> plans, UserAccountImportResult checked) {
        List<UserAccountImportRowView> views = new ArrayList<>(plans.size());
        int created = 0;
        int skipped = 0;
        for (int index = 0; index < plans.size(); index++) {
            Plan plan = plans.get(index);
            UserAccountImportRowView previewed = checked.rows().get(index);
            if (plan.existingUserId != null) {
                skipped++;
                views.add(previewed);
                continue;
            }
            UserView user = systemUserService.createUser(new UserCreateRequest(
                institutionId, plan.username, plan.displayName, plan.phone,
                plan.initialPassword, plan.status, plan.remark));
            // 角色走既有入口而不是自己插关联表：那里带着"改授权必须让旧会话失效"的契约
            if (!plan.roleIds.isEmpty()) {
                systemUserService.updateRoles(user.id(), new UserRoleUpdateRequest(plan.roleIds));
            }
            created++;
            views.add(new UserAccountImportRowView(previewed.rowNum(), previewed.username(),
                previewed.displayName(), previewed.phone(), previewed.roleCodes(), previewed.status(),
                previewed.valid(), previewed.error(), previewed.notice(), user.id()));
        }
        return new UserAccountImportResult(checked.total(), checked.validCount(),
            checked.errorCount(), created, skipped, views);
    }

    // ------------------------------------------------------------------ 工具

    private List<String> splitCodes(String raw) {
        List<String> codes = new ArrayList<>();
        for (String part : raw.split("[,，]")) {
            String code = part.trim();
            if (!code.isEmpty() && !codes.contains(code)) {
                codes.add(code);
            }
        }
        return codes;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    /** 取空白并把被 Excel 存成数值的串还原回数字文本；整串空白归 null。口令列不走这个方法 */
    private static String text(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (NUMERIC_FORM.matcher(trimmed).matches()) {
            return new BigDecimal(trimmed).toBigInteger().toString();
        }
        return trimmed;
    }

    /** 口令只去两侧空白：中间的空格是口令的一部分，去掉了本人照着文件输就登不进去 */
    private static String passwordText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 一行 Excel 在校验期间的全部中间结论：错误/提示累加、角色解析结果、同机构已有账号的 ID */
    private static final class Plan {

        private int rowNum;
        private String username;
        private String displayName;
        private String initialPassword;
        private String phone;
        private String roleCodes;
        private String status;
        private String remark;
        private boolean empty;
        private final List<String> errors = new ArrayList<>();
        private final List<String> notices = new ArrayList<>();
        private List<Long> roleIds = List.of();
        private Long existingUserId;

        static Plan of(UserAccountImportRow row, int rowNum) {
            Plan plan = new Plan();
            plan.rowNum = rowNum;
            plan.username = text(row.getUsername());
            plan.displayName = text(row.getDisplayName());
            plan.initialPassword = passwordText(row.getInitialPassword());
            plan.phone = text(row.getPhone());
            plan.roleCodes = text(row.getRoleCodes());
            plan.status = text(row.getStatus());
            plan.remark = text(row.getRemark());
            plan.empty = plan.username == null && plan.displayName == null && plan.initialPassword == null
                && plan.phone == null && plan.roleCodes == null && plan.status == null && plan.remark == null;
            return plan;
        }

        UserAccountImportRowView toView() {
            return new UserAccountImportRowView(rowNum, username, displayName, phone, roleCodes,
                status == null ? ACTIVE : status, errors.isEmpty(),
                errors.isEmpty() ? null : String.join("；", errors),
                notices.isEmpty() ? null : String.join("；", notices),
                existingUserId);
        }
    }
}
