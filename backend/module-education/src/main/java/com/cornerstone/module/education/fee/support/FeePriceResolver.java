package com.cornerstone.module.education.fee.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.module.education.fee.domain.entity.FeePriceEntity;
import com.cornerstone.module.education.fee.mapper.FeePriceMapper;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 收费取价：按「学员 &gt; 班级 &gt; 校区 &gt; 学期 &gt; 全局」的特异度取最贴合的一行。
 *
 * <p>每个维度 0 表示不限；一行价目对某次取价有效，要求它的每个非 0 维度都与目标一致。
 * 月标准与日标准**各自独立回落**：学员级只覆盖了月标准时，日标准仍取班级/校区/全局价，
 * 而不是整行作废。</p>
 *
 * <p>权重按"越具体越优先"给：学员 8、班级 4、校区 2、学期 1。同分不会出现——同一组维度
 * 有唯一键兜着；真出现（手工改库）取 id 较大的一行，结果稳定可复现。</p>
 */
@Component
public class FeePriceResolver {

    private static final long ANY = 0L;

    private final FeePriceMapper feePriceMapper;

    public FeePriceResolver(FeePriceMapper feePriceMapper) {
        this.feePriceMapper = feePriceMapper;
    }

    public ResolvedPrice resolve(Long institutionId, Long feeItemId, Long campusId,
                                 Long classId, Long termId, Long studentId) {
        FeePriceEntity monthly = best(institutionId, feeItemId, campusId, classId, termId, studentId, true);
        FeePriceEntity daily = best(institutionId, feeItemId, campusId, classId, termId, studentId, false);
        return new ResolvedPrice(
            monthly == null ? null : monthly.getMonthlyPrice(),
            daily == null ? null : daily.getDailyPrice(),
            monthly == null ? null : monthly.getId(),
            daily == null ? null : daily.getId(),
            sourceOf(monthly),
            sourceOf(daily));
    }

    private FeePriceEntity best(Long institutionId, Long feeItemId, Long campusId, Long classId,
                                Long termId, Long studentId, boolean monthly) {
        LambdaQueryWrapper<FeePriceEntity> wrapper = new LambdaQueryWrapper<FeePriceEntity>()
            .eq(FeePriceEntity::getInstitutionId, institutionId)
            .eq(FeePriceEntity::getFeeItemId, feeItemId)
            .eq(FeePriceEntity::getStatus, "active");
        if (monthly) {
            wrapper.isNotNull(FeePriceEntity::getMonthlyPrice);
        } else {
            wrapper.isNotNull(FeePriceEntity::getDailyPrice);
        }
        FeePriceEntity best = null;
        int bestScore = -1;
        for (FeePriceEntity candidate : feePriceMapper.selectList(wrapper)) {
            if (!matches(candidate.getCampusId(), campusId)
                || !matches(candidate.getClassId(), classId)
                || !matches(candidate.getTermId(), termId)
                || !matches(candidate.getStudentId(), studentId)) {
                continue;
            }
            int score = score(candidate);
            if (score > bestScore
                || (score == bestScore && best != null && candidate.getId() != null
                    && best.getId() != null && candidate.getId() > best.getId())) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static boolean matches(Long priceDimension, Long target) {
        return priceDimension == null || Objects.equals(priceDimension, ANY)
            || Objects.equals(priceDimension, target);
    }

    private static int score(FeePriceEntity price) {
        int score = 0;
        if (!isAny(price.getStudentId())) {
            score += 8;
        }
        if (!isAny(price.getClassId())) {
            score += 4;
        }
        if (!isAny(price.getCampusId())) {
            score += 2;
        }
        if (!isAny(price.getTermId())) {
            score += 1;
        }
        return score;
    }

    private static boolean isAny(Long value) {
        return value == null || value == ANY;
    }

    private static String sourceOf(FeePriceEntity price) {
        if (price == null) {
            return null;
        }
        return isAny(price.getStudentId()) ? "PRICE" : "STUDENT";
    }

    /** 取价结果：月标准/日标准各自可能为 null（该维度没配），来源分开记便于审计。 */
    public record ResolvedPrice(BigDecimal monthlyPrice, BigDecimal dailyPrice,
                                Long monthlyPriceId, Long dailyPriceId,
                                String monthlySource, String dailySource) {

        /** 台账行只存一个 priceId：按月项目记月标准来源，按天项目记日标准来源。 */
        public Long priceIdFor(String billingMode) {
            return "daily".equals(billingMode) ? dailyPriceId : monthlyPriceId;
        }

        public String sourceFor(String billingMode) {
            String source = "daily".equals(billingMode) ? dailySource : monthlySource;
            return source == null ? "MANUAL" : source;
        }
    }
}
