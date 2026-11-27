package com.cornerstone.module.education.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
import com.cornerstone.module.education.domain.entity.ClassTeacherEntity;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.domain.entity.UserStudentRelEntity;
import com.cornerstone.module.education.mapper.ClassStudentMapper;
import com.cornerstone.module.education.mapper.ClassTeacherMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.mapper.UserStudentRelMapper;
import com.cornerstone.module.education.message.domain.entity.MsgEventTypeEntity;
import com.cornerstone.module.education.message.domain.entity.MsgSubscriptionEntity;
import com.cornerstone.module.education.message.dto.MessageRecommendationView;
import com.cornerstone.module.education.message.dto.MessageSubscriptionRuleInput;
import com.cornerstone.module.education.message.dto.MessageSubscriptionRuleView;
import com.cornerstone.module.education.message.dto.MessageSubscriptionUpdateRequest;
import com.cornerstone.module.education.message.dto.MessageSubscriptionView;
import com.cornerstone.module.education.message.mapper.MsgEventTypeMapper;
import com.cornerstone.module.education.message.mapper.MsgSubscriptionMapper;
import com.cornerstone.module.education.message.support.MessageTypeRules;
import com.cornerstone.module.education.message.service.MessageSubscriptionService;
import com.cornerstone.module.education.service.support.DictValueValidator;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.mapper.CampusMapper;
import com.cornerstone.module.organization.service.support.CampusScopeGuard;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageSubscriptionServiceImpl implements MessageSubscriptionService {

    private static final String DICT_TYPE_CATEGORY = "message_category";
    private static final Map<String, String> LEVEL_LABELS = Map.of(
        "info", "提示",
        "warning", "警告",
        "critical", "严重");

    private final MsgSubscriptionMapper subscriptionMapper;
    private final MsgEventTypeMapper typeMapper;
    private final CampusMapper campusMapper;
    private final TeachingClassMapper classMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final ClassTeacherMapper classTeacherMapper;
    private final ClassStudentMapper classStudentMapper;
    private final UserStudentRelMapper userStudentRelMapper;
    private final DictValueValidator dictValueValidator;

    public MessageSubscriptionServiceImpl(MsgSubscriptionMapper subscriptionMapper,
                                          MsgEventTypeMapper typeMapper,
                                          CampusMapper campusMapper,
                                          TeachingClassMapper classMapper,
                                          StudentMapper studentMapper,
                                          TeacherMapper teacherMapper,
                                          ClassTeacherMapper classTeacherMapper,
                                          ClassStudentMapper classStudentMapper,
                                          UserStudentRelMapper userStudentRelMapper,
                                          DictValueValidator dictValueValidator) {
        this.subscriptionMapper = subscriptionMapper;
        this.typeMapper = typeMapper;
        this.campusMapper = campusMapper;
        this.classMapper = classMapper;
        this.studentMapper = studentMapper;
        this.teacherMapper = teacherMapper;
        this.classTeacherMapper = classTeacherMapper;
        this.classStudentMapper = classStudentMapper;
        this.userStudentRelMapper = userStudentRelMapper;
        this.dictValueValidator = dictValueValidator;
    }

    @Override
    public MessageSubscriptionView getMine() {
        Long userId = AuthContext.requireUser().userId();
        List<MsgSubscriptionEntity> rules = subscriptionMapper.selectList(
            new LambdaQueryWrapper<MsgSubscriptionEntity>()
                .eq(MsgSubscriptionEntity::getUserId, userId)
                .orderByAsc(MsgSubscriptionEntity::getRuleType)
                .orderByAsc(MsgSubscriptionEntity::getId));
        List<MessageSubscriptionRuleView> views = rules.stream()
            .map(rule -> new MessageSubscriptionRuleView(
                rule.getRuleType(), rule.getRuleValue(), labelOf(rule.getRuleType(), rule.getRuleValue()),
                Boolean.TRUE.equals(rule.getEnabled())))
            .toList();
        return new MessageSubscriptionView(views);
    }

    @Override
    @Transactional
    public void saveMine(MessageSubscriptionUpdateRequest request) {
        AuthenticatedUser user = AuthContext.requireUser();
        Map<String, MessageSubscriptionRuleInput> unique = new LinkedHashMap<>();
        for (MessageSubscriptionRuleInput rule : request.getRules()) {
            String ruleType = rule.getRuleType() == null ? "" : rule.getRuleType().trim();
            String ruleValue = rule.getRuleValue() == null ? "" : rule.getRuleValue().trim();
            if (!MessageTypeRules.RULE_TYPES.contains(ruleType)) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "不支持的订阅维度：" + rule.getRuleType());
            }
            if (ruleValue.isEmpty()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "订阅值不能为空");
            }
            validateRuleValue(user, ruleType, ruleValue);
            unique.put(ruleType + "|" + ruleValue, rule);
        }
        subscriptionMapper.physicalDeleteByUser(user.userId());
        for (Map.Entry<String, MessageSubscriptionRuleInput> entry : unique.entrySet()) {
            MessageSubscriptionRuleInput rule = entry.getValue();
            MsgSubscriptionEntity entity = new MsgSubscriptionEntity();
            entity.setUserId(user.userId());
            entity.setRuleType(rule.getRuleType().trim());
            entity.setRuleValue(rule.getRuleValue().trim());
            entity.setEnabled(!Boolean.FALSE.equals(rule.getEnabled()));
            entity.setCreatedBy(user.userId());
            entity.setUpdatedBy(user.userId());
            subscriptionMapper.insert(entity);
        }
    }

    /**
     * 订阅值只做「存在且属于本机构」校验。订阅本身只收窄不加宽，
     * 但放任订阅别人机构的 ID 会产生看似生效、实际永远为空的规则，运维排查成本高。
     */
    private void validateRuleValue(AuthenticatedUser user, String ruleType, String ruleValue) {
        Long institutionId = user.orgId();
        switch (ruleType) {
            case "type_code" -> {
                MsgEventTypeEntity type = typeMapper.selectOne(new LambdaQueryWrapper<MsgEventTypeEntity>()
                    .eq(MsgEventTypeEntity::getTypeCode, ruleValue)
                    .last("LIMIT 1"));
                if (type == null) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "事件类型不存在：" + ruleValue);
                }
            }
            case "category" -> dictValueValidator.validate(DICT_TYPE_CATEGORY, "订阅分类", ruleValue);
            case "level" -> {
                if (!MessageTypeRules.LEVELS.contains(ruleValue)) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "事件级别无效：" + ruleValue);
                }
            }
            case "campus" -> {
                CampusEntity campus = campusMapper.selectById(parseId(ruleValue, "校区"));
                if (campus == null || !Objects.equals(campus.getInstitutionId(), institutionId)) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "校区不存在或不属于当前机构");
                }
                CampusScopeGuard.assertCampus(campus.getId(), "校区");
            }
            case "class" -> {
                TeachingClassEntity teachingClass = classMapper.selectById(parseId(ruleValue, "班级"));
                if (teachingClass == null || !Objects.equals(teachingClass.getInstitutionId(), institutionId)) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "班级不存在或不属于当前机构");
                }
                CampusScopeGuard.assertCampus(teachingClass.getCampusId(), "班级");
            }
            case "student" -> {
                StudentEntity student = studentMapper.selectById(parseId(ruleValue, "学员"));
                if (student == null || !Objects.equals(student.getInstitutionId(), institutionId)) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "学员不存在或不属于当前机构");
                }
            }
            case "teacher" -> {
                TeacherEntity teacher = teacherMapper.selectById(parseId(ruleValue, "教师"));
                if (teacher == null || !Objects.equals(teacher.getInstitutionId(), institutionId)) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "教师不存在或不属于当前机构");
                }
                CampusScopeGuard.assertCampus(teacher.getCampusId(), "教师");
            }
            case "keyword" -> {
                if (ruleValue.length() > 128) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "订阅关键字不能超过 128 字符");
                }
            }
            default -> throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的订阅维度：" + ruleType);
        }
    }

    @Override
    public List<MessageRecommendationView> recommendations() {
        AuthenticatedUser user = AuthContext.requireUser();
        Set<String> subscribed = subscriptionMapper.selectList(new LambdaQueryWrapper<MsgSubscriptionEntity>()
                .eq(MsgSubscriptionEntity::getUserId, user.userId()))
            .stream()
            .map(rule -> rule.getRuleType() + "|" + rule.getRuleValue())
            .collect(Collectors.toSet());
        Map<String, Suggestion> suggestions = new LinkedHashMap<>();

        // 任课教师：自己的班 + 所属校区
        List<TeacherEntity> teachers = teacherMapper.selectList(new LambdaQueryWrapper<TeacherEntity>()
            .eq(TeacherEntity::getUserId, user.userId())
            .eq(TeacherEntity::getInstitutionId, user.orgId()));
        for (TeacherEntity teacher : teachers) {
            if (teacher.getCampusId() != null) {
                suggestions.putIfAbsent("campus|" + teacher.getCampusId(),
                    new Suggestion("campus", String.valueOf(teacher.getCampusId()),
                        campusName(teacher.getCampusId()), "你的所属校区"));
            }
            List<Long> classIds = classTeacherMapper.selectList(new LambdaQueryWrapper<ClassTeacherEntity>()
                    .eq(ClassTeacherEntity::getTeacherId, teacher.getId())
                    .eq(ClassTeacherEntity::getStatus, "active"))
                .stream()
                .map(ClassTeacherEntity::getClassId)
                .toList();
            for (TeachingClassEntity teachingClass : classesOf(classIds)) {
                suggestions.putIfAbsent("class|" + teachingClass.getId(),
                    new Suggestion("class", String.valueOf(teachingClass.getId()),
                        teachingClass.getClassName(), "你是该班任课教师"));
            }
        }

        // 家长/学生：绑定的学员 + 学员所在班级
        List<Long> studentIds = userStudentRelMapper.selectList(new LambdaQueryWrapper<UserStudentRelEntity>()
                .eq(UserStudentRelEntity::getUserId, user.userId()))
            .stream()
            .map(UserStudentRelEntity::getStudentId)
            .toList();
        for (StudentEntity student : studentsOf(studentIds)) {
            suggestions.putIfAbsent("student|" + student.getId(),
                new Suggestion("student", String.valueOf(student.getId()),
                    student.getStudentName(), "你绑定的学员"));
        }
        if (!studentIds.isEmpty()) {
            List<Long> classIds = classStudentMapper.selectList(new LambdaQueryWrapper<ClassStudentEntity>()
                    .in(ClassStudentEntity::getStudentId, studentIds)
                    .eq(ClassStudentEntity::getStatus, "active"))
                .stream()
                .map(ClassStudentEntity::getClassId)
                .toList();
            for (TeachingClassEntity teachingClass : classesOf(classIds)) {
                suggestions.putIfAbsent("class|" + teachingClass.getId(),
                    new Suggestion("class", String.valueOf(teachingClass.getId()),
                        teachingClass.getClassName(), "学员所在班级"));
            }
        }

        // 受限角色的数据范围校区（如校区教务）
        if (CampusScopeGuard.restricted() && user.dataScopeIds() != null) {
            for (Long campusId : user.dataScopeIds()) {
                suggestions.putIfAbsent("campus|" + campusId,
                    new Suggestion("campus", String.valueOf(campusId),
                        campusName(campusId), "你的数据范围校区"));
            }
        }

        List<MessageRecommendationView> views = new ArrayList<>();
        for (Suggestion suggestion : suggestions.values()) {
            views.add(new MessageRecommendationView(
                suggestion.ruleType(), suggestion.ruleValue(), suggestion.label(), suggestion.reason(),
                subscribed.contains(suggestion.ruleType() + "|" + suggestion.ruleValue())));
        }
        return views;
    }

    private List<TeachingClassEntity> classesOf(List<Long> classIds) {
        List<Long> ids = classIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return classMapper.selectBatchIds(ids);
    }

    private List<StudentEntity> studentsOf(List<Long> studentIds) {
        List<Long> ids = studentIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return studentMapper.selectBatchIds(ids);
    }

    private String campusName(Long campusId) {
        CampusEntity campus = campusMapper.selectById(campusId);
        return campus == null ? String.valueOf(campusId) : campus.getCampusName();
    }

    private String labelOf(String ruleType, String ruleValue) {
        return switch (ruleType) {
            case "type_code" -> typeMapper.selectList(new LambdaQueryWrapper<MsgEventTypeEntity>()
                    .eq(MsgEventTypeEntity::getTypeCode, ruleValue))
                .stream()
                .map(MsgEventTypeEntity::getTypeName)
                .findFirst()
                .orElse(ruleValue);
            case "category" -> dictValueValidator.labels(DICT_TYPE_CATEGORY)
                .getOrDefault(ruleValue, ruleValue);
            case "level" -> LEVEL_LABELS.getOrDefault(ruleValue, ruleValue);
            case "campus" -> {
                Long campusId = parseLong(ruleValue);
                yield campusId == null ? ruleValue : campusName(campusId);
            }
            case "class" -> {
                Long classId = parseLong(ruleValue);
                TeachingClassEntity teachingClass = classId == null ? null : classMapper.selectById(classId);
                yield teachingClass == null ? ruleValue : teachingClass.getClassName();
            }
            case "student" -> {
                Long studentId = parseLong(ruleValue);
                StudentEntity student = studentId == null ? null : studentMapper.selectById(studentId);
                yield student == null ? ruleValue : student.getStudentName();
            }
            case "teacher" -> {
                Long teacherId = parseLong(ruleValue);
                TeacherEntity teacher = teacherId == null ? null : teacherMapper.selectById(teacherId);
                yield teacher == null ? ruleValue : teacher.getTeacherName();
            }
            default -> ruleValue;
        };
    }

    private Long parseId(String value, String subject) {
        Long id = parseLong(value);
        if (id == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "订阅的" + subject + "ID不合法：" + value);
        }
        return id;
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record Suggestion(String ruleType, String ruleValue, String label, String reason) {
    }
}
