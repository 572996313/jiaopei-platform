package com.cornerstone.module.education.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.module.education.domain.entity.ScheduleRuleEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.dto.ScheduleSlotCell;
import com.cornerstone.module.education.mapper.ScheduleRuleMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.organization.domain.entity.ClassroomEntity;
import com.cornerstone.module.organization.domain.entity.TimeSlotEntity;
import com.cornerstone.module.organization.mapper.ClassroomMapper;
import com.cornerstone.module.organization.mapper.TimeSlotMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 排课规则的读取与匹配：把"班级/教师/教室在星期×节次网格上的禁排、必排约束"翻译成排课校验能直接用的结论。
 *
 * <p>格子键与 jp_schedule_plan.slots_json 同构（星期+时段ID），规则不绑学期：jp_time_slot 挂在机构+校区、
 * 不随学期重建，所以按时段ID绑定可以跨学期沿用；生效区间为空表示不限日期。
 */
@Component
public class ScheduleRuleSupport {

    public static final String RULE_FORBIDDEN = "forbidden";
    public static final String RULE_REQUIRED = "required";
    public static final String TARGET_CLASS = "class";
    public static final String TARGET_TEACHER = "teacher";
    public static final String TARGET_CLASSROOM = "classroom";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";

    private static final String[] WEEKDAY_LABELS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private final ScheduleRuleMapper ruleMapper;
    private final TeachingClassMapper classMapper;
    private final TeacherMapper teacherMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final ClassroomMapper classroomMapper;
    private final ObjectMapper objectMapper;

