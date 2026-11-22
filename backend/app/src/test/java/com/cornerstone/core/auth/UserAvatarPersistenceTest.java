package com.cornerstone.core.auth;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.auth.mapper.UserAccountMapper;
import com.cornerstone.core.auth.service.UserAvatarService;
import com.cornerstone.core.auth.service.impl.UserAvatarPersistence;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.file.service.FileService;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAvatarPersistenceTest {

    @BeforeAll
    static void initEntityTableInfo() {
        // 单测环境没有 MyBatis 启动流程，手动注册实体元数据，wrapper 的 lambda 才能解析出列名
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, UserAccountEntity.class);
    }

    private final UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
    private final FileService fileService = mock(FileService.class);
    private final UserAvatarPersistence persistence = new UserAvatarPersistence(userAccountMapper, fileService);

    @Test
    void replaceBindsNewFileAndUnbindsPreviousOne() {
        when(userAccountMapper.selectOne(any())).thenReturn(user(111L));

        Long old = persistence.replace(1L, 222L);

        assertEquals(111L, old.longValue());
        verify(userAccountMapper).update(isNull(), any());
        verify(fileService).bind(List.of(222L), UserAvatarService.BIZ_TYPE, 1L);
        verify(fileService).unbind(List.of(111L));
    }

    @Test
    void replaceSkipsWorkWhenAvatarDoesNotChange() {
        when(userAccountMapper.selectOne(any())).thenReturn(user(111L));

        Long old = persistence.replace(1L, 111L);

        assertEquals(111L, old.longValue());
        verify(userAccountMapper, never()).update(any(), any());
        verify(fileService, never()).bind(any(), any(), any());
        verify(fileService, never()).unbind(any());
    }

    @Test
    void clearWritesNullAndUnbindsPreviousFile() {
        when(userAccountMapper.selectOne(any())).thenReturn(user(111L));

        persistence.clear(1L);

        verify(userAccountMapper).update(isNull(), any());
        verify(fileService).unbind(List.of(111L));
    }

    @Test
    void clearIsNoOpWhenThereIsNoAvatar() {
        when(userAccountMapper.selectOne(any())).thenReturn(user(null));

        persistence.clear(1L);

        verify(userAccountMapper, never()).update(any(), any());
        verify(fileService, never()).unbind(any());
    }

    @Test
    void releaseUnbindsByBusinessOwnership() {
        persistence.release(1L);

        verify(fileService).releaseBoundByBiz(UserAvatarService.BIZ_TYPE, 1L);
    }

    @Test
    void missingAccountIsRejected() {
        when(userAccountMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> persistence.replace(1L, 222L));
    }

    private static UserAccountEntity user(Long avatarFileId) {
        UserAccountEntity user = new UserAccountEntity();
        user.setId(1L);
        user.setAvatarFileId(avatarFileId);
        return user;
    }
}
