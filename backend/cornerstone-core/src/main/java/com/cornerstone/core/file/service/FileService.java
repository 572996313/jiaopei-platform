package com.cornerstone.core.file.service;

import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.file.dto.FileContent;
import com.cornerstone.core.file.dto.FileListView;
import com.cornerstone.core.file.dto.FilePageQuery;
import com.cornerstone.core.file.dto.FileView;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 通用文件能力。业务模块只依赖本接口的 {@link #bind} / {@link #unbind} 与 fileId 契约，
 * 不要直接操作 {@code sys_file}。
 */
public interface FileService {

    /** 上传文件，归属机构取当前登录态，不接受调用方指定。 */
    FileView upload(MultipartFile file, String bizType, Long bizId);

    /** 查询元数据；跨机构访问一律拒绝（平台管理员除外）。 */
    FileView getFile(Long fileId);

    /** 取字节流；权限与租户校验同 {@link #getFile}。 */
    FileContent openContent(Long fileId);

    /** 文件管理分页，平台管理员可看全机构，其余只看本机构。 */
    PageResult<FileListView> pageFiles(FilePageQuery query);

    /** 删除文件；已被业务数据引用时拒绝。 */
    void deleteFile(Long fileId);

    /** 清理超过保留时长且未绑定业务的孤儿文件，返回清理条数。 */
    int cleanupOrphans();

    /**
     * 把文件绑定到业务记录，供业务 Service 在自己的事务里调用。
     *
     * <p>不对外暴露 HTTP 接口：公开的绑定入口等于允许任意用户把文件挂到任意业务记录上。</p>
     */
    void bind(List<Long> fileIds, String bizType, Long bizId);

    /** 解除绑定，文件转为孤儿，由清理任务回收。 */
    void unbind(List<Long> fileIds);

    /**
     * 按业务归属批量解除绑定（不依赖登录态），供账号注销等系统清理路径调用。
     *
     * <p>只解除绑定、不直接删对象：文件转为孤儿后由清理任务统一回收，避免在事务里
     * 做不可回滚的存储删除。{@link #unbind} 要求登录态与同机构校验，不能用于事件监听器。</p>
     */
    void releaseBoundByBiz(String bizType, Long bizId);

    /** 上传人账号注销时清空归属，文件本身是机构资产，不随账号删除。 */
    void clearUploader(Long userId);
}