    public ScheduleRuleSupport(ScheduleRuleMapper ruleMapper, TeachingClassMapper classMapper,
                               TeacherMapper teacherMapper, TimeSlotMapper timeSlotMapper,
                               ClassroomMapper classroomMapper, ObjectMapper objectMapper) {
        this.ruleMapper = ruleMapper;
        this.classMapper = classMapper;
        this.teacherMapper = teacherMapper;
        this.timeSlotMapper = timeSlotMapper;
        this.classroomMapper = classroomMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 一次取齐班级、其教师与教室身上启用中的规则，供整批课次在内存里反复比对（批量展开逐条查库是 N+1）。
     * 班级可以是一批（整张课表跨多个班），比对到具体课次时再用 {@link RuleSet#scopedTo} 收窄归属。
     * 教室传进来是为了自动选室：这间房哪天哪节不对外，必须和班级、教师同一层被看见。
     */
    public RuleSet loadRuleSet(Long institutionId, Collection<Long> classIds, List<Long> teacherIds,
                               Collection<Long> classroomIds) {
        List<Long> ids = classIds == null ? List.of() : classIds.stream().filter(Objects::nonNull).distinct().toList();
        List<ScheduleRuleEntity> entities = ids.isEmpty()
            ? new ArrayList<>()
            : new ArrayList<>(ruleMapper.selectList(activeScope(institutionId)
                .eq(ScheduleRuleEntity::getTargetType, TARGET_CLASS)
                .in(ScheduleRuleEntity::getTargetId, ids)));
        List<Long> teacherIdList = teacherIds == null ? List.of() : teacherIds.stream().filter(Objects::nonNull).distinct().toList();
        if (!teacherIdList.isEmpty()) {
            entities.addAll(ruleMapper.selectList(activeScope(institutionId)
                .eq(ScheduleRuleEntity::getTargetType, TARGET_TEACHER)
                .in(ScheduleRuleEntity::getTargetId, teacherIdList)));
        }
        List<Long> classroomIdList = classroomIds == null
            ? List.of()
            : classroomIds.stream().filter(Objects::nonNull).distinct().toList();
        if (!classroomIdList.isEmpty()) {
            entities.addAll(ruleMapper.selectList(activeScope(institutionId)
                .eq(ScheduleRuleEntity::getTargetType, TARGET_CLASSROOM)
                .in(ScheduleRuleEntity::getTargetId, classroomIdList)));
        }
        List<Rule> rules = entities.stream()
            .map(entity -> new Rule(entity.getRuleType(), entity.getTargetType(), entity.getTargetId(),
                entity.getStartDate(), entity.getEndDate(), entity.getRemark(), readSlots(entity.getSlotsJson())))
            .filter(rule -> !rule.slots().isEmpty())
            .toList();
        return new RuleSet(rules, slotNames(rules), targetNames(rules));
    }

    private LambdaQueryWrapper<ScheduleRuleEntity> activeScope(Long institutionId) {
        return new LambdaQueryWrapper<ScheduleRuleEntity>()
            .eq(ScheduleRuleEntity::getInstitutionId, institutionId)
            .eq(ScheduleRuleEntity::getStatus, STATUS_ACTIVE);
    }

    private Map<Long, String> slotNames(List<Rule> rules) {
        Set<Long> slotIds = rules.stream()
            .flatMap(rule -> rule.slots().stream())
            .map(ScheduleSlotCell::timeSlotId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (slotIds.isEmpty()) {
            return Map.of();
        }
        return timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlotEntity>()
                .select(TimeSlotEntity::getId, TimeSlotEntity::getSlotName)
                .in(TimeSlotEntity::getId, slotIds))
            .stream()
            .collect(Collectors.toMap(TimeSlotEntity::getId, slot -> Objects.toString(slot.getSlotName(), "")));
    }

    /**
     * 规则对象（班级/教师/教室）的中文名，键为 {@code targetType#targetId}。
     * 提示语要给操作员看，19 位雪花 ID 等于没说，所以只在确实命中规则时才查名称。
     */
    private Map<String, String> targetNames(List<Rule> rules) {
        List<Long> classIds = rules.stream()
            .filter(rule -> TARGET_CLASS.equals(rule.targetType()))
            .map(Rule::targetId)
            .distinct()
            .toList();
        List<Long> teacherIds = rules.stream()
            .filter(rule -> TARGET_TEACHER.equals(rule.targetType()))
            .map(Rule::targetId)
            .distinct()
            .toList();
        List<Long> classroomIds = rules.stream()
            .filter(rule -> TARGET_CLASSROOM.equals(rule.targetType()))
            .map(Rule::targetId)
            .distinct()
            .toList();
        Map<String, String> names = new LinkedHashMap<>();
        if (!classIds.isEmpty()) {
            classMapper.selectList(new LambdaQueryWrapper<TeachingClassEntity>()
                    .select(TeachingClassEntity::getId, TeachingClassEntity::getClassName, TeachingClassEntity::getClassCode)
                    .in(TeachingClassEntity::getId, classIds))
                .forEach(item -> names.put(TARGET_CLASS + "#" + item.getId(),
                    displayName(item.getClassName(), item.getClassCode(), item.getId())));
        }
        if (!teacherIds.isEmpty()) {
            teacherMapper.selectList(new LambdaQueryWrapper<TeacherEntity>()
                    .select(TeacherEntity::getId, TeacherEntity::getTeacherName, TeacherEntity::getTeacherCode)
                    .in(TeacherEntity::getId, teacherIds))
                .forEach(item -> names.put(TARGET_TEACHER + "#" + item.getId(),
                    displayName(item.getTeacherName(), item.getTeacherCode(), item.getId())));
        }
        if (!classroomIds.isEmpty()) {
            classroomMapper.selectList(new LambdaQueryWrapper<ClassroomEntity>()
                    .select(ClassroomEntity::getId, ClassroomEntity::getClassroomName, ClassroomEntity::getClassroomCode)
                    .in(ClassroomEntity::getId, classroomIds))
                .forEach(item -> names.put(TARGET_CLASSROOM + "#" + item.getId(),
                    displayName(item.getClassroomName(), item.getClassroomCode(), item.getId())));
        }
        return names;
    }

    private static String displayName(String primary, String secondary, Long id) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return secondary == null || secondary.isBlank() ? "#" + id : secondary;
    }

    public List<ScheduleSlotCell> readSlots(String slotsJson) {
        if (slotsJson == null || slotsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(slotsJson, new TypeReference<List<ScheduleSlotCell>>() {
            });
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "排课规则格子数据解析失败");
        }
    }

