<template>
  <div class="json-panel">
    <el-alert type="info" :closable="false" show-icon class="json-alert">
      <template #title>JSON 模式适合熟悉配置结构的人;新手建议切回「表单模式」按字段填。</template>
    </el-alert>

    <el-form-item label="列配置 columnsJson">
      <el-input v-model="columns" type="textarea" :rows="9" placeholder='[{"prop":"username","label":"账号","width":150}]' />
      <div class="education-context-text">
        每列 prop/label 必填;type 可选 text/datetime/dict/boolean/tags/map;type=dict 需 dictType,可配 tagTypeMap;type=map 需 valueMap;
        renderer 可选 institutionName/campusName/permissionTags/passwordStatus。
      </div>
    </el-form-item>
    <el-form-item label="查询条件 queryJson">
      <el-input v-model="query" type="textarea" :rows="6" placeholder='[{"field":"keyword","label":"关键字","type":"input"}]' />
      <div class="education-context-text">
        每项 field/label 必填;type 可选 input/select/dict-select/date;type=dict-select 需 dictType;type=select 可配 options 或 optionsLoader=institutions;
        field 必须是后端分页接口支持的查询参数,否则查询不生效。
      </div>
    </el-form-item>
    <el-form-item label="按钮配置 actionsJson">
      <el-input v-model="actions" type="textarea" :rows="7" placeholder='[{"action":"xxx-create","label":"新增","position":"toolbar","buttonType":"primary"}]' />
      <div class="education-context-text">
        每项 action/label/position 必填;position= toolbar(表格上方)或 row(行内);buttonType 可选 primary/default/text/danger;
        permission 为权限码,无权限自动隐藏;action 必须是对应列表页已注册的动作名,写错会导致按钮不渲染。
      </div>
    </el-form-item>
    <el-form-item label="弹窗表单 formJson">
      <el-input v-model="form" type="textarea" :rows="10" placeholder='{"labelWidth":"auto","gridColumns":2,"fields":[{"field":"displayName","label":"显示名称","type":"input","required":true}]}' />
      <div class="education-context-text">
        可选,描述该资源的"新增/编辑"弹窗;空表示未配置(列表页弹窗会提示先配置)。整体为 JSON 对象,fields 为非空数组;
        每个字段 field/label/type 必填;type 可选 input/password/textarea/number/select/dict-select/switch/checkbox-group/radio/permission-groups;
        select 可配 options 或 optionsLoader(institutions/campuses/roles/status);dict-select 需 dictType;
        showOn/disabledOn 可选 create/edit;required 标必填;gridSpan 1(半行)/2(整行);onChange 填页面注册的联动钩子名。
      </div>
    </el-form-item>
    <el-form-item label="页面壳配置 pageJson">
      <el-input v-model="page" type="textarea" :rows="8" placeholder='{"title":"系统参数","description":"维护全局键值参数","showCount":true,"countFormat":"共 {total} 条","emptyText":"暂无数据"}' />
      <div class="education-context-text">
        可选,描述该资源的页面主标题/副标题说明/计数/空态,保存后立即生效;空表示未配置,页面走默认。
        均为可选字段:title(页面大标题,缺省显示资源名)、description(标题右侧说明)、
        showCount(布尔,是否显示"共 X 条")、countFormat(计数模板,{total} 为占位符)、
        emptyText(表格空态文案)。
      </div>
    </el-form-item>
    <el-form-item label="弹窗壳配置 dialogJson">
      <el-input v-model="dialog" type="textarea" :rows="8" placeholder='{"width":"680px","createTitle":"新增角色","editTitle":"编辑角色","createMessage":"角色已创建","updateMessage":"角色已更新"}' />
      <div class="education-context-text">
        可选,描述该资源"新增/编辑"弹窗的展示外壳;空表示未配置,弹窗走默认(640px 宽、"新增/编辑{name}" 标题)。
        均为可选字段:width(弹窗宽度)、createTitle/editTitle(新增/编辑标题)、submitText/cancelText(底部按钮文案)、
        createMessage/updateMessage(成功提示,{name} 为资源名占位符)、reloadAfterSubmit(布尔,提交后是否刷新列表)。
      </div>
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
const columns = defineModel<string>('columns', { required: true })
const query = defineModel<string>('query', { required: true })
const actions = defineModel<string>('actions', { required: true })
const form = defineModel<string>('form', { required: true })
const page = defineModel<string>('page', { required: true })
const dialog = defineModel<string>('dialog', { required: true })
</script>

<style scoped>
.json-panel {
  display: grid;
  gap: 12px;
}

.json-alert {
  margin-bottom: 4px;
}
</style>