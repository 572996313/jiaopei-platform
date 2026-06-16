package com.jiaopei.platform.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_class_change_notice")
public class ClassChangeNoticeEntity extends BaseEntity {

    private Long institutionId;
    private Long campusId;
    private Long classId;
    private String sourceType;
    private Long sourceId;
    private String noticeType;
    private String noticeLevel;
    private String noticeStatus;
    private String noticeMessage;
    private String suggestedAction;
    private LocalDateTime resolvedAt;
    private Long resolvedBy;
    private String remark;
}
