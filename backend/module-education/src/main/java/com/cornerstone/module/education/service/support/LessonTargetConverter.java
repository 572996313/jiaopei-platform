package com.cornerstone.module.education.service.support;

import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 班级「每周应上节数」的录入口径换算。
 *
 * <p>操作员手上未必有"每周几节"这个数：合同常写"一共 40 节"，收费口径常是"一个月 8 次"。
 * 这里只解决"按月填"到"按周算"的一步，让报表的分母仍然只有一个来源。</p>
 *
 * <p>两点约束别改回去：</p>
 * <ul>
 *   <li>换算结果写进 {@code jp_class.weekly_lessons}，原值写进 {@code weekly_lessons_input}。
 *       只换算一次又不留原值，编辑弹窗就会还原成一个用户从没填过的数字。</li>
 *   <li>结果不足 1 时返回 {@code null} 而不是 0。读取侧判的是 {@code != null && > 0}，
 *       0 与 null 行为完全一致，但"每周应上 0 节"是非法值（见 AGENTS 的课时目标口径条目与 V75 头部），
 *       存一个会误导下一个读代码的人的值没有好处。</li>
 * </ul>
 */
public final class LessonTargetConverter {

    /** 按周填：填进去的就是每周节数 */
    public static final String MODE_WEEKLY = "weekly";
    /** 按月填：填进去的是每月节数，由本类换算成每周 */
    public static final String MODE_MONTHLY = "monthly";

    /**
     * 一年按 52 周、12 个月算。用整数常量而不是 ÷4.348：换算式要能被操作员拿计算器复验，
     * {@code 8 × 12 ÷ 52 = 1.85 → 2} 一眼看得懂，除以小数就只能选择相信系统。
     */
    private static final int MONTHS_PER_YEAR = 12;
    private static final int WEEKS_PER_YEAR = 52;

    private LessonTargetConverter() {
    }

    /** 口径为空按"按周填"处理，与迁移列的默认值一致；出现未知值直接拒绝，不静默回落。 */
    public static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return MODE_WEEKLY;
        }
        String value = mode.trim().toLowerCase();
        if (!MODE_WEEKLY.equals(value) && !MODE_MONTHLY.equals(value)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "课时目标口径不支持：" + mode + "，可选值：weekly、monthly");
        }
        return value;
    }

    /**
     * 把录制原值换算成每周应上节数。
     *
     * @param mode  录入口径，见 {@link #normalizeMode(String)}
     * @param input 录制原值（按周口径时就是每周节数，按月口径时是每月节数）
     * @return 每周应上节数；未设定或换算不足 1 节时为 {@code null}
     */
    public static Integer toWeekly(String mode, Integer input) {
        if (input == null || input <= 0) {
            return null;
        }
        if (MODE_WEEKLY.equals(normalizeMode(mode))) {
            return input;
        }
        int weekly = BigDecimal.valueOf((long) input * MONTHS_PER_YEAR)
            .divide(BigDecimal.valueOf(WEEKS_PER_YEAR), 0, RoundingMode.HALF_UP)
            .intValue();
        return weekly > 0 ? weekly : null;
    }
}
