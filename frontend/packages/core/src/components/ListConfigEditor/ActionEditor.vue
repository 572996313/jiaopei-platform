<template>
  <TableEditorShell v-model="modelValue" :create-row="createRow">
    <el-table-column prop="label" label="按钮文案 label" min-width="120">
      <template #default="{ row }">
        <el-input v-model="row.label" size="small" placeholder="如 新增用户" />
      </template>
    </el-table-column>

    <el-table-column prop="action" label="动作名 action" min-width="170">
      <template #default="{ row }">
        <el-input v-model="row.action" size="small" placeholder="如 user-create" />
      </template>
    </el-table-column>

    <el-table-column prop="position" label="位置" width="110">
      <template #default="{ row }">
        <el-select v-model="row.position" size="small" filterable>
          <el-option
            v-for="opt in ACTION_POSITION_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </template>
    </el-table-column>

    <el-table-column prop="buttonType" label="样式" width="110">
      <template #default="{ row }">
        <el-select v-model="row.buttonType" size="small" clearable filterable placeholder="默认">
          <el-option
            v-for="opt in ACTION_BUTTON_TYPE_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </template>
    </el-table-column>

    <el-table-column prop="permission" label="权限码 permission" min-width="200">
      <template #default="{ row }">
        <el-select
          v-model="row.permission"
          size="small"
          allow-create
          filterable
          clearable
          placeholder="可下拉选或手输"
        >
          <el-option
            v-for="item in permissionOptions"
            :key="item.code"
            :label="`${item.label}(${item.code})`"
            :value="item.code"
          />
        </el-select>
      </template>
    </el-table-column>

    <template #expand="{ row }">
      <div class="te-expand-grid">
        <el-form-item label="点击行为 actionType(不填自动按动作名推断)">
          <el-select
            v-model="row.actionType"
            size="small"
            clearable
            filterable
            placeholder="自动推断"
          >
            <el-option
              v-for="opt in ACTION_TYPE_OPTIONS"
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
        action 必须是页面代码里注册的动作名,写错按钮不渲染;权限码控制谁能看到。
        <a
          href="https://element-plus.org/zh-CN/component/button.html"
          target="_blank"
          rel="noopener"
          >按钮文档 ↗</a
        >
      </span>
    </template>
  </TableEditorShell>
</template>

<script setup lang="ts">
import { permissionGroups } from '@cornerstone/core/constants/permissionLabels'
import TableEditorShell from './TableEditorShell.vue'
import {
  ACTION_BUTTON_TYPE_OPTIONS,
  ACTION_POSITION_OPTIONS,
  ACTION_TYPE_OPTIONS,
  type ActionRow,
} from './types'

const modelValue = defineModel<ActionRow[]>({ required: true })

/** 全部权限码(按分组),供 permission 下拉选择 */
const permissionOptions = permissionGroups().flatMap((group) => group.items)

function createRow(): ActionRow {
  return {
    action: '',
    label: '',
    position: 'toolbar',
    buttonType: undefined,
    permission: undefined,
    actionType: undefined,
  }
}
</script>

<style scoped src="./editor.css"></style>
