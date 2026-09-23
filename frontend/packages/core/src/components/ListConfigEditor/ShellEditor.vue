<template>
  <div class="editor-blocks">
    <!-- 页面壳 pageJson -->
    <div class="block-card">
      <div class="block-card-head">
        <span class="block-card-title">页面显示(页面壳)</span>
        <span class="block-card-sub">说明与空态文案(页面标题取菜单名,不在此配置)</span>
      </div>
      <div class="form-grid">
        <el-form-item label="标题下方说明 description" class="full-span">
          <el-input v-model="page.description" placeholder="只写「怎么生效」这类说明(选填)" />
        </el-form-item>
        <el-form-item label="空数据文案 emptyText" class="full-span">
          <el-input v-model="page.emptyText" placeholder="暂无数据" />
        </el-form-item>
      </div>
    </div>

    <!-- 弹窗壳 dialogJson -->
    <div class="block-card">
      <div class="block-card-head">
        <span class="block-card-title">弹窗显示(弹窗壳)</span>
        <span class="block-card-sub">新增/编辑弹窗的宽度、标题、按钮文案</span>
      </div>
      <div class="form-grid">
        <el-form-item label="弹窗宽度 width">
          <el-input v-model="dialog.width" placeholder="如 680px" />
        </el-form-item>
        <el-form-item label="新增标题 createTitle">
          <el-input v-model="dialog.createTitle" placeholder="如 新增用户" />
        </el-form-item>
        <el-form-item label="编辑标题 editTitle">
          <el-input v-model="dialog.editTitle" placeholder="如 编辑用户" />
        </el-form-item>
        <el-form-item label="提交按钮文案 submitText">
          <el-input v-model="dialog.submitText" placeholder="保存" />
        </el-form-item>
        <el-form-item label="取消按钮文案 cancelText">
          <el-input v-model="dialog.cancelText" placeholder="取消" />
        </el-form-item>
        <div class="empty-item" />
        <el-form-item label="新增成功提示 createMessage">
          <el-input v-model="dialog.createMessage" placeholder="如 用户已创建" />
        </el-form-item>
        <el-form-item label="编辑成功提示 updateMessage">
          <el-input v-model="dialog.updateMessage" placeholder="如 用户已更新" />
        </el-form-item>
        <el-form-item label="提交后刷新列表">
          <el-switch v-model="dialog.reloadAfterSubmit" />
        </el-form-item>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { DialogShellState, PageShellState } from './types'

defineModel<PageShellState>('page', { required: true })
defineModel<DialogShellState>('dialog', { required: true })
</script>

<style scoped src="./editor.css"></style>
