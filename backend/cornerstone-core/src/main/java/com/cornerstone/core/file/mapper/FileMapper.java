package com.cornerstone.core.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.core.file.domain.entity.FileEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface FileMapper extends BaseMapper<FileEntity> {

    /**
     * 物理删除。文件行不留墓碑：对象键是 UUID、永不重用，"删→重建同键"不存在；
     * 留着墓碑只会让桶里的对象与表行对不上。删除动作的审计由操作日志承担。
     *
     * <p>唯一键仍带 {@code deleted} 列（底座基线守护的硬要求），物理删除下该列恒为 0，
     * 不产生额外语义。</p>
     */
    @Delete("DELETE FROM sys_file WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
