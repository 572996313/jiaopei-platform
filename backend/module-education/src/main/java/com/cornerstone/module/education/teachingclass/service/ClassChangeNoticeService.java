package com.cornerstone.module.education.teachingclass.service;

import com.cornerstone.module.education.domain.entity.ClassChangeNoticeEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import java.util.List;

public interface ClassChangeNoticeService {

    List<ClassChangeNoticeEntity> listNotices(Long institutionId, Long classId, String noticeStatus);

    void syncClassNotices(Long institutionId, Long classId);

    void recordPrimaryTeacherChanged(TeachingClassEntity teachingClass, Long sourceId, String noticeMessage,
                                     String suggestedAction);
}