    public String writeSlots(List<ScheduleSlotCell> slots) {
        try {
            return objectMapper.writeValueAsString(slots);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "排课规则格子序列化失败");
        }
    }

    public static String cellKey(Integer weekday, Long timeSlotId) {
        return weekday + "#" + timeSlotId;
    }

    /**
     * 规则集合与其涉及到的时段名称。空集合时各方法直接返回空说明，调用方无需判空。
     */
    public record RuleSet(List<Rule> rules, Map<Long, String> slotNames, Map<String, String> targetNames) {

        /**
         * 收窄到"管得着这一节课"的规则：班级规则要匹配本课次的班级，教室规则要匹配本课次的教室，
         * 教师规则要命中本课次的某一位授课教师。
         * 整张课表一次取规则落到具体课次时才分得清是谁被禁排，不然会把同事的禁排算到这节课头上。
         */
        public RuleSet scopedTo(Long classId, Collection<Long> teacherIds, Long classroomId) {
            List<Long> ids = teacherIds == null
                ? List.of()
                : teacherIds.stream().filter(Objects::nonNull).distinct().toList();
            List<Rule> scoped = rules.stream()
                .filter(rule -> switch (rule.targetType()) {
                    case TARGET_CLASS -> Objects.equals(rule.targetId(), classId);
                    case TARGET_CLASSROOM -> Objects.equals(rule.targetId(), classroomId);
                    default -> ids.contains(rule.targetId());
                })
                .toList();
            return scoped.size() == rules.size() ? this : new RuleSet(scoped, slotNames, targetNames);
        }

        /** 该课次是否踩在禁排格子上，返回给操作员看的中文说明。 */
        public List<String> forbiddenMessages(LocalDate lessonDate, Integer weekday, Long timeSlotId) {
            List<String> messages = new ArrayList<>();
            for (Rule rule : rules) {
                if (!RULE_FORBIDDEN.equals(rule.ruleType()) || !rule.appliesOn(lessonDate)) {
                    continue;
                }
                for (ScheduleSlotCell cell : rule.slots()) {
                    if (cell.weekday().equals(weekday) && cell.timeSlotId().equals(timeSlotId)) {
                        messages.add(targetLabel(rule) + weekdayLabel(cell.weekday()) + "·" + slotLabel(cell.timeSlotId())
                            + " 为禁排时段" + remarkSuffix(rule));
                    }
                }
            }
            return messages;
        }

        /**
         * 必排规则里没被这份计划的每周模式覆盖到的格子。
         * 只按"星期×节次"比对，不按周次逐周扫：某几周缺排属于完成度问题，归阶段C的完成度视图。
         * 同样不按 startDate/endDate 判定：必排约束的是每周节奏，不是某一天的课次。
         */
        public List<String> requiredGapMessages(Set<String> plannedCellKeys) {
            List<String> messages = new ArrayList<>();
            for (Rule rule : rules) {
                if (!RULE_REQUIRED.equals(rule.ruleType())) {
                    continue;
                }
                for (ScheduleSlotCell cell : rule.slots()) {
                    if (!plannedCellKeys.contains(cellKey(cell.weekday(), cell.timeSlotId()))) {
                        messages.add(targetLabel(rule) + "必排 " + weekdayLabel(cell.weekday()) + "·"
                            + slotLabel(cell.timeSlotId()) + " 未被本计划覆盖" + remarkSuffix(rule));
                    }
                }
            }
            return messages;
        }

        /** 规则对象的中文标签；名称查不到（对象已删）时退回 ID，至少还能定位到那条规则。 */
        private String targetLabel(Rule rule) {
            String kind = switch (rule.targetType()) {
                case TARGET_CLASS -> "班级";
                case TARGET_CLASSROOM -> "教室";
                default -> "教师";
            };
            String name = targetNames.get(rule.targetType() + "#" + rule.targetId());
            return (name == null || name.isBlank() ? kind + rule.targetId() : kind + "「" + name + "」") + " ";
        }

        /** 调用方已在前缀里给出"周一"，节次名自带同名前缀时剥掉，避免消息读成"周一·周一上1"。 */
        private String slotLabel(Long timeSlotId) {
            String name = slotNames.get(timeSlotId);
            if (name == null || name.isBlank()) {
                return "时段" + timeSlotId;
            }
            String stripped = name.replaceFirst("^周[一二三四五六日]", "");
            return stripped.isBlank() ? name : stripped;
        }

        private static String remarkSuffix(Rule rule) {
            return rule.remark() == null || rule.remark().isBlank() ? "" : "（" + rule.remark() + "）";
        }
    }

    public record Rule(String ruleType, String targetType, Long targetId, LocalDate startDate, LocalDate endDate,
                       String remark, List<ScheduleSlotCell> slots) {

        /** 生效区间为空表示不限日期。 */
        boolean appliesOn(LocalDate lessonDate) {
            if (lessonDate == null) {
                return true;
            }
            if (startDate != null && lessonDate.isBefore(startDate)) {
                return false;
            }
            return endDate == null || !lessonDate.isAfter(endDate);
        }
    }

    public static String weekdayLabel(Integer weekday) {
        if (weekday == null || weekday < 1 || weekday > 7) {
            return "星期?";
        }
        return WEEKDAY_LABELS[weekday - 1];
    }

    /** 规则类型的中文名：报错文案里不出现 forbidden / required 这种码值。 */
    public static String ruleTypeLabel(String ruleType) {
        if (RULE_FORBIDDEN.equals(ruleType)) {
            return "禁排";
        }
        return RULE_REQUIRED.equals(ruleType) ? "必排" : ruleType;
    }
}
