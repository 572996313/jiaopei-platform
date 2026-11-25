package com.cornerstone.core.system.service;

import com.cornerstone.core.system.schema.TableReference;
import java.util.List;

/**
 * 当前库的外键引用事实，供业务链路等只读视图自动补全「引用」关系。
 */
public interface TableReferenceService {

    /** 全库外键（进程内缓存 60 秒） */
    List<TableReference> references();
}
