<template>
  <section class="education-page">
    <article class="panel-card education-panel">
      <div class="table-toolbar">
        <el-button
          v-if="permissions.canWriteInstitution"
          type="primary"
          :loading="institutionSubmitting"
          @click="submitInstitutionProfile"
          >保存机构资料</el-button
        >
        <el-button
          :icon="Refresh"
          circle
          :loading="institutionLoading"
          @click="loadCurrentInstitutionProfile"
        />
        <div class="education-context-text">机构 ID 与机构编码只读,不提供多机构切换。</div>
      </div>

      <el-alert
        v-if="institutionSectionError"
        :title="institutionSectionError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />

      <div v-loading="institutionLoading" class="institution-profile-shell">
        <div class="dashboard-grid education-stat-grid">
          <article class="stat-card">
            <div class="stat-label">当前机构</div>
            <div class="stat-value institution-stat-text">{{ currentInstitutionTitle }}</div>
            <div class="stat-hint">{{ currentInstitution?.shortName || '未设置简称' }}</div>
          </article>
          <article class="stat-card">
            <div class="stat-label">机构编码</div>
            <div class="stat-value institution-stat-text">
              {{ currentInstitution?.orgCode || '-' }}
            </div>
            <div class="stat-hint">只读,由后端机构档案返回</div>
          </article>
          <article class="stat-card">
            <div class="stat-label">联系人</div>
            <div class="stat-value institution-stat-text">
              {{ currentInstitution?.contactName || '-' }}
            </div>
            <div class="stat-hint">{{ currentInstitution?.contactPhone || '未设置联系电话' }}</div>
          </article>
          <article class="stat-card">
            <div class="stat-label">机构状态</div>
            <div class="stat-value institution-stat-text">
              {{ statusLabel(currentInstitution?.status, statusLabelMap) }}
            </div>
            <div class="stat-hint">用于当前机构基础启停展示</div>
          </article>
        </div>

        <el-form
          ref="institutionFormRef"
          :model="institutionForm"
          :rules="institutionRules"
          label-position="top"
          class="education-form"
          scroll-to-error
          @submit.prevent
        >
          <div class="system-form-grid">
            <el-form-item label="机构 ID">
              <el-input v-model="institutionForm.id" disabled placeholder="后端返回机构 ID" />
            </el-form-item>
            <el-form-item label="机构编码">
              <el-input v-model="institutionForm.orgCode" disabled placeholder="后端返回机构编码" />
            </el-form-item>
            <el-form-item label="机构名称" prop="name">
              <el-input
                v-model="institutionForm.name"
                maxlength="120"
                placeholder="请输入机构名称"
              />
            </el-form-item>
            <el-form-item label="机构简称">
              <el-input
                v-model="institutionForm.shortName"
                maxlength="80"
                placeholder="请输入机构简称"
              />
            </el-form-item>
            <el-form-item label="联系人">
              <el-input
                v-model="institutionForm.contactName"
                maxlength="80"
                placeholder="请输入联系人"
              />
            </el-form-item>
            <el-form-item label="联系电话">
              <el-input
                v-model="institutionForm.contactPhone"
                maxlength="40"
                placeholder="请输入联系电话"
              />
            </el-form-item>
            <el-form-item label="机构状态">
              <el-select v-model="institutionForm.status" placeholder="选择状态">
                <el-option
                  v-for="item in statusOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </div>

          <el-form-item label="机构地址">
            <el-input
              v-model="institutionForm.address"
              maxlength="200"
              placeholder="请输入机构地址"
            />
          </el-form-item>
          <el-form-item label="备注">
            <el-input
              v-model="institutionForm.remark"
              type="textarea"
              :rows="3"
              maxlength="500"
              placeholder="补充机构说明"
            />
          </el-form-item>
        </el-form>
      </div>
    </article>
  </section>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { fetchCurrentInstitution, updateCurrentInstitution } from '@cornerstone/core/api/system'
