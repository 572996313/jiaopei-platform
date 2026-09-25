package com.cornerstone.module.education.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.education.domain.entity.MsgAnnouncementReceiverEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 公告接收人。
 *
 * <p>这里的四段范围解析 SQL 是本功能唯一"新长出来"的数据访问：全仓原本没有
 * "校区/班级 → 该发给哪些登录账号"的反查，教师靠 jp_teacher.user_id、
 * 家长靠 jp_class_student → jp_user_student_rel.user_id 两条链路。</p>
 */
public interface MsgAnnouncementReceiverMapper extends BaseMapper<MsgAnnouncementReceiverEntity> {

    /**
     * 教师档：校区范围内已开通且启用登录账号的教师。
     *
     * <p>教师档案没绑账号（user_id 为 NULL）的人不在结果里，前端要在"预计触达"里提示补绑。</p>
     */
    @Select("""
        <script>
        SELECT DISTINCT t.user_id
        FROM jp_teacher t
        JOIN sys_user_account ua ON ua.id = t.user_id AND ua.deleted = 0 AND ua.status = 'active'
        WHERE t.deleted = 0
          AND t.institution_id = #{institutionId}
          AND t.user_id IS NOT NULL
        <if test="campusIds != null and campusIds.size() > 0">
          AND t.campus_id IN
          <foreach collection="campusIds" item="campusId" open="(" separator="," close=")">#{campusId}</foreach>
        </if>
        </script>
        """)
    List<Long> selectTeacherUserIds(@Param("institutionId") Long institutionId,
                                    @Param("campusIds") List<Long> campusIds);

    /**
     * 学员家长档：范围内在读学员所绑定的小程序账号。
     *
     * <p>家长端与学生端账号都落在 jp_user_student_rel，不按小程序角色拆分——公告要发给"这个孩子的家里"，
     * 绑在这个学员上的账号都该收到。校区范围经 jp_class.campus_id 生效；给定班级时以班级为准。</p>
     */
    @Select("""
        <script>
        SELECT DISTINCT rel.user_id
        FROM jp_class_student cs
        JOIN jp_class clazz ON clazz.id = cs.class_id AND clazz.deleted = 0
        JOIN jp_user_student_rel rel ON rel.student_id = cs.student_id AND rel.deleted = 0
        JOIN sys_user_account ua ON ua.id = rel.user_id AND ua.deleted = 0 AND ua.status = 'active'
        WHERE cs.deleted = 0
          AND cs.status = 'active'
          AND cs.institution_id = #{institutionId}
        <if test="classIds != null and classIds.size() > 0">
          AND cs.class_id IN
          <foreach collection="classIds" item="classId" open="(" separator="," close=")">#{classId}</foreach>
        </if>
        <if test="(classIds == null or classIds.size() == 0) and campusIds != null and campusIds.size() > 0">
          AND clazz.campus_id IN
          <foreach collection="campusIds" item="campusId" open="(" separator="," close=")">#{campusId}</foreach>
        </if>
        </script>
        """)
    List<Long> selectParentUserIds(@Param("institutionId") Long institutionId,
                                   @Param("campusIds") List<Long> campusIds,
                                   @Param("classIds") List<Long> classIds);

    /**
     * 全部登录账号档：机构下启用状态的账号。
     *
     * <p>账号本身没有校区归属（jp_user_campus_pref 只是列表页的默认筛选偏好），
     * 所以这一档不受校区范围约束，前端勾选时要说明。</p>
     */
    @Select("SELECT ua.id FROM sys_user_account ua "
        + "WHERE ua.deleted = 0 AND ua.status = 'active' AND ua.institution_id = #{institutionId}")
    List<Long> selectAccountUserIds(@Param("institutionId") Long institutionId);

    /**
     * 范围内**没有**绑定任何登录账号的学员数，用于"预计触达"下方的补绑提示。
     */
    @Select("""
        <script>
        SELECT COUNT(DISTINCT cs.student_id)
        FROM jp_class_student cs
        JOIN jp_class clazz ON clazz.id = cs.class_id AND clazz.deleted = 0
        LEFT JOIN jp_user_student_rel rel ON rel.student_id = cs.student_id AND rel.deleted = 0
        WHERE cs.deleted = 0
          AND cs.status = 'active'
          AND cs.institution_id = #{institutionId}
          AND rel.id IS NULL
        <if test="classIds != null and classIds.size() > 0">
          AND cs.class_id IN
          <foreach collection="classIds" item="classId" open="(" separator="," close=")">#{classId}</foreach>
        </if>
        <if test="(classIds == null or classIds.size() == 0) and campusIds != null and campusIds.size() > 0">
          AND clazz.campus_id IN
          <foreach collection="campusIds" item="campusId" open="(" separator="," close=")">#{campusId}</foreach>
        </if>
        </script>
        """)
    Long countUnboundStudents(@Param("institutionId") Long institutionId,
                              @Param("campusIds") List<Long> campusIds,
                              @Param("classIds") List<Long> classIds);

    /**
     * 我收到的公告ID，按置顶、发布时间倒序分页。
     *
     * <p>只看 status='published'：草稿与已撤回的公告对接收人不存在，但接收人行保留作留痕与分母。</p>
     */
    @Select("""
        <script>
        SELECT a.id
        FROM jp_msg_announcement a
        JOIN jp_msg_announcement_receiver rel ON rel.announcement_id = a.id AND rel.deleted = 0
        WHERE rel.user_id = #{userId}
          AND a.institution_id = #{institutionId}
          AND a.deleted = 0
          AND a.status = 'published'
        <if test="unreadOnly">
          AND rel.read_at IS NULL
        </if>
        ORDER BY a.top_flag DESC, a.published_at DESC, a.id DESC
        LIMIT #{offset}, #{size}
        </script>
        """)
    List<Long> selectMyAnnouncementIds(@Param("userId") Long userId,
                                       @Param("institutionId") Long institutionId,
                                       @Param("unreadOnly") boolean unreadOnly,
                                       @Param("offset") long offset,
                                       @Param("size") long size);

    @Select("""
        <script>
        SELECT COUNT(1)
        FROM jp_msg_announcement a
        JOIN jp_msg_announcement_receiver rel ON rel.announcement_id = a.id AND rel.deleted = 0
        WHERE rel.user_id = #{userId}
          AND a.institution_id = #{institutionId}
          AND a.deleted = 0
          AND a.status = 'published'
        <if test="unreadOnly">
          AND rel.read_at IS NULL
        </if>
        </script>
        """)
    Long countMyAnnouncements(@Param("userId") Long userId,
                              @Param("institutionId") Long institutionId,
                              @Param("unreadOnly") boolean unreadOnly);

    /**
     * 打读：只在未读时写时间，重复打开保持首次已读时间。
     *
     * <p>靠 read_at IS NULL 条件保证幂等，不加乐观锁；并发重复打开最坏是多打一次相同时间，不影响分母与分子。</p>
     */
    @Update("UPDATE jp_msg_announcement_receiver SET read_at = #{readAt} "
        + "WHERE announcement_id = #{announcementId} AND user_id = #{userId} AND read_at IS NULL AND deleted = 0")
    int markRead(@Param("announcementId") Long announcementId,
                 @Param("userId") Long userId,
                 @Param("readAt") LocalDateTime readAt);
}
