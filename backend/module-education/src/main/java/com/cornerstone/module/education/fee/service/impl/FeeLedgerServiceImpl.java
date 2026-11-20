package com.cornerstone.module.education.fee.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.tenant.domain.InstitutionEntity;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.StudentParentEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeAttendanceMonthEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeItemEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeLedgerEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeLedgerItemEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeMonthlyEntity;
import com.cornerstone.module.education.fee.domain.entity.FeePaymentEntity;
import com.cornerstone.module.education.fee.dto.FeeAttendanceView;
import com.cornerstone.module.education.fee.dto.FeeLedgerCreateRequest;
import com.cornerstone.module.education.fee.dto.FeeLedgerDetailExportRow;
import com.cornerstone.module.education.fee.dto.FeeLedgerDetailView;
import com.cornerstone.module.education.fee.dto.FeeLedgerGenerateRequest;
import com.cornerstone.module.education.fee.dto.FeeLedgerGenerateResult;
import com.cornerstone.module.education.fee.dto.FeeLedgerItemRequest;
import com.cornerstone.module.education.fee.dto.FeeLedgerItemView;
import com.cornerstone.module.education.fee.dto.FeeLedgerPageQuery;
import com.cornerstone.module.education.fee.dto.FeeLedgerRowView;
import com.cornerstone.module.education.fee.dto.FeeLedgerUpdateRequest;
import com.cornerstone.module.education.fee.dto.FeeMonthlyExportRow;
import com.cornerstone.module.education.fee.dto.FeeMonthlyView;
import com.cornerstone.module.education.fee.dto.FeePaymentView;
import com.cornerstone.module.education.fee.dto.FeeRecalculateRequest;
import com.cornerstone.module.education.fee.mapper.FeeAttendanceMonthMapper;
import com.cornerstone.module.education.fee.mapper.FeeItemMapper;
import com.cornerstone.module.education.fee.mapper.FeeLedgerItemMapper;
import com.cornerstone.module.education.fee.mapper.FeeLedgerMapper;
import com.cornerstone.module.education.fee.mapper.FeeMonthlyMapper;
import com.cornerstone.module.education.fee.mapper.FeePaymentMapper;
import com.cornerstone.module.education.fee.service.FeeLedgerService;
import com.cornerstone.module.education.fee.support.FeeBillingEngine;
import com.cornerstone.module.education.fee.support.FeeLedgerCalculator;
import com.cornerstone.module.education.fee.support.FeeLedgerGuard;
import com.cornerstone.module.education.fee.support.FeeMatrixExportBuilder;
import com.cornerstone.module.education.fee.support.FeePriceResolver;
import com.cornerstone.module.education.mapper.ClassStudentMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.StudentParentMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.domain.entity.TermEntity;
import com.cornerstone.module.organization.mapper.CampusMapper;
import com.cornerstone.module.organization.mapper.TermMapper;
import com.cornerstone.module.organization.service.support.CampusScopeGuard;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeeLedgerServiceImpl implements FeeLedgerService {

    private static final int MAX_EXPORT_ROWS = 5000;
    private static final Set<String> LEDGER_STATUSES = Set.of("active", "settled");
    private static final String MONTHLY = "monthly";
    private static final String DAILY = "daily";

    private final FeeLedgerMapper feeLedgerMapper;
    private final FeeLedgerItemMapper feeLedgerItemMapper;
    private final FeeMonthlyMapper feeMonthlyMapper;
    private final FeeAttendanceMonthMapper feeAttendanceMonthMapper;
    private final FeePaymentMapper feePaymentMapper;
    private final FeeItemMapper feeItemMapper;
    private final FeePriceResolver feePriceResolver;
    private final FeeLedgerGuard feeLedgerGuard;
    private final ClassStudentMapper classStudentMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final TermMapper termMapper;
    private final StudentMapper studentMapper;
    private final StudentParentMapper studentParentMapper;
    private final CampusMapper campusMapper;
    private final InstitutionMapper institutionMapper;

    public FeeLedgerServiceImpl(FeeLedgerMapper feeLedgerMapper, FeeLedgerItemMapper feeLedgerItemMapper,
                                FeeMonthlyMapper feeMonthlyMapper,
                                FeeAttendanceMonthMapper feeAttendanceMonthMapper,
                                FeePaymentMapper feePaymentMapper, FeeItemMapper feeItemMapper,
                                FeePriceResolver feePriceResolver, FeeLedgerGuard feeLedgerGuard,
                                ClassStudentMapper classStudentMapper, TeachingClassMapper teachingClassMapper,
                                TermMapper termMapper, StudentMapper studentMapper,
                                StudentParentMapper studentParentMapper, CampusMapper campusMapper,
                                InstitutionMapper institutionMapper) {
        this.feeLedgerMapper = feeLedgerMapper;
        this.feeLedgerItemMapper = feeLedgerItemMapper;
        this.feeMonthlyMapper = feeMonthlyMapper;
        this.feeAttendanceMonthMapper = feeAttendanceMonthMapper;
        this.feePaymentMapper = feePaymentMapper;
        this.feeItemMapper = feeItemMapper;
        this.feePriceResolver = feePriceResolver;
        this.feeLedgerGuard = feeLedgerGuard;
        this.classStudentMapper = classStudentMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.termMapper = termMapper;
        this.studentMapper = studentMapper;
        this.studentParentMapper = studentParentMapper;
        this.campusMapper = campusMapper;
        this.institutionMapper = institutionMapper;
    }

    @Override
    public PageResult<FeeLedgerRowView> pageLedgers(FeeLedgerPageQuery query) {
        Long institutionId = feeLedgerGuard.resolveInstitution(query.getInstitutionId());
        LambdaQueryWrapper<FeeLedgerEntity> wrapper = buildWrapper(query, institutionId);
        applyDefaultOrder(wrapper);
        Page<FeeLedgerEntity> page = feeLedgerMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page, toRowViews(page.getRecords()));
    }

    @Override
    public FeeLedgerDetailView getLedger(Long id) {
        return buildDetail(feeLedgerGuard.requireLedger(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeeLedgerDetailView createLedger(FeeLedgerCreateRequest request) {
        Long institutionId = feeLedgerGuard.resolveInstitution(request.institutionId());
        TeachingClassEntity clazz = feeLedgerGuard.requireClass(request.classId(), institutionId);
        TermEntity term = feeLedgerGuard.requireTerm(request.termId(), institutionId);
        StudentEntity student = feeLedgerGuard.requireStudent(request.studentId(), institutionId);

        Long exists = feeLedgerMapper.selectCount(new LambdaQueryWrapper<FeeLedgerEntity>()
            .eq(FeeLedgerEntity::getInstitutionId, institutionId)
            .eq(FeeLedgerEntity::getTermId, term.getId())
            .eq(FeeLedgerEntity::getClassId, clazz.getId())
            .eq(FeeLedgerEntity::getStudentId, student.getId()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "学员「" + student.getStudentName() + "」在该班级本学期已有收费台账");
        }

        FeeLedgerEntity ledger = new FeeLedgerEntity();
        ledger.setInstitutionId(institutionId);
        ledger.setCampusId(clazz.getCampusId());
        ledger.setClassId(clazz.getId());
        ledger.setTermId(term.getId());
        ledger.setStudentId(student.getId());
        ledger.setStatus(requireStatus(request.status()));
        ledger.setRemark(normalizeToNull(request.remark()));
        ledger.setDeleted(Boolean.FALSE);
        try {
            feeLedgerMapper.insert(ledger);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "学员「" + student.getStudentName() + "」在该班级本学期已有收费台账");
        }

        applyItems(ledger, term, request.items(), true);
        rebuildMonthly(ledger, term);
        return buildDetail(ledger);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeeLedgerDetailView updateLedger(Long id, FeeLedgerUpdateRequest request) {
        FeeLedgerEntity ledger = feeLedgerGuard.requireLedger(id);
        if (request.status() != null && !request.status().isBlank()) {
            ledger.setStatus(requireStatus(request.status()));
        }
        if (request.remark() != null) {
            ledger.setRemark(normalizeToNull(request.remark()));
        }
        feeLedgerMapper.updateById(ledger);
        if (request.items() != null) {
            TermEntity term = requireTermEntity(ledger.getTermId());
            applyItems(ledger, term, request.items(), false);
            rebuildMonthly(ledger, term);
        }
        return buildDetail(ledger);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLedger(Long id) {
        FeeLedgerEntity ledger = feeLedgerGuard.requireLedger(id);
        Long paymentCount = feePaymentMapper.selectCount(new LambdaQueryWrapper<FeePaymentEntity>()
            .eq(FeePaymentEntity::getLedgerId, id));
        if (paymentCount != null && paymentCount > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "该台账已有收款流水，不能删除；请先删除收款记录或把台账改为「已结清」");
        }
        feeMonthlyMapper.deletePhysicallyByLedgerId(id);
        feeAttendanceMonthMapper.deletePhysicallyByLedgerId(id);
        feeLedgerItemMapper.deletePhysicallyByLedgerId(id);
        feeLedgerMapper.physicalDeleteById(ledger.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeeLedgerGenerateResult generate(FeeLedgerGenerateRequest request) {
        Long institutionId = feeLedgerGuard.resolveInstitution(request.institutionId());
        TeachingClassEntity clazz = feeLedgerGuard.requireClass(request.classId(), institutionId);
        TermEntity term = feeLedgerGuard.requireTerm(request.termId(), institutionId);

        List<ClassStudentEntity> roster = classStudentMapper.selectList(
            new LambdaQueryWrapper<ClassStudentEntity>()
                .eq(ClassStudentEntity::getClassId, clazz.getId())
                .eq(ClassStudentEntity::getStatus, "active"));
        if (roster.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "班级「" + clazz.getClassName() + "」名册里没有在读学员，无法生成台账");
        }
        Set<Long> existingStudents = feeLedgerMapper.selectList(new LambdaQueryWrapper<FeeLedgerEntity>()
                .eq(FeeLedgerEntity::getInstitutionId, institutionId)
                .eq(FeeLedgerEntity::getTermId, term.getId())
                .eq(FeeLedgerEntity::getClassId, clazz.getId()))
            .stream().map(FeeLedgerEntity::getStudentId).collect(Collectors.toSet());

        List<FeeLedgerItemRequest> defaultItems = defaultItemRequests(institutionId);
        int created = 0;
        int skipped = 0;
        for (ClassStudentEntity member : roster) {
            if (existingStudents.contains(member.getStudentId())) {
                skipped++;
                continue;
            }
            FeeLedgerEntity ledger = new FeeLedgerEntity();
            ledger.setInstitutionId(institutionId);
            ledger.setCampusId(clazz.getCampusId());
            ledger.setClassId(clazz.getId());
            ledger.setTermId(term.getId());
            ledger.setStudentId(member.getStudentId());
            ledger.setStatus("active");
            ledger.setDeleted(Boolean.FALSE);
            try {
                feeLedgerMapper.insert(ledger);
            } catch (DuplicateKeyException exception) {
                skipped++;
                continue;
            }
            applyItems(ledger, term, defaultItems, true);
            rebuildMonthly(ledger, term);
            created++;
        }
        return new FeeLedgerGenerateResult(created, skipped, defaultItems.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeeLedgerDetailView resolvePrices(Long id) {
        FeeLedgerEntity ledger = feeLedgerGuard.requireLedger(id);
        TermEntity term = requireTermEntity(ledger.getTermId());
        List<FeeLedgerItemEntity> items = feeLedgerItemMapper.selectList(
            new LambdaQueryWrapper<FeeLedgerItemEntity>().eq(FeeLedgerItemEntity::getLedgerId, id));
        for (FeeLedgerItemEntity item : items) {
            FeePriceResolver.ResolvedPrice price = feePriceResolver.resolve(
                ledger.getInstitutionId(), item.getFeeItemId(),
                ledger.getCampusId(), ledger.getClassId(), ledger.getTermId(), ledger.getStudentId());
            item.setMonthlyStandard(FeeBillingEngine.money(price.monthlyPrice()));
            item.setDailyRate(FeeBillingEngine.money(price.dailyPrice()));
            item.setPriceSource(price.sourceFor(item.getBillingMode()));
            item.setPriceId(price.priceIdFor(item.getBillingMode()));
            feeLedgerItemMapper.updateById(item);
        }
        rebuildMonthly(ledger, term);
        return buildDetail(ledger);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int recalculate(FeeRecalculateRequest request) {
        List<FeeLedgerEntity> ledgers = resolveRecalculateLedgers(request);
        for (FeeLedgerEntity ledger : ledgers) {
            TermEntity term = termMapper.selectById(ledger.getTermId());
            if (term != null) {
                rebuildMonthly(ledger, term);
            }
        }
        return ledgers.size();
    }

    @Override
    public byte[] exportMatrix(FeeLedgerPageQuery query) {
        List<FeeLedgerEntity> ledgers = loadExportLedgers(query);
        Map<Long, FeeLedgerDetailView> detailsByLedger = ledgers.stream()
            .collect(Collectors.toMap(FeeLedgerEntity::getId, this::buildDetail,
                (first, second) -> first, LinkedHashMap::new));
        // 同一班级一个学期的台账共享表头；导出通常已按学期/班级筛过，取第一份做标题
        FeeLedgerDetailView first = detailsByLedger.values().iterator().next();
        if (first.termStartDate() == null || first.termEndDate() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "所选台账的学期没有起止日期，无法按月份生成矩阵表；请先到学期页补齐日期");
        }
        List<YearMonth> months = FeeBillingEngine.termMonths(first.termStartDate(), first.termEndDate());

        List<FeeMatrixExportBuilder.ItemRef> itemRefs = resolveMatrixItems(
            first.institutionId(), detailsByLedger.values());
        Map<Long, StudentParentEntity> parents = loadParents(detailsByLedger.values());
        List<FeeMatrixExportBuilder.StudentBlock> blocks = new ArrayList<>();
        for (FeeLedgerDetailView detail : detailsByLedger.values()) {
            StudentParentEntity parent = parents.get(detail.studentId());
            BigDecimal attendanceDays = detail.attendance().stream()
                .map(FeeAttendanceView::attendanceDays)
                .map(FeeBillingEngine::nz)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            blocks.add(new FeeMatrixExportBuilder.StudentBlock(
                detail.studentNo(), detail.studentName(),
                parent == null ? null : parent.getParentName(),
                parent == null ? null : parent.getParentPhone(),
                parent == null ? null : parent.getAlipayPhone(),
                detail.remark(), attendanceDays,
                detail.items(), detail.monthly(),
                detail.receivable(), detail.paid(), detail.unpaid(),
                detail.totalOccurrence(), detail.balance()));
        }
        InstitutionEntity institution = institutionMapper.selectById(first.institutionId());
        return FeeMatrixExportBuilder.build(
            institution == null ? null : institution.getOrgName(),
            first.campusName(), first.className(), first.termName(),
            months, itemRefs, blocks);
    }

    @Override
    public byte[] exportDetail(FeeLedgerPageQuery query) {
        List<FeeLedgerEntity> ledgers = loadExportLedgers(query);
        List<FeeLedgerDetailView> details = ledgers.stream().map(this::buildDetail).toList();
        List<FeeLedgerDetailExportRow> detailRows = new ArrayList<>();
        List<FeeMonthlyExportRow> monthlyRows = new ArrayList<>();
        for (FeeLedgerDetailView detail : details) {
            Map<Long, String> itemNames = detail.items().stream().collect(Collectors.toMap(
                FeeLedgerItemView::feeItemId, FeeLedgerItemView::itemName, (first, second) -> first));
            Map<YearMonth, BigDecimal> attendanceByMonth = detail.attendance().stream().collect(
                Collectors.toMap(row -> YearMonth.from(row.billingMonth()),
                    FeeAttendanceView::attendanceDays, (first, second) -> first));
            for (FeeLedgerItemView item : detail.items()) {
                detailRows.add(new FeeLedgerDetailExportRow(detail.campusName(), detail.termName(),
                    detail.className(), detail.studentNo(), detail.studentName(),
                    item.itemName(), billingModeLabel(item.billingMode()),
                    item.billableMonths(), item.monthlyStandard(), item.dailyRate(),
                    item.openingBalance(), item.otherDeduction(),
                    item.receivable(), item.paid(), item.unpaid(), item.totalOccurrence(), item.balance(),
                    ledgerStatusLabel(detail.status()), item.remark()));
            }
            for (FeeMonthlyView monthly : detail.monthly()) {
                monthlyRows.add(new FeeMonthlyExportRow(detail.campusName(), detail.termName(),
                    detail.className(), detail.studentNo(), detail.studentName(),
                    itemNames.get(monthly.feeItemId()),
                    YearMonth.from(monthly.billingMonth()).toString(),
                    monthly.monthFactor(),
                    attendanceByMonth.get(YearMonth.from(monthly.billingMonth())),
                    monthly.autoAmount(), monthly.occurrenceAmount(),
                    monthly.manualOverride() ? "是" : "", monthly.remark()));
            }
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ExcelWriter writer = EasyExcel.write(out).build();
            try {
                writer.write(detailRows, EasyExcel.writerSheet(0, "台账明细")
                    .head(FeeLedgerDetailExportRow.class).build());
                writer.write(monthlyRows, EasyExcel.writerSheet(1, "逐月发生")
                    .head(FeeMonthlyExportRow.class).build());
            } finally {
                writer.finish();
            }
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("生成收费台账明细失败", exception);
        }
    }

    // ------------------------------------------------------------------ 查询辅助

    private LambdaQueryWrapper<FeeLedgerEntity> buildWrapper(FeeLedgerPageQuery query, Long institutionId) {
        LambdaQueryWrapper<FeeLedgerEntity> wrapper = new LambdaQueryWrapper<FeeLedgerEntity>()
            .eq(FeeLedgerEntity::getInstitutionId, institutionId);
        if (query.getCampusId() != null) {
            CampusScopeGuard.assertCampus(query.getCampusId(), "收费台账");
            wrapper.eq(FeeLedgerEntity::getCampusId, query.getCampusId());
        }
        CampusScopeGuard.apply(wrapper, FeeLedgerEntity::getCampusId);
        if (query.getTermId() != null) {
            wrapper.eq(FeeLedgerEntity::getTermId, query.getTermId());
        }
        if (query.getClassId() != null) {
            wrapper.eq(FeeLedgerEntity::getClassId, query.getClassId());
        }
        if (query.getStudentId() != null) {
            wrapper.eq(FeeLedgerEntity::getStudentId, query.getStudentId());
        }
        String status = normalize(query.getStatus());
        if (status != null && !status.isBlank()) {
            wrapper.eq(FeeLedgerEntity::getStatus, status);
        }
        String keyword = normalize(query.getKeyword());
        if (keyword != null && !keyword.isBlank()) {
            applyKeyword(wrapper, institutionId, keyword);
        }
        return wrapper;
    }

    private void applyKeyword(LambdaQueryWrapper<FeeLedgerEntity> wrapper, Long institutionId, String keyword) {
        // 关键字只用于等值取 id，真正的匹配交给 MyBatis 参数绑定，绝不拼进 SQL
        List<Long> studentIds = studentMapper.selectList(new LambdaQueryWrapper<StudentEntity>()
                .eq(StudentEntity::getInstitutionId, institutionId)
                .and(w -> w.like(StudentEntity::getStudentNo, keyword)
                    .or().like(StudentEntity::getStudentName, keyword)))
            .stream().map(StudentEntity::getId).limit(500).toList();
        List<Long> classIds = teachingClassMapper.selectList(new LambdaQueryWrapper<TeachingClassEntity>()
                .eq(TeachingClassEntity::getInstitutionId, institutionId)
                .and(w -> w.like(TeachingClassEntity::getClassName, keyword)
                    .or().like(TeachingClassEntity::getClassCode, keyword)))
            .stream().map(TeachingClassEntity::getId).limit(500).toList();
        if (studentIds.isEmpty() && classIds.isEmpty()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.and(w -> {
            if (!studentIds.isEmpty()) {
                w.in(FeeLedgerEntity::getStudentId, studentIds);
            }
            if (!classIds.isEmpty()) {
                if (!studentIds.isEmpty()) {
                    w.or();
                }
                w.in(FeeLedgerEntity::getClassId, classIds);
            }
        });
    }

    private void applyDefaultOrder(LambdaQueryWrapper<FeeLedgerEntity> wrapper) {
        wrapper.orderByAsc(FeeLedgerEntity::getClassId)
            .orderByAsc(FeeLedgerEntity::getStudentId)
            .orderByAsc(FeeLedgerEntity::getId);
    }

    private List<FeeLedgerEntity> loadExportLedgers(FeeLedgerPageQuery query) {
        Long institutionId = feeLedgerGuard.resolveInstitution(query.getInstitutionId());
        LambdaQueryWrapper<FeeLedgerEntity> wrapper = buildWrapper(query, institutionId);
        Long total = feeLedgerMapper.selectCount(wrapper);
        if (total == null || total == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前筛选范围下没有收费台账可导出");
        }
        if (total > MAX_EXPORT_ROWS) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "待导出 " + total + " 份台账，超过单次上限 " + MAX_EXPORT_ROWS + " 份，请先按学期或班级收窄范围");
        }
        applyDefaultOrder(wrapper);
        return feeLedgerMapper.selectList(wrapper);
    }

    // ------------------------------------------------------------------ 视图组装

    private List<FeeLedgerRowView> toRowViews(List<FeeLedgerEntity> ledgers) {
        if (ledgers.isEmpty()) {
            return List.of();
        }
        List<Long> ledgerIds = ledgers.stream().map(FeeLedgerEntity::getId).toList();
        List<FeeLedgerItemEntity> items = feeLedgerItemMapper.selectList(
            new LambdaQueryWrapper<FeeLedgerItemEntity>().in(FeeLedgerItemEntity::getLedgerId, ledgerIds));
        List<FeeMonthlyEntity> monthly = feeMonthlyMapper.selectList(
            new LambdaQueryWrapper<FeeMonthlyEntity>().in(FeeMonthlyEntity::getLedgerId, ledgerIds));
        List<FeePaymentEntity> payments = feePaymentMapper.selectList(
            new LambdaQueryWrapper<FeePaymentEntity>().in(FeePaymentEntity::getLedgerId, ledgerIds));
        Map<Long, BigDecimal> paidByItem = FeeLedgerCalculator.paidByItem(payments);
        Map<Long, BigDecimal> occurrenceByItem = FeeLedgerCalculator.occurrenceByItem(monthly);
        Map<Long, List<FeeLedgerItemEntity>> itemsByLedger = items.stream()
            .collect(Collectors.groupingBy(FeeLedgerItemEntity::getLedgerId));
        Names names = loadNames(ledgers);

        List<FeeLedgerRowView> rows = new ArrayList<>();
        for (FeeLedgerEntity ledger : ledgers) {
            List<FeeLedgerItemEntity> ledgerItems = itemsByLedger.getOrDefault(ledger.getId(), List.of());
            BigDecimal receivable = BigDecimal.ZERO;
            BigDecimal paid = BigDecimal.ZERO;
            BigDecimal occurrence = BigDecimal.ZERO;
            for (FeeLedgerItemEntity item : ledgerItems) {
                receivable = receivable.add(itemReceivable(item));
                paid = paid.add(paidByItem.getOrDefault(item.getId(), BigDecimal.ZERO));
                occurrence = occurrence.add(occurrenceByItem.getOrDefault(item.getId(), BigDecimal.ZERO));
            }
            StudentEntity student = names.students().get(ledger.getStudentId());
            TeachingClassEntity clazz = names.classes().get(ledger.getClassId());
            TermEntity term = names.terms().get(ledger.getTermId());
            CampusEntity campus = names.campuses().get(ledger.getCampusId());
            rows.add(new FeeLedgerRowView(ledger.getId(), ledger.getInstitutionId(), ledger.getCampusId(),
                campus == null ? null : campus.getCampusName(), ledger.getClassId(),
                clazz == null ? null : clazz.getClassName(), ledger.getTermId(),
                term == null ? null : term.getTermName(), ledger.getStudentId(),
                student == null ? null : student.getStudentNo(),
                student == null ? null : student.getStudentName(),
                ledger.getStatus(), ledgerItems.size(),
                FeeBillingEngine.money(receivable), FeeBillingEngine.money(paid),
                FeeBillingEngine.unpaid(receivable, paid), FeeBillingEngine.money(occurrence),
                FeeBillingEngine.balance(paid, occurrence), ledger.getRemark()));
        }
        return rows;
    }

    private FeeLedgerDetailView buildDetail(FeeLedgerEntity ledger) {
        List<FeeLedgerItemEntity> items = feeLedgerItemMapper.selectList(
            new LambdaQueryWrapper<FeeLedgerItemEntity>().eq(FeeLedgerItemEntity::getLedgerId, ledger.getId()));
        Map<Long, FeeItemEntity> feeItems = loadFeeItems(items);
        items.sort(Comparator
            .comparing((FeeLedgerItemEntity item) -> sortNoOf(feeItems, item))
            .thenComparing(FeeLedgerItemEntity::getId));

        List<FeeMonthlyEntity> monthly = feeMonthlyMapper.selectList(
            new LambdaQueryWrapper<FeeMonthlyEntity>().eq(FeeMonthlyEntity::getLedgerId, ledger.getId()));
        List<FeeAttendanceMonthEntity> attendance = feeAttendanceMonthMapper.selectList(
            new LambdaQueryWrapper<FeeAttendanceMonthEntity>()
                .eq(FeeAttendanceMonthEntity::getLedgerId, ledger.getId())
                .orderByAsc(FeeAttendanceMonthEntity::getBillingMonth));
        List<FeePaymentEntity> payments = feePaymentMapper.selectList(
            new LambdaQueryWrapper<FeePaymentEntity>()
                .eq(FeePaymentEntity::getLedgerId, ledger.getId())
                .orderByDesc(FeePaymentEntity::getPaidOn)
                .orderByDesc(FeePaymentEntity::getId));

        Map<Long, BigDecimal> paidByItem = FeeLedgerCalculator.paidByItem(payments);
        Map<Long, BigDecimal> occurrenceByItem = FeeLedgerCalculator.occurrenceByItem(monthly);

        List<FeeLedgerItemView> itemViews = new ArrayList<>();
        BigDecimal receivable = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal occurrence = BigDecimal.ZERO;
        for (FeeLedgerItemEntity item : items) {
            BigDecimal itemReceivable = itemReceivable(item);
            BigDecimal itemPaid = paidByItem.getOrDefault(item.getId(), BigDecimal.ZERO);
            BigDecimal itemOccurrence = occurrenceByItem.getOrDefault(item.getId(), BigDecimal.ZERO);
            receivable = receivable.add(itemReceivable);
            paid = paid.add(itemPaid);
            occurrence = occurrence.add(itemOccurrence);
            FeeItemEntity feeItem = feeItems.get(item.getFeeItemId());
            itemViews.add(new FeeLedgerItemView(item.getId(), item.getFeeItemId(),
                feeItem == null ? null : feeItem.getItemCode(),
                feeItem == null ? null : feeItem.getItemName(),
                item.getBillingMode(), item.getBillableMonths(), item.getMonthlyStandard(),
                item.getDailyRate(), item.getOpeningBalance(), item.getOtherDeduction(),
                itemReceivable, FeeBillingEngine.money(itemPaid),
                FeeBillingEngine.unpaid(itemReceivable, itemPaid), FeeBillingEngine.money(itemOccurrence),
                FeeBillingEngine.balance(itemPaid, itemOccurrence), item.getPriceSource(), item.getRemark()));
        }

        Map<Long, Integer> itemSortNo = new HashMap<>();
        items.forEach(item -> itemSortNo.put(item.getFeeItemId(), sortNoOf(feeItems, item)));
        List<FeeMonthlyView> monthlyViews = monthly.stream()
            .sorted(Comparator
                .comparing(FeeMonthlyEntity::getBillingMonth)
                .thenComparing((FeeMonthlyEntity row) -> itemSortNo.getOrDefault(itemFeeItemId(items, row), 0))
                .thenComparing(FeeMonthlyEntity::getLedgerItemId))
            .map(row -> {
                FeeItemEntity feeItem = feeItems.get(itemFeeItemId(items, row));
                return new FeeMonthlyView(row.getId(), row.getLedgerItemId(),
                    feeItem == null ? null : feeItem.getId(),
                    feeItem == null ? null : feeItem.getItemName(),
                    feeItem == null ? null : feeItem.getBillingMode(),
                    row.getBillingMonth(), row.getMonthFactor(), row.getAutoAmount(),
                    row.getOccurrenceAmount(), Boolean.TRUE.equals(row.getManualOverride()),
                    row.getRemark());
            }).toList();

        List<FeeAttendanceView> attendanceViews = attendance.stream()
            .map(row -> new FeeAttendanceView(row.getId(), row.getBillingMonth(),
                row.getAttendanceDays(), row.getAutoDays(),
                Boolean.TRUE.equals(row.getManualOverride()), row.getSource(), row.getRemark()))
            .toList();
        List<FeePaymentView> paymentViews = payments.stream()
            .map(row -> new FeePaymentView(row.getId(), row.getLedgerItemId(), row.getPaymentNo(),
                row.getAmount(), row.getPaidOn(), row.getPaymentMethod(), row.getBillingMonth(),
                row.getVoucherNo(), row.getRemark()))
            .toList();

        StudentEntity student = studentMapper.selectById(ledger.getStudentId());
        TeachingClassEntity clazz = teachingClassMapper.selectById(ledger.getClassId());
        TermEntity term = termMapper.selectById(ledger.getTermId());
        CampusEntity campus = campusMapper.selectById(ledger.getCampusId());
        return new FeeLedgerDetailView(ledger.getId(), ledger.getInstitutionId(), ledger.getCampusId(),
            campus == null ? null : campus.getCampusName(), ledger.getClassId(),
            clazz == null ? null : clazz.getClassName(), ledger.getTermId(),
            term == null ? null : term.getTermName(),
            term == null ? null : term.getStartDate(), term == null ? null : term.getEndDate(),
            ledger.getStudentId(), student == null ? null : student.getStudentNo(),
            student == null ? null : student.getStudentName(), ledger.getStatus(), ledger.getRemark(),
            itemViews, monthlyViews, attendanceViews, paymentViews,
            FeeBillingEngine.money(receivable), FeeBillingEngine.money(paid),
            FeeBillingEngine.unpaid(receivable, paid), FeeBillingEngine.money(occurrence),
            FeeBillingEngine.balance(paid, occurrence));
    }

    // ------------------------------------------------------------------ 建账与重算

    private void applyItems(FeeLedgerEntity ledger, TermEntity term,
                            List<FeeLedgerItemRequest> requested, boolean create) {
        List<FeeLedgerItemRequest> effective = requested;
        if (effective == null || effective.isEmpty()) {
            if (!create) {
                return;
            }
            effective = defaultItemRequests(ledger.getInstitutionId());
        }
        Map<Long, FeeLedgerItemRequest> byItem = new LinkedHashMap<>();
        for (FeeLedgerItemRequest request : effective) {
            if (byItem.putIfAbsent(request.feeItemId(), request) != null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "同一个收费项目不能重复配置");
            }
        }
        Map<Long, FeeLedgerItemEntity> existing = feeLedgerItemMapper.selectList(
                new LambdaQueryWrapper<FeeLedgerItemEntity>()
                    .eq(FeeLedgerItemEntity::getLedgerId, ledger.getId()))
            .stream().collect(Collectors.toMap(FeeLedgerItemEntity::getFeeItemId,
                Function.identity(), (first, second) -> first));

        for (Map.Entry<Long, FeeLedgerItemRequest> entry : byItem.entrySet()) {
            FeeItemEntity feeItem = feeItemMapper.selectById(entry.getKey());
            if (feeItem == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "收费项目不存在：" + entry.getKey());
            }
            if (!Objects.equals(feeItem.getInstitutionId(), ledger.getInstitutionId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "收费项目不属于该机构：" + feeItem.getItemName());
            }
            upsertItem(ledger, term, feeItem, entry.getValue(), existing.get(entry.getKey()));
        }
        for (FeeLedgerItemEntity item : existing.values()) {
            if (!byItem.containsKey(item.getFeeItemId())) {
                pruneItem(item);
            }
        }
    }

    private List<FeeLedgerItemRequest> defaultItemRequests(Long institutionId) {
        return feeItemMapper.selectList(new LambdaQueryWrapper<FeeItemEntity>()
                .eq(FeeItemEntity::getInstitutionId, institutionId)
                .eq(FeeItemEntity::getStatus, "active")
                .orderByAsc(FeeItemEntity::getSortNo)
                .orderByAsc(FeeItemEntity::getId))
            .stream()
            .map(item -> new FeeLedgerItemRequest(item.getId(), null, null, null, null, null, null))
            .toList();
    }

    private void upsertItem(FeeLedgerEntity ledger, TermEntity term, FeeItemEntity feeItem,
                            FeeLedgerItemRequest request, FeeLedgerItemEntity existing) {
        FeeLedgerItemEntity entity = existing == null ? new FeeLedgerItemEntity() : existing;
        if (existing == null) {
            entity.setInstitutionId(ledger.getInstitutionId());
            entity.setCampusId(ledger.getCampusId());
            entity.setLedgerId(ledger.getId());
            entity.setFeeItemId(feeItem.getId());
            entity.setDeleted(Boolean.FALSE);
        }
        String billingMode = feeItem.getBillingMode() == null ? MONTHLY : feeItem.getBillingMode();
        entity.setBillingMode(billingMode);
        entity.setBillableMonths(request.billableMonths() != null
            ? FeeBillingEngine.quantity(request.billableMonths())
            : FeeBillingEngine.defaultBillableMonths(term.getStartDate(), term.getEndDate()));

        FeePriceResolver.ResolvedPrice price = feePriceResolver.resolve(
            ledger.getInstitutionId(), feeItem.getId(), ledger.getCampusId(),
            ledger.getClassId(), ledger.getTermId(), ledger.getStudentId());
        boolean manualMonthly = request.monthlyStandard() != null;
        boolean manualDaily = request.dailyRate() != null;
        entity.setMonthlyStandard(FeeBillingEngine.money(
            manualMonthly ? request.monthlyStandard() : price.monthlyPrice()));
        entity.setDailyRate(FeeBillingEngine.money(
            manualDaily ? request.dailyRate() : price.dailyPrice()));
        if (DAILY.equals(billingMode)) {
            entity.setPriceSource(manualDaily ? "MANUAL" : price.sourceFor(DAILY));
            entity.setPriceId(manualDaily ? null : price.priceIdFor(DAILY));
        } else {
            entity.setPriceSource(manualMonthly ? "MANUAL" : price.sourceFor(MONTHLY));
            entity.setPriceId(manualMonthly ? null : price.priceIdFor(MONTHLY));
        }
        entity.setOpeningBalance(FeeBillingEngine.money(request.openingBalance()));
        entity.setOtherDeduction(FeeBillingEngine.money(request.otherDeduction()));
        if (request.remark() != null) {
            entity.setRemark(normalizeToNull(request.remark()));
        }
        if (existing == null) {
            feeLedgerItemMapper.insert(entity);
        } else {
            feeLedgerItemMapper.updateById(entity);
        }
    }

    private void pruneItem(FeeLedgerItemEntity item) {
        FeeItemEntity feeItem = feeItemMapper.selectById(item.getFeeItemId());
        String itemName = feeItem == null ? String.valueOf(item.getFeeItemId()) : feeItem.getItemName();
        Long paymentCount = feePaymentMapper.selectCount(new LambdaQueryWrapper<FeePaymentEntity>()
            .eq(FeePaymentEntity::getLedgerItemId, item.getId()));
        if (paymentCount != null && paymentCount > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "「" + itemName + "」已有收款流水，不能移除；收费有误请先冲正或删除收款记录");
        }
        Long overrideCount = feeMonthlyMapper.selectCount(new LambdaQueryWrapper<FeeMonthlyEntity>()
            .eq(FeeMonthlyEntity::getLedgerItemId, item.getId())
            .eq(FeeMonthlyEntity::getManualOverride, Boolean.TRUE));
        if (overrideCount != null && overrideCount > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "「" + itemName + "」有手工覆盖的逐月发生额，不能移除；请先取消覆盖");
        }
        feeMonthlyMapper.deletePhysicallyByLedgerItemId(item.getId());
        feeLedgerItemMapper.physicalDeleteById(item.getId());
    }

    private void rebuildMonthly(FeeLedgerEntity ledger, TermEntity term) {
        if (term.getStartDate() == null || term.getEndDate() == null) {
            return;
        }
        List<FeeLedgerItemEntity> items = feeLedgerItemMapper.selectList(
            new LambdaQueryWrapper<FeeLedgerItemEntity>()
                .eq(FeeLedgerItemEntity::getLedgerId, ledger.getId()));
        Map<YearMonth, FeeAttendanceMonthEntity> attendance = feeAttendanceMonthMapper.selectList(
                new LambdaQueryWrapper<FeeAttendanceMonthEntity>()
                    .eq(FeeAttendanceMonthEntity::getLedgerId, ledger.getId()))
            .stream().collect(Collectors.toMap(
                row -> YearMonth.from(row.getBillingMonth()),
                Function.identity(), (first, second) -> first));
        Map<String, FeeMonthlyEntity> existing = feeMonthlyMapper.selectList(
                new LambdaQueryWrapper<FeeMonthlyEntity>()
                    .eq(FeeMonthlyEntity::getLedgerId, ledger.getId()))
            .stream().collect(Collectors.toMap(
                row -> monthlyKey(row.getLedgerItemId(), row.getBillingMonth()),
                Function.identity(), (first, second) -> first));

        for (FeeLedgerItemEntity item : items) {
            for (YearMonth month : FeeBillingEngine.termMonths(term.getStartDate(), term.getEndDate())) {
                LocalDate monthFirst = month.atDay(1);
                FeeMonthlyEntity row = existing.get(monthlyKey(item.getId(), monthFirst));
                boolean isNew = row == null;
                if (isNew) {
                    row = new FeeMonthlyEntity();
                    row.setInstitutionId(ledger.getInstitutionId());
                    row.setCampusId(ledger.getCampusId());
                    row.setLedgerId(ledger.getId());
                    row.setLedgerItemId(item.getId());
                    row.setBillingMonth(monthFirst);
                    row.setManualOverride(Boolean.FALSE);
                    row.setDeleted(Boolean.FALSE);
                }
                if (row.getMonthFactor() == null) {
                    row.setMonthFactor(FeeBillingEngine.defaultFactor(term.getStartDate(), month));
                }
                BigDecimal days = attendance.containsKey(month)
                    ? FeeBillingEngine.quantity(attendance.get(month).getAttendanceDays())
                    : BigDecimal.ZERO;
                BigDecimal auto = DAILY.equals(item.getBillingMode())
                    ? FeeBillingEngine.dailyOccurrence(item.getDailyRate(), days)
                    : FeeBillingEngine.monthlyOccurrence(item.getMonthlyStandard(), row.getMonthFactor(), days);
                row.setAutoAmount(auto);
                if (isNew || !Boolean.TRUE.equals(row.getManualOverride())) {
                    row.setOccurrenceAmount(auto);
                }
                if (isNew) {
                    feeMonthlyMapper.insert(row);
                } else {
                    feeMonthlyMapper.updateById(row);
                }
            }
        }
    }

    private List<FeeLedgerEntity> resolveRecalculateLedgers(FeeRecalculateRequest request) {
        if (request.ledgerId() != null) {
            return List.of(feeLedgerGuard.requireLedger(request.ledgerId()));
        }
        if (request.termId() == null || request.classId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请指定台账，或同时指定学期与班级");
        }
        Long institutionId = feeLedgerGuard.resolveInstitution(null);
        TeachingClassEntity clazz = feeLedgerGuard.requireClass(request.classId(), institutionId);
        feeLedgerGuard.requireTerm(request.termId(), institutionId);
        return feeLedgerMapper.selectList(new LambdaQueryWrapper<FeeLedgerEntity>()
            .eq(FeeLedgerEntity::getInstitutionId, institutionId)
            .eq(FeeLedgerEntity::getTermId, request.termId())
            .eq(FeeLedgerEntity::getClassId, clazz.getId()));
    }

    // ------------------------------------------------------------------ 导出辅助

    private List<FeeMatrixExportBuilder.ItemRef> resolveMatrixItems(Long institutionId,
                                                                    java.util.Collection<FeeLedgerDetailView> details) {
        Map<Long, String> ordered = new LinkedHashMap<>();
        feeItemMapper.selectList(new LambdaQueryWrapper<FeeItemEntity>()
                .eq(FeeItemEntity::getInstitutionId, institutionId)
                .orderByAsc(FeeItemEntity::getSortNo)
                .orderByAsc(FeeItemEntity::getId))
            .forEach(item -> ordered.put(item.getId(), item.getItemName()));
        // 台账里还引用着、但项目表已查不到（或已跨机构）的行也要保留，否则金额凭空消失
        for (FeeLedgerDetailView detail : details) {
            for (FeeLedgerItemView item : detail.items()) {
                ordered.putIfAbsent(item.feeItemId(),
                    item.itemName() == null ? String.valueOf(item.feeItemId()) : item.itemName());
            }
        }
        return ordered.entrySet().stream()
            .map(entry -> new FeeMatrixExportBuilder.ItemRef(entry.getKey(), entry.getValue()))
            .toList();
    }

    private Map<Long, StudentParentEntity> loadParents(java.util.Collection<FeeLedgerDetailView> details) {
        List<Long> studentIds = details.stream().map(FeeLedgerDetailView::studentId)
            .filter(Objects::nonNull).distinct().toList();
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, StudentParentEntity> parents = new HashMap<>();
        for (StudentParentEntity parent : studentParentMapper.selectList(
            new LambdaQueryWrapper<StudentParentEntity>()
                .in(StudentParentEntity::getStudentId, studentIds)
                .eq(StudentParentEntity::getStatus, "active"))) {
            parents.merge(parent.getStudentId(), parent, FeeLedgerServiceImpl::preferParent);
        }
        return parents;
    }

    private static StudentParentEntity preferParent(StudentParentEntity first, StudentParentEntity second) {
        if (Boolean.TRUE.equals(first.getIsPrimary())) {
            return first;
        }
        if (Boolean.TRUE.equals(second.getIsPrimary())) {
            return second;
        }
        return first;
    }

    private Names loadNames(List<FeeLedgerEntity> ledgers) {
        List<Long> studentIds = ledgers.stream().map(FeeLedgerEntity::getStudentId).distinct().toList();
        List<Long> classIds = ledgers.stream().map(FeeLedgerEntity::getClassId).distinct().toList();
        List<Long> termIds = ledgers.stream().map(FeeLedgerEntity::getTermId).distinct().toList();
        List<Long> campusIds = ledgers.stream().map(FeeLedgerEntity::getCampusId).distinct().toList();
        return new Names(
            studentIds.isEmpty() ? Map.of() : studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(StudentEntity::getId, Function.identity(), (a, b) -> a)),
            classIds.isEmpty() ? Map.of() : teachingClassMapper.selectBatchIds(classIds).stream()
                .collect(Collectors.toMap(TeachingClassEntity::getId, Function.identity(), (a, b) -> a)),
            termIds.isEmpty() ? Map.of() : termMapper.selectBatchIds(termIds).stream()
                .collect(Collectors.toMap(TermEntity::getId, Function.identity(), (a, b) -> a)),
            campusIds.isEmpty() ? Map.of() : campusMapper.selectBatchIds(campusIds).stream()
                .collect(Collectors.toMap(CampusEntity::getId, Function.identity(), (a, b) -> a)));
    }

    // ------------------------------------------------------------------ 小工具

    private BigDecimal itemReceivable(FeeLedgerItemEntity item) {
        return FeeBillingEngine.receivable(item.getBillableMonths(), item.getMonthlyStandard(),
            item.getOpeningBalance(), item.getOtherDeduction());
    }

    private Map<Long, FeeItemEntity> loadFeeItems(List<FeeLedgerItemEntity> items) {
        List<Long> itemIds = items.stream().map(FeeLedgerItemEntity::getFeeItemId).distinct().toList();
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return feeItemMapper.selectBatchIds(itemIds).stream()
            .collect(Collectors.toMap(FeeItemEntity::getId, Function.identity(), (a, b) -> a));
    }

    private static int sortNoOf(Map<Long, FeeItemEntity> feeItems, FeeLedgerItemEntity item) {
        FeeItemEntity feeItem = feeItems.get(item.getFeeItemId());
        return feeItem == null || feeItem.getSortNo() == null ? 0 : feeItem.getSortNo();
    }

    private static Long itemFeeItemId(List<FeeLedgerItemEntity> items, FeeMonthlyEntity monthly) {
        return items.stream()
            .filter(item -> item.getId().equals(monthly.getLedgerItemId()))
            .map(FeeLedgerItemEntity::getFeeItemId)
            .findFirst().orElse(null);
    }

    private static String monthlyKey(Long ledgerItemId, LocalDate billingMonth) {
        return ledgerItemId + "|" + billingMonth;
    }

    private TermEntity requireTermEntity(Long termId) {
        TermEntity term = termMapper.selectById(termId);
        if (term == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "学期不存在：" + termId);
        }
        return term;
    }

    private String requireStatus(String value) {
        String normalized = normalize(value);
        if (normalized == null || normalized.isBlank()) {
            return "active";
        }
        if (!LEDGER_STATUSES.contains(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "台账状态只能是 active 或 settled");
        }
        return normalized;
    }

    private static String billingModeLabel(String billingMode) {
        return DAILY.equals(billingMode) ? "按天" : "按月";
    }

    private static String ledgerStatusLabel(String status) {
        return "settled".equals(status) ? "已结清" : "在收";
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeToNull(String value) {
        String normalized = normalize(value);
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private record Names(Map<Long, StudentEntity> students,
                         Map<Long, TeachingClassEntity> classes,
                         Map<Long, TermEntity> terms,
                         Map<Long, CampusEntity> campuses) {
    }
}
