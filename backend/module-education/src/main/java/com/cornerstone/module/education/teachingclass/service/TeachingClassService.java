package com.cornerstone.module.education.teachingclass.service;

import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.teachingclass.dto.ClassOptionView;
import com.cornerstone.module.education.teachingclass.dto.TeachingClassCreateRequest;
import java.util.List;

public interface TeachingClassService {

    List<TeachingClassEntity> listClasses(Long institutionId, Long campusId, Long termId);

    /**
     * 班级下拉的按需搜索选项。班级档案下拉曾把整机构几百个班一次性画进 DOM，把班级/排课几页拖到秒级卡顿，
     * 所以这里只回下拉与回显需要的字段、按关键字模糊匹配并封顶条数。
     *
     * <p>{@code ids} 用于"表格行只有班级 id"的名称批量回查（一屏课次/计划涉及的班），
     * 传了 ids 就按 id 取、忽略 keyword，上限换成 {@code CLASS_LABEL_LIMIT}。</p>
     */
    List<ClassOptionView> listClassOptions(Long institutionId, Long campusId, Long termId, String keyword,
                                           List<Long> ids);

    /** 班级条数：下拉改成按需搜索后本机没有全量数组，统计卡的"班级数"必须由服务端算。 */
    long countClasses(Long institutionId, Long campusId, Long termId);

    /**
     * 按 id 取单个班级。班级主档页要看课程/班型/容量/课时目标一整排字段，但它一次只展示一个班，
     * 所以取这一条就够，不需要把全机构的班级拉回本机当字典。
     *
     * <p>{@code institutionId} 必传并参与比对：按 id 直取单条最容易漏掉机构归属。</p>
     */
    TeachingClassEntity getClassDetail(Long id, Long institutionId);

    TeachingClassEntity createClass(TeachingClassCreateRequest request);

    TeachingClassEntity updateClass(Long id, TeachingClassCreateRequest request);

    void deleteClass(Long id);
}
