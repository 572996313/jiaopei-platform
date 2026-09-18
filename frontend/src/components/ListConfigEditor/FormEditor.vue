<template>
  <div class="form-editor">
    <!-- 弹窗表单整页设置 -->
    <div class="form-page-bar">
      <div class="form-page-label">弹窗表单整体设置</div>
      <el-form-item label="栅格列数(一行几个字段)" class="form-page-item">
        <el-input-number v-model="modelValue.gridColumns" :min="1" :max="4" size="small" />
      </el-form-item>
      <el-form-item label="标签宽度" class="form-page-item">
        <el-input v-model="modelValue.labelWidth" size="small" placeholder="如 100px 或 auto" />
      </el-form-item>
    </div>

    <!-- 字段表格:一行一个字段 -->
    <TableEditorShell v-model="fields" :create-row="createRow">
      <el-table-column prop="label" label="字段标题 label" min-width="110">
        <template #default="{ row }">
          <el-input v-model="row.label" size="small" placeholder="如 显示名称" />
        </template>
      </el-table-column>

      <el-table-column prop="field" label="字段名 field" min-width="140">
        <template #default="{ row }">
          <el-input v-model="row.field" size="small" placeholder="如 displayName" />
        </template>
      </el-table-column>

      <el-table-column prop="type" label="控件类型 type" width="180">
        <template #default="{ row }">
          <el-select v-model="row.type" size="small" filterable>
            <el-option v-for="opt in FORM_FIELD_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </template>
      </el-table-column>

      <el-table-column prop="required" label="必填" width="62">
        <template #default="{ row }">
          <el-switch v-model="row.required" />
        </template>
      </el-table-column>

      <el-table-column prop="gridSpan" label="占位" width="100">
        <template #default="{ row }">
          <el-select v-model="row.gridSpan" size="small" clearable placeholder="默认">
            <el-option v-for="opt in GRID_SPAN_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </template>
      </el-table-column>

      <el-table-column prop="placeholder" label="占位提示" min-width="140">
        <template #default="{ row }">
          <el-input v-model="row.placeholder" size="small" placeholder="框内灰色提示" />
        </template>
      </el-table-column>

      <!-- 展开行:选项来源/字典/默认值/显示禁用/长度限制等次要属性 -->
      <template #expand="{ row }">
        <div class="te-expand-grid">
          <el-form-item label="提示文字 tip">
            <el-input v-model="row.tip" size="small" placeholder="字段下方的灰色说明" />
          </el-form-item>
          <el-form-item label="可清空">
            <el-switch v-model="row.clearable" />
          </el-form-item>

          <el-form-item
            v-if="row.type === 'select' || row.type === 'radio' || row.type === 'checkbox-group' || row.type === 'dict-select'"
            label="选项来源"
            class="full-span"
          >
            <el-radio-group :model-value="modeOf(row)" @update:model-value="setMode(row, $event as string)">
              <template v-if="row.type === 'dict-select'">
                <el-radio value="loader">字典(dictType)</el-radio>
              </template>
              <template v-else>
                <el-radio value="options">手填选项</el-radio>
                <el-radio value="loader">用现成数据源(optionsLoader)</el-radio>
              </template>
            </el-radio-group>
          </el-form-item>

          <el-form-item
            v-if="(row.type === 'select' || row.type === 'radio' || row.type === 'checkbox-group') && modeOf(row) === 'options'"
            label="静态选项 options"
            class="full-span"
          >
            <KeyValueEditor
              v-model="row.options"
              key-placeholder="选项值,如 active"
              value-placeholder="显示文案,如 启用"
            />
          </el-form-item>

          <el-form-item
            v-if="row.type === 'dict-select' || ((row.type === 'select' || row.type === 'radio' || row.type === 'checkbox-group') && modeOf(row) === 'loader')"
            :label="row.type === 'dict-select' ? '字典类型 dictType' : '数据源 optionsLoader'"
          >
            <el-select
              v-if="row.type === 'dict-select'"
              v-model="row.dictType"
              size="small"
              allow-create
              filterable
              clearable
              placeholder="如 common_status"
            />
            <el-select v-else v-model="row.optionsLoader" size="small" clearable filterable placeholder="选择数据源">
              <el-option v-for="opt in OPTIONS_LOADER_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>

          <el-form-item label="默认值 defaultValue">
            <el-input v-model="row.defaultValue" size="small" :placeholder="defaultValuePlaceholder(row.type)" />
          </el-form-item>

          <el-form-item label="模式显示/禁用" class="full-span">
            <div class="te-mode-checks">
              <el-checkbox-group v-model="row.showOn" size="small">
                <el-checkbox value="create">仅新增时显示</el-checkbox>
                <el-checkbox value="edit">仅编辑时显示</el-checkbox>
              </el-checkbox-group>
              <el-checkbox-group v-model="row.disabledOn" size="small">
                <el-checkbox value="create">新增时禁用</el-checkbox>
                <el-checkbox value="edit">编辑时禁用</el-checkbox>
              </el-checkbox-group>
            </div>
          </el-form-item>

          <template v-if="isTextish(row.type)">
            <el-form-item label="最多输入 maxlength">
              <el-input-number v-model="row.maxlength" size="small" :min="0" :controls="false" placeholder="不限" />
            </el-form-item>
          </template>

          <template v-if="row.type === 'number'">
            <el-form-item label="最小值 min">
              <el-input-number v-model="row.min" size="small" :controls="false" placeholder="不限" />
            </el-form-item>
            <el-form-item label="最大值 max">
              <el-input-number v-model="row.max" size="small" :controls="false" placeholder="不限" />
            </el-form-item>
          </template>

          <el-form-item v-if="row.type === 'textarea'" label="行数 rows">
            <el-input-number v-model="row.rows" size="small" :min="1" :max="20" />
          </el-form-item>

          <el-form-item label="联动 onChange(一般不用填)">
            <el-input v-model="row.onChange" size="small" placeholder="页面注册的联动钩子名" />
          </el-form-item>
        </div>
      </template>

      <template #tip>
        <span class="block-tip">
          field 与保存接口字段一致;type、必填、默认值在此设置。查看
          <a href="https://element-plus.org/zh-CN/component/form.html" target="_blank" rel="noopener">表单文档 ↗</a>
        </span>
      </template>
    </TableEditorShell>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import KeyValueEditor from './KeyValueEditor.vue'