import type { CurrentInstitutionProfile } from '@cornerstone/core/api/system'
import {
  ensureStatusDictLoaded,
  statusLabel,
  toMessage,
  trimToUndefined,
  useStatusLabelMap,
  useStatusOptions,
  useSystemPermissions,
} from './shared'

const auth = useAuthStore()
const permissions = useSystemPermissions()

const statusOptions = useStatusOptions()
const statusLabelMap = useStatusLabelMap()

const institutionSectionError = ref('')
const institutionLoading = ref(false)
const institutionSubmitting = ref(false)
const currentInstitution = ref<CurrentInstitutionProfile | null>(null)

const institutionFormRef = ref<FormInstance | null>(null)

const institutionForm = reactive({
  id: '',
  orgCode: '',
  name: '',
  shortName: '',
  contactName: '',
  contactPhone: '',
  address: '',
  status: 'active',
  remark: '',
})

const institutionRules: FormRules = {
  name: [{ required: true, whitespace: true, message: '请填写机构名称', trigger: 'blur' }],
}

const currentInstitutionTitle = computed(() => {
  const profile = currentInstitution.value
  if (!profile) {
    return formatInstitution(auth.currentUser?.institutionId)
  }
  return profile.name || profile.shortName || profile.orgCode || `机构 #${profile.id}`
})

function formatInstitution(id?: string | null) {
  if (!id) {
    return '未设置'
  }
  return currentInstitution.value?.id === id
    ? currentInstitution.value.name ||
        currentInstitution.value.shortName ||
        currentInstitution.value.orgCode ||
        `机构 #${id}`
    : `机构 #${id}`
}

function populateInstitutionForm(profile: CurrentInstitutionProfile | null) {
  institutionForm.id = profile?.id || ''
  institutionForm.orgCode = profile?.orgCode || ''
  institutionForm.name = profile?.name || ''
  institutionForm.shortName = profile?.shortName || ''
  institutionForm.contactName = profile?.contactName || ''
  institutionForm.contactPhone = profile?.contactPhone || ''
  institutionForm.address = profile?.address || ''
  institutionForm.status = profile?.status || statusOptions.value[0]?.value || 'active'
  institutionForm.remark = profile?.remark || ''
}

async function loadCurrentInstitutionProfile() {
  institutionLoading.value = true
  institutionSectionError.value = ''
  try {
    const profile = await fetchCurrentInstitution()
    currentInstitution.value = profile
    populateInstitutionForm(profile)
    // 重新拉取资料后清掉上次保存失败留下的红框
    void nextTick(() => institutionFormRef.value?.clearValidate())
  } catch (error) {
    currentInstitution.value = null
    populateInstitutionForm(null)
    institutionSectionError.value = toMessage(error, '当前机构资料加载失败,请确认账号权限')
  } finally {
    institutionLoading.value = false
  }
}

async function submitInstitutionProfile() {
  // 校验失败时 el-form 已经在字段上标红并给出提示，这里不再补一条顶部总文案
  const valid = await institutionFormRef.value?.validate().catch(() => false)
  if (valid === false) {
    return
  }
  const name = institutionForm.name.trim()

  institutionSubmitting.value = true
  institutionSectionError.value = ''
  try {
    const profile = await updateCurrentInstitution({
      name,
      shortName: trimToUndefined(institutionForm.shortName),
      contactName: trimToUndefined(institutionForm.contactName),
      contactPhone: trimToUndefined(institutionForm.contactPhone),
      address: trimToUndefined(institutionForm.address),
      status: institutionForm.status || 'active',
      remark: trimToUndefined(institutionForm.remark),
    })
    currentInstitution.value = profile
    populateInstitutionForm(profile)
    ElMessage.success('机构资料已保存')
  } catch (error) {
    institutionSectionError.value = toMessage(error, '机构资料保存失败,请确认账号权限')
  } finally {
    institutionSubmitting.value = false
  }
}

onMounted(async () => {
  await Promise.allSettled([ensureStatusDictLoaded(), loadCurrentInstitutionProfile()])
})
</script>
