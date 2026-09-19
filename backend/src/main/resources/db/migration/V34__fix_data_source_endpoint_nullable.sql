-- V34: 修正 sys_data_source.endpoint 为可空(dict 类型数据源不需要接口路径)
-- dict 类型数据源(source_type=dict)只依赖 dict_type 取字典选项,不填 endpoint;
-- 原 V33 将 endpoint 建成 NOT NULL 无默认值,导致创建 dict 数据源时 SQL 报错
-- "Field 'endpoint' doesn't have a default value"。

ALTER TABLE sys_data_source
    MODIFY COLUMN endpoint VARCHAR(128) NULL COMMENT '接口路径(source_type=api 时必填,如 /organizations/institutions)';