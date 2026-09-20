package com.cornerstone.core.system.module;

/**
 * 模块自述信息：由每个模块自己提供实现，底座只负责收集展示。
 *
 * <p>存在的意义是把"平台装了哪些模块"从底座代码里的硬编码清单变成注册表：
 * 新增业务模块时不需要改 {@code PlatformBootstrapController} / {@code OpenApiConfig}，
 * 只要在自己的 jar 里注册一个实现（记得加 {@code @Order} 决定展示顺序）。</p>
 *
 * <p>注意语义：实现类随模块装配才注册，所以关闭的模块不会出现在清单里，
 * 而不是出现成"未启用"。</p>
 */
public interface ModuleDescriptor {

    /** 模块码，与 {@code cornerstone.modules.<code>-enabled} 开关同名（底座固定能力用 auth）。 */
    String code();

    /** 中文展示名。 */
    String name();

    /** 当前开关状态（读 {@code CornerstoneProperties}，便于排查"以为装了其实没装"）。 */
    boolean enabled();

    /** 就绪状态说明，展示在启动信息里。 */
    String status();
}
