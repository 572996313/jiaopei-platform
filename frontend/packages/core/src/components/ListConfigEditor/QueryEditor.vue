<template>
  <TableEditorShell v-model="modelValue" :create-row="createRow">
    <el-table-column prop="field" label="查询参数名 field" min-width="150">
      <template #default="{ row }">
        <el-input v-model="row.field" size="small" placeholder="如 username" />
      </template>
    </el-table-column>

    <el-table-column prop="label" label="标签 label" min-width="110">
      <template #default="{ row }">
        <el-input v-model="row.label" size="small" placeholder="如 账号" />
      </template>
    </el-table-column>

    <el-table-column prop="type" label="控件类型 type" width="160">
      <template #default="{ row }">
        <el-select v-model="row.type" size="small" filterable>
          <el-option
            v-for="opt in QUERY_TYPE_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </template>
    </el-table-column>

    <el-table-column prop="placeholder" label="占位提示" min-width="140">
      <template #default="{ row }">
        <el-input v-model="row.placeholder" size="small" placeholder="框内灰色提示" />
      </template>
    </el-table-column>

    <template #expand="{ row }">
      <div class="te-expand-grid">
        <el-form-item label="默认值 defaultValue">
          <el-input v-model="row.defaultValue" size="small" placeholder="进入页面默认带上的值" />
        </el-form-item>

        <template v-if="row.type === 'dict-select'">
          <el-form-item label="字典类型 dictType">
            <el-input v-model="row.dictType" size="small" placeholder="如 common_status" />
          </el-form-item>
        </template>

        <template v-if="row.type === 'select'">
          <el-form-item label="选项来源" class="full-span">
            <el-radio-group
              :model-value="modeOf(row)"
              @update:model-value="setMode(row, $event as string)"
            >
              <el-radio value="options">手填选项</el-radio>
              <el-radio value="loader">用现成数据源(optionsLoader)</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item
            v-if="modeOf(row) === 'loader'"
            label="数据源 optionsLoader"
            class="full-span"
          >
            <el-select
              v-model="row.optionsLoader"
              size="small"
              clearable
              filterable
              allow-create
              default-first-option
              placeholder="选择底座数据源,或输入已配置的业务数据源编码"
            >
              <el-option
                v-for="opt in OPTIONS_LOADER_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-else label="静态选项 options" class="full-span">
            <KeyValueEditor
              v-model="row.options"
              key-placeholder="选项值,如 active"
              value-placeholder="显示文案,如 启用"
            />
          </el-form-item>
        </template>
      </div>
    </template>

    <template #tip>
      <span class="block-tip">
        field 必须是后端分页接口支持的查询参数,否则填了不生效。
        业务数据源先在「数据源配置」里配好编码,再在下拉里直接输入该编码。
        <a
          href="https://element-plus.org/zh-CN/component/select.html"
          target="_blank"
          rel="noopener"
          >下拉文档 ↗</a
        >
      </span>
    </template>
  </TableEditorShell>
</template>

<script setup lang="ts">
import KeyValueEditor from './KeyValueEditor.vue'
import TableEditorShell from './TableEditorShell.vue'
import { OPTIONS_LOADER_OPTIONS, QUERY_TYPE_OPTIONS, type QueryRow } from './types'

const modelValue = defineModel<QueryRow[]>({ required: true })

function createRow(): QueryRow {
  return {
    field: '',
    label: '',
    type: 'input',
    options: [],
  }
}

/** 读当前「选项来源」:有静态选项行视为手填模式,否则视为数据源模式 */
function modeOf(row: QueryRow): 'options' | 'loader' {
  return (row.options ?? []).length > 0 ? 'options' : 'loader'
}

/** 切换选项来源,清空另一侧,避免同时提交冲突(后端要求二选一) */
function setMode(row: QueryRow, mode: string) {
  if (mode === 'options') {
    row.optionsLoader = undefined
    if (!row.options) {
      row.options = []
    }
  } else {
    row.options = []
    if (!row.optionsLoader) {
      row.optionsLoader = ''
    }
  }
}
</script>

<style scoped src="./editor.css"></style>
