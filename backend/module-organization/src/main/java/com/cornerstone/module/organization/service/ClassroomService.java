package com.cornerstone.module.organization.service;

import com.cornerstone.module.organization.domain.entity.ClassroomEntity;
import com.cornerstone.module.organization.dto.ClassroomCreateRequest;
import com.cornerstone.module.organization.dto.ClassroomUpdateRequest;
import java.util.List;

public interface ClassroomService {

    /**
     * 教室列表。
     *
     * @param operatorInstitutionId 当前登录账号所属机构，必填；教室表没有机构列，靠本校区的 id 集合收窄
     * @param campusId 校区收窄，传空=本机构全部校区
     */
    List<ClassroomEntity> listClassrooms(Long operatorInstitutionId, Long campusId);

    ClassroomEntity createClassroom(ClassroomCreateRequest request);

    ClassroomEntity updateClassroom(Long id, ClassroomUpdateRequest request);

    void deleteClassroom(Long id);
}
