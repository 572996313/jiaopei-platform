package com.cornerstone.module.education.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.education.domain.entity.ScheduleAutoBatchEntity;

public interface ScheduleAutoBatchMapper extends BaseMapper<ScheduleAutoBatchEntity> {
    // 批次只撤销不删除：改 status + 删本批课次，清单本身留着回答"这批课是谁什么时候排的"。
}
