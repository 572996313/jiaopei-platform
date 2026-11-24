package com.cornerstone.core.system.service;

import com.cornerstone.core.system.dto.EndpointChainView;

/**
 * 接口链路：以接口为入口，把权限码、界面按钮、控制器、服务与数据表串成一条只读的静态链路。
 *
 * <p>它回答"点这个按钮会经过哪些代码和数据表"，不回答"这次实际改了什么"。
 * 链路靠运行时反射推导，不写死模块名，也不引入手工维护的登记表。</p>
 */
public interface EndpointChainService {

    /** 全量接口链路；结果进程内缓存 60 秒 */
    EndpointChainView chains();
}
