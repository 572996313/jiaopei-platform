package com.cornerstone.core.system.service;

import com.cornerstone.core.system.dto.DocumentLineageView;

/**
 * 业务单据链路：汇总各模块的 {@code DocumentLineageContributor} 登记。
 */
public interface DocumentLineageService {

    /** 当前装配模块声明的全部单据与关系 */
    DocumentLineageView lineage();
}
