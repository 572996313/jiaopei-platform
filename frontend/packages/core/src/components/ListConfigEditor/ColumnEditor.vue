<template>
  <TableEditorShell v-model="modelValue" :create-row="createRow" class="te-col-editor">
    <!-- 主列:字段名 / 标题 / 渲染类型 / 宽度 —— 一行一条,上下对齐方便对比 -->
    <el-table-column prop="prop" label="数据字段名 prop" min-width="140">
      <template #default="{ row }">
        <el-input v-model="row.prop" size="small" placeholder="如 username" />
      </template>
    </el-table-column>

    <el-table-column prop="label" label="列标题 label" min-width="120">
      <template #default="{ row }">
        <el-input v-model="row.label" size="small" placeholder="如 账号" />
      </template>
    </el-table-column>

    <el-table-column prop="type" label="渲染类型 type" width="170">
      <template #default="{ row }">
        <el-select v-model="row.type" size="small" filterable>
          <el-option
            v-for="opt in COLUMN_TYPE_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </template>
    </el-table-column>

    <el-table-column prop="width" label="宽度 width" width="110">
      <template #default="{ row }">
        <el-input-number
          v-model="row.width"
          size="small"
          :min="0"
          :controls="false"
          placeholder="自适应"
        />
      </template>
    </el-table-column>

    <el-table-column prop="minWidth" label="最小宽" width="100">
      <template #default="{ row }">
        <el-input-number
          v-model="row.minWidth"
          size="small"
          :min="0"
          :controls="false"
          placeholder="自适应"
        />
      </template>
    </el-table-column>

    <!-- 展开行:跟类型相关的次要属性 -->
    <template #expand="{ row }">
      <div class="te-expand-grid">
        <el-form-item label="超出省略(内容过长显示…+悬浮全文)">
          <el-switch v-model="row.showOverflowTooltip" />
        </el-form-item>

        <template v-if="row.type === 'dict'">
          <el-form-item label="字典类型 dictType">
            <el-input v-model="row.dictType" size="small" placeholder="如 common_status" />
          </el-form-item>
          <el-form-item label="标签颜色 tagTypeMap" class="full-span">
            <KeyValueEditor
              v-model="row.tagTypeMap"
              key-placeholder="值,如 active"
              value-placeholder="颜色,如 success"
              :value-options="TAG_COLOR_OPTIONS"
            />
          </el-form-item>
        </template>

        <template v-if="row.type === 'boolean'">
          <el-form-item label="「是」文案 trueLabel">
            <el-input v-model="row.trueLabel" size="small" placeholder="默认 是" />
          </el-form-item>
          <el-form-item label="「否」文案 falseLabel">
            <el-input v-model="row.falseLabel" size="small" placeholder="默认 否" />
          </el-form-item>
        </template>

        <el-form-item v-if="row.type === 'map'" label="值映射 valueMap" class="full-span">
          <KeyValueEditor
            v-model="row.valueMap"
            key-placeholder="后端值,如 auth"
            value-placeholder="显示文案,如 认证"
          />
        </el-form-item>

        <el-form-item label="自定义渲染器 renderer">
          <el-select
            v-model="row.renderer"
            size="small"
            clearable
            filterable
            placeholder="特殊列才需要"
          >
            <el-option
              v-for="opt in RENDERER_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
      </div>
    </template>

    <template #tip>
      <span class="block-tip">
        prop 必须与后端返回的字段名一致;label 是表头中文。查看
        <a href="https://element-plus.org/zh-CN/component/table.html" target="_blank" rel="noopener"
          >表格文档 ↗</a
        >
      </span>
    </template>
  </TableEditorShell>
</template>

<script setup lang="ts">
import KeyValueEditor from './KeyValueEditor.vue'
import TableEditorShell from './TableEditorShell.vue'
import { COLUMN_TYPE_OPTIONS, RENDERER_OPTIONS, type ColumnRow } from './types'

const modelValue = defineModel<ColumnRow[]>({ required: true })

function createRow(): ColumnRow {
  return {
    prop: '',
    label: '',
    type: 'text',
    width: null,
    minWidth: null,
    showOverflowTooltip: false,
    tagTypeMap: [],
    valueMap: [],
  }
}

const TAG_COLOR_OPTIONS = [
  { value: 'primary', label: 'primary(蓝色)' },
  { value: 'success', label: 'success(绿色)' },
  { value: 'warning', label: 'warning(橙色)' },
  { value: 'info', label: 'info(灰色)' },
  { value: 'danger', label: 'danger(红色)' },
]
</script>

<style scoped src="./editor.css"></style>
