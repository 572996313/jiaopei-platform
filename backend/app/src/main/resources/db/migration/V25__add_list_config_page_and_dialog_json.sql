-- V25: sys_list_config 增加 page_json / dialog_json(页面壳、弹窗壳配置)
-- 三个内置资源(system-users / system-roles / system-configs)补初始壳配置,
-- 与改造前页面硬编码的标题/描述/弹窗文案保持一致,上线后页面视觉与行为零变化。
-- 置空(NULL/空串)时前端回退默认壳,不影响现有页面。

ALTER TABLE sys_list_config
    ADD COLUMN page_json TEXT NULL COMMENT '页面壳配置 JSON(title/description/showCount/countFormat/emptyText),为空时前端回退默认壳'
    AFTER form_json;

ALTER TABLE sys_list_config
    ADD COLUMN dialog_json TEXT NULL COMMENT '弹窗壳配置 JSON(width/createTitle/editTitle/submitText/cancelText/createMessage/updateMessage/reloadAfterSubmit),为空时前端回退默认弹窗'
    AFTER page_json;

-- 用户管理:页面壳 + 弹窗壳(与改造前手写文案一致)
UPDATE sys_list_config SET
    page_json = '{
      "title": "用户列表",
      "description": "支持创建、编辑、启停、重置密码、分配角色。",
      "showCount": true,
      "countFormat": "共 {total} 条",
      "emptyText": "暂无数据"
    }',
    dialog_json = '{
      "width": "640px",
      "createTitle": "新增用户",
      "editTitle": "编辑用户",
      "submitText": "保存",
      "cancelText": "取消",
      "createMessage": "用户已创建",
      "updateMessage": "用户已更新",
      "reloadAfterSubmit": true
    }'
WHERE resource_code = 'system-users' AND deleted = 0;

-- 角色管理:页面壳 + 弹窗壳
UPDATE sys_list_config SET
    page_json = '{
      "title": "角色列表",
      "description": "支持角色维护,并可进一步分配菜单树。",
      "showCount": true,
      "countFormat": "共 {total} 条",
      "emptyText": "暂无数据"
    }',
    dialog_json = '{
      "width": "680px",
      "createTitle": "新增角色",
      "editTitle": "编辑角色",
      "submitText": "保存",
      "cancelText": "取消",
      "createMessage": "角色已创建",
      "updateMessage": "角色已更新",
      "reloadAfterSubmit": true
    }'
WHERE resource_code = 'system-roles' AND deleted = 0;

-- 系统参数:页面壳 + 弹窗壳
UPDATE sys_list_config SET
    page_json = '{
      "title": "系统参数",
      "description": "维护全局键值参数(token 有效期等),支持新增、编辑与删除。",
      "showCount": true,
      "countFormat": "共 {total} 条",
      "emptyText": "暂无数据"
    }',
    dialog_json = '{
      "width": "640px",
      "createTitle": "新增系统参数",
      "editTitle": "编辑系统参数",
      "submitText": "保存",
      "cancelText": "取消",
      "createMessage": "系统参数已创建",
      "updateMessage": "系统参数已更新",
      "reloadAfterSubmit": true
    }'
WHERE resource_code = 'system-configs' AND deleted = 0;