package com.cornerstone.core.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.cornerstone.module.education.fee.mapper.FeeLedgerMapper;
import com.cornerstone.module.education.fee.service.FeeLedgerService;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import org.junit.jupiter.api.Test;

/**
 * 接口链路里两条纯推导口径：Mapper 泛型 → 实体 → 表名，以及"哪些字段算服务"。
 *
 * <p>这两处一旦漂移，页面不会报错，只会安静地少掉整段链路（表变空、服务不展开），
 * 所以单独锁死；真正的端到端校验依赖 Spring 上下文，不放在单元测试里。</p>
 */
class EndpointChainServiceImplTest {

    @Test
    void mapperGenericResolvesToEntityAndTable() {
        assertThat(EndpointChainServiceImpl.entityTypeOf(FeeLedgerMapper.class).getSimpleName())
            .isEqualTo("FeeLedgerEntity");
        assertThat(EndpointChainServiceImpl.tableNameOf(
            EndpointChainServiceImpl.entityTypeOf(FeeLedgerMapper.class)))
            .isEqualTo("jp_fee_ledger");

        assertThat(EndpointChainServiceImpl.tableNameOf(
            EndpointChainServiceImpl.entityTypeOf(TeachingClassMapper.class)))
            .isEqualTo("jp_class");
    }

    @Test
    void serviceDetectionFollowsNamingConvention() {
        assertThat(EndpointChainServiceImpl.looksLikeService(FeeLedgerService.class)).isTrue();
        assertThat(EndpointChainServiceImpl.looksLikeService(FeeLedgerMapper.class)).isFalse();
        assertThat(EndpointChainServiceImpl.looksLikeService(String.class)).isFalse();
    }

    @Test
    void nonMapperTypeHasNoEntity() {
        // 解析不到泛型时返回 null，调用方据此跳过——不能猜一个表名出来
        assertThat(EndpointChainServiceImpl.entityTypeOf(String.class)).isNull();
    }

    @Test
    void noteKeepsDeclarationNotInvocationWording() {
        // 页面最容易被误读成审计日志；口径一旦被改软，实施人员就会拿它判断"这次改了哪几张表"
        assertThat(EndpointChainServiceImpl.NOTE)
            .contains("声明依赖")
            .contains("不是某一次请求的实际调用记录")
            .contains("不区分本次读了还是写了");
    }
}
