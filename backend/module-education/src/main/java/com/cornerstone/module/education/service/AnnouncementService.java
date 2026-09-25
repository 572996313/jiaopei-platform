package com.cornerstone.module.education.service;

import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.module.education.dto.AnnouncementPageQuery;
import com.cornerstone.module.education.dto.AnnouncementRecipientPreview;
import com.cornerstone.module.education.dto.AnnouncementTemplateUpsertRequest;
import com.cornerstone.module.education.dto.AnnouncementTemplateView;
import com.cornerstone.module.education.dto.AnnouncementUpsertRequest;
import com.cornerstone.module.education.dto.AnnouncementView;
import com.cornerstone.module.education.dto.MyAnnouncementView;
import java.util.List;

/**
 * 公告中心：人工撰写、按范围广播、可撤回的单向站内公告。
 *
 * <p>与"系统事件"（课次变动通知）分家：这里只有人发布的公告，没有系统自动产生的消息。</p>
 * <p>全部方法的机构条件一律取自当前登录态，接口不接受 institutionId 参数。</p>
 */
public interface AnnouncementService {

    /** 管理端分页列表，含每行的已读人数。 */
    PageResult<AnnouncementView> pageAnnouncements(AnnouncementPageQuery query);

    /** 管理端详情。 */
    AnnouncementView getAnnouncement(Long id);

    /** 发布前按范围试算触达人数，不落库、不写接收人表。 */
    AnnouncementRecipientPreview previewRecipients(AnnouncementUpsertRequest request);

    /** 新建草稿。 */
    AnnouncementView createDraft(AnnouncementUpsertRequest request);

    /** 修改草稿；已发布/已撤回的公告正文冻结，不可改。 */
    AnnouncementView updateDraft(Long id, AnnouncementUpsertRequest request);

    /** 发布：解析接收人并展开成接收人行，写入分母与发布时间。 */
    AnnouncementView publish(Long id);

    /** 撤回：状态转 recalled，接收端即刻不可见；接收人行保留作留痕与分母。 */
    AnnouncementView recall(Long id, String reason);

    /** 删除草稿；已发布的公告只能撤回，不能删。 */
    void deleteDraft(Long id);

    /**
     * 当前机构的全部公告模板，按创建时间倒序。
     * 模板是"新建时预填"的小体量数据，套用的下拉一次要拿到全量，所以不分页。
     */
    List<AnnouncementTemplateView> listTemplates();

    /** 新建模板。范围字段按公告草稿同一套规则校验。 */
    AnnouncementTemplateView createTemplate(AnnouncementTemplateUpsertRequest request);

    /** 修改模板。模板不存"已发布"状态，任何时候都能改。 */
    AnnouncementTemplateView updateTemplate(Long id, AnnouncementTemplateUpsertRequest request);

    /** 删除模板。已按它发过的公告不受影响（公告不存模板溯源列）。 */
    void deleteTemplate(Long id);

    /** 接收端分页：我收到的公告，按置顶、发布时间倒序。 */
    PageResult<MyAnnouncementView> pageMine(int page, int pageSize, boolean unreadOnly);

    /** 接收端未读数。 */
    long countMyUnread();

    /** 接收端打开详情：非接收人 404，首次打开打已读时间。 */
    MyAnnouncementView openMine(Long id);
}