import TableEditorShell from './TableEditorShell.vue'
import {
  FORM_FIELD_TYPE_OPTIONS,
  GRID_SPAN_OPTIONS,
  OPTIONS_LOADER_OPTIONS,
  type FormConfigState,
  type FormFieldRow,
} from './types'

const modelValue = defineModel<FormConfigState>({ required: true })

// 表格壳只处理字段数组;整页配置(gridColumns/labelWidth)由本组件直接绑 modelValue
const fields = computed({
  get: () => modelValue.value.fields,
  set: (value) => {
    modelValue.value.fields = value
  },
})

/** 文字类控件(带 placeholder / maxlength) */
function isTextish(type: string) {
  return type === 'input' || type === 'password'
}

function defaultValuePlaceholder(type: string) {
  if (type === 'switch') {
    return '填 true 或 false'
  }
  if (type === 'number') {
    return '填数字'
  }
  return '选填'
}

function modeOf(field: FormFieldRow): 'options' | 'loader' {
  if (field.type === 'dict-select') {
    return 'loader'
  }
  return (field.options ?? []).length > 0 ? 'options' : 'loader'
}

function setMode(field: FormFieldRow, mode: string) {
  if (mode === 'options') {
    field.optionsLoader = undefined
    if (!field.options) {
      field.options = []
    }
  } else {
    field.options = []
    if (!field.optionsLoader) {
      field.optionsLoader = ''
    }
  }
}

function createRow(): FormFieldRow {
  return {
    field: '',
    label: '',
    type: 'input',
    options: [],
    showOn: [],
    disabledOn: [],
  }
}
</script>

<style scoped src="./editor.css"></style>