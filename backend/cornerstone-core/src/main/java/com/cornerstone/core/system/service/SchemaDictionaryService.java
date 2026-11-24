package com.cornerstone.core.system.service;

import com.cornerstone.core.system.dto.SchemaDictionaryView;

/**
 * 数据字典：库表结构与字段含义的只读聚合。
 *
 * <p>内容全部来自运行时事实——库结构读 {@code INFORMATION_SCHEMA}，字段含义读实体 {@code @Schema}，
 * 不维护任何手写文档，避免与库脱节。</p>
 */
public interface SchemaDictionaryService {

    /** 全库表结构（进程内缓存 60 秒，表结构不会每秒变） */
    SchemaDictionaryView dictionary();
}
