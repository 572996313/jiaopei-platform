<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <article class="panel-card education-panel">
      <div class="table-toolbar">
        <el-button type="primary" @click="openCreate">新建事件类型</el-button>
        <el-button :icon="Refresh" circle :loading="loading" @click="load" />
        <NoteHint label="事件目录口径">
          这里配的是<b>事件类型</b>，不是代码：换数据源视图、调阈值（条件）、改文案模板、调级别/可见性/巡检间隔都在页面上完成。
          只有出现新的数据形状（新视图）才需要开发写迁移。条件只做<b>视图列 + 比较符 + 字面量</b
          >，不开任意 SQL。
          巡检<b>查询失败或候选行超限</b>时不会自动消解历史事件，失败原因会落到「巡检状态」里。
        </NoteHint>
      </div>

      <el-table
        :data="types"
        border
        row-key="typeCode"
        v-loading="loading"
        empty-text="还没有事件类型"
      >
        <el-table-column
          prop="typeName"
          label="类型"
          min-width="150"
          fixed="left"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <div class="mt-name">{{ row.typeName }}</div>
            <div class="mt-code">{{ row.typeCode }}</div>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="100">
          <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
        </el-table-column>
        <el-table-column label="产生方式" width="120">
          <template #default="{ row }">
            <el-tag :type="row.sourceMode === 'scan' ? 'warning' : 'info'" size="small">
              {{ row.sourceMode === 'scan' ? '配置化巡检' : '人工事项' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="级别" width="90">
          <template #default="{ row }">
            <el-tag :type="levelTag(row.level)" size="small">{{ levelLabel(row.level) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="可见端" width="100">
          <template #default="{ row }">{{ visibilityLabel(row.visibility) }}</template>
        </el-table-column>
        <el-table-column label="数据源" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{
            row.sourceMode === 'scan' ? row.sourceViewName || row.sourceCode || '—' : '—'
          }}</template>
        </el-table-column>
        <el-table-column label="巡检状态" min-width="210">
          <template #default="{ row }">
            <div v-if="row.sourceMode !== 'scan'" class="mt-muted">不巡检</div>
            <template v-else>
              <div class="mt-muted">
                {{ row.lastRunAt ? formatTime(row.lastRunAt) : '尚未执行' }}
                <span v-if="row.lastRunAt">· 命中 {{ row.lastRunCount ?? 0 }}</span>
              </div>
              <el-tooltip v-if="row.lastError" :content="row.lastError" placement="top">
                <el-tag type="danger" size="small">失败：{{ row.lastError }}</el-tag>
              </el-tooltip>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="mt-subhead">
        <h3>数据源视图目录</h3>
        <span class="mt-muted"
          >开发在迁移里登记只读视图；列契约可实时校验，视图改名/缺列在这里能直接看到</span
        >
      </div>
      <el-table :data="sources" border row-key="sourceCode" empty-text="还没有登记数据源视图">
        <el-table-column prop="displayName" label="名称" min-width="140" />
        <el-table-column prop="sourceCode" label="数据源代码" min-width="150" />
        <el-table-column prop="viewName" label="只读视图" min-width="180" />
        <el-table-column label="列" min-width="260">
          <template #default="{ row }">
            <div class="mt-columns">
              <el-tag v-for="column in row.columns" :key="column" size="small" type="info">
                {{ column }}
              </el-tag>
              <span v-if="row.columns.length === 0" class="mt-muted">读取失败</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="最近校验" width="150">
          <template #default="{ row }">{{ formatTime(row.lastValidatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :loading="validating === row.sourceCode"
              @click="validateSource(row)"
            >
              校验列
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </article>

    <el-dialog
      v-model="formVisible"
      :title="formMode === 'create' ? '新建事件类型' : `编辑事件类型：${form.typeCode}`"
      width="900px"
      top="4vh"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-position="top"
        class="education-form"
        scroll-to-error
        @submit.prevent
      >
        <div class="mt-grid">
          <el-form-item label="类型码" prop="typeCode">
            <el-input
              v-model="form.typeCode"
              :disabled="formMode === 'edit'"
              maxlength="64"
              placeholder="小写字母/数字/下划线，如 lesson_attendance_missing"
            />
          </el-form-item>
          <el-form-item label="类型名称" prop="typeName">
            <el-input v-model="form.typeName" maxlength="64" placeholder="如 课次未点名" />
          </el-form-item>
          <el-form-item label="分类" prop="category">
            <DictSelect
              v-model="form.category"
              type-code="message_category"
              placeholder="选择分类"
            />
          </el-form-item>
          <el-form-item label="产生方式" prop="sourceMode">
            <el-select v-model="form.sourceMode" @change="onSourceModeChange">
              <el-option
                v-for="item in MESSAGE_SOURCE_MODE_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="级别" prop="level">
            <el-select v-model="form.level">
              <el-option
                v-for="item in MESSAGE_LEVEL_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="可见端" prop="visibility">
            <el-select v-model="form.visibility">
              <el-option
                v-for="item in MESSAGE_VISIBILITY_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="巡检间隔（分钟）">
            <el-input-number
              v-model="form.intervalMinutes"
              :min="1"
              :max="1440"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="保留天数">
            <el-input-number
              v-model="form.retentionDays"
              :min="1"
              :max="3650"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="排序号">
            <el-input-number v-model="form.sortNo" :min="0" :max="9999" style="width: 100%" />
          </el-form-item>
          <el-form-item label="状态">
            <div class="mt-switches">
              <el-switch v-model="form.enabled" active-text="启用" />
              <el-switch
                v-if="form.sourceMode === 'manual'"
                v-model="form.creatable"
                active-text="允许手动新建"
              />
            </div>
          </el-form-item>
        </div>

        <template v-if="form.sourceMode === 'scan'">
          <el-form-item label="数据源视图" prop="sourceCode">
            <el-select
              v-model="form.sourceCode"
              placeholder="选择已登记的只读视图"
              style="width: 100%"
            >
              <el-option
                v-for="item in sources"
                :key="item.sourceCode"
                :label="`${item.displayName}（${item.viewName}）`"
                :value="item.sourceCode"
              />
            </el-select>
            <div v-if="sourceColumns.length > 0" class="mt-hint">
              可用列：
              <code v-for="column in sourceColumns" :key="column" class="mt-col-code">{{
                column
              }}</code>
            </div>
          </el-form-item>

          <el-form-item label="过滤条件（条件之间取 AND）">
            <div class="mt-conditions">
              <div
                v-for="(condition, index) in form.conditions"
                :key="index"
                class="mt-condition-row"
              >
                <el-select
                  v-model="condition.column"
                  filterable
                  allow-create
                  default-first-option
                  placeholder="视图列"
                  style="width: 190px"
                >
                  <el-option
                    v-for="column in sourceColumns"
                    :key="column"
                    :label="column"
                    :value="column"
                  />
                </el-select>
                <el-select v-model="condition.op" style="width: 150px">
                  <el-option
                    v-for="item in MESSAGE_CONDITION_OPS"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
                <el-input
                  v-model="condition.value"
                  placeholder="比较值；in 用逗号分隔"
                  style="width: 220px"
                />
                <el-button link type="danger" @click="form.conditions.splice(index, 1)"
                  >移除</el-button
                >
              </div>
              <el-button size="small" @click="addCondition">添加条件</el-button>
              <div class="mt-hint">
                条件为空表示命中视图全部候选行。阈值（如 30 分钟、未收 &gt;
                0）都写在这里，改阈值不用发版。
              </div>
            </div>
          </el-form-item>
        </template>

        <div class="mt-grid">
          <el-form-item label="标题模板" prop="titleTemplate">
            <el-input v-model="form.titleTemplate" maxlength="200" placeholder="如 课次未点名" />
          </el-form-item>
          <el-form-item label="去重键模板" prop="dedupeTemplate">
            <el-input
              v-model="form.dedupeTemplate"
              maxlength="300"
              placeholder="如 attendance_missing:lesson:${lesson_id}；同一业务对象同一问题只留一条"
            />
          </el-form-item>
        </div>

        <el-form-item label="正文模板">
          <el-input
            v-model="form.bodyTemplate"
            type="textarea"
            :rows="4"
            maxlength="5000"
            show-word-limit
            placeholder="如 ${class_name} ${lesson_date} 未完成点名（应到 ${expected} 人，已点 ${marked} 人）。"
          />
        </el-form-item>

        <el-form-item label="深链模板（可选）">
          <el-input
            v-model="form.actionTemplate"
            maxlength="500"
            placeholder="如 /schedule?date=${lesson_date}&focus=pending；渲染后按白名单校验"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import NoteHint from '@cornerstone/core/components/NoteHint.vue'
import DictSelect from '@cornerstone/core/components/DictSelect.vue'
import { useDictStore } from '@cornerstone/core/stores/dict'
import {
  MESSAGE_CONDITION_OPS,
  MESSAGE_LEVEL_OPTIONS,
  MESSAGE_SOURCE_MODE_OPTIONS,
  MESSAGE_VISIBILITY_OPTIONS,
  createMessageType,
  fetchMessageSources,
  fetchMessageTypes,
  updateMessageType,
  validateMessageSource,
  type MessageCondition,
  type MessageEventTypePayload,
  type MessageEventTypeRecord,
  type MessageSourceRecord,
} from '@cornerstone/module-education/api/message'

const dictStore = useDictStore()

const types = ref<MessageEventTypeRecord[]>([])
const sources = ref<MessageSourceRecord[]>([])
const loading = ref(false)
const pageError = ref('')
const validating = ref('')

const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const saving = ref(false)
const formRef = ref<FormInstance>()

interface ConditionForm {
  column: string
  op: string
  value: string
}

const form = reactive({
  typeCode: '',
  typeName: '',
  category: 'teaching',
  sourceMode: 'scan',
  sourceCode: '',
  conditions: [] as ConditionForm[],
  titleTemplate: '',
  bodyTemplate: '',
  actionTemplate: '',
  dedupeTemplate: '',
  level: 'warning',
  visibility: 'internal',
  intervalMinutes: 5,
  retentionDays: 90,
  creatable: false,
  enabled: true,
  sortNo: 0,
})

const formRules: FormRules = {
  typeCode: [
    { required: true, message: '请填写类型码', trigger: 'blur' },
    {
      pattern: /^[a-z][a-z0-9_]*$/,
      message: '只能由小写字母、数字、下划线组成，且以字母开头',
      trigger: 'blur',
    },
  ],
  typeName: [{ required: true, message: '请填写类型名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  sourceMode: [{ required: true, message: '请选择产生方式', trigger: 'change' }],
  level: [{ required: true, message: '请选择级别', trigger: 'change' }],
  visibility: [{ required: true, message: '请选择可见端', trigger: 'change' }],
  titleTemplate: [{ required: true, message: '请填写标题模板', trigger: 'blur' }],
}

const sourceColumns = computed(() => {
  return sources.value.find((item) => item.sourceCode === form.sourceCode)?.columns ?? []
})

onMounted(async () => {
  void dictStore.ensureLoaded(['message_category'])
  await load()
})

async function load() {
  loading.value = true
  pageError.value = ''
  try {
    const [typeList, sourceList] = await Promise.all([fetchMessageTypes(), fetchMessageSources()])
    types.value = typeList
    sources.value = sourceList
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '事件目录加载失败'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  formMode.value = 'create'
  Object.assign(form, {
    typeCode: '',
    typeName: '',
    category: 'teaching',
    sourceMode: 'scan',
    sourceCode: sources.value[0]?.sourceCode ?? '',
    conditions: [],
    titleTemplate: '',
    bodyTemplate: '',
    actionTemplate: '',
    dedupeTemplate: '',
    level: 'warning',
    visibility: 'internal',
    intervalMinutes: 5,
    retentionDays: 90,
    creatable: false,
    enabled: true,
    sortNo: 0,
  })
  formVisible.value = true
}

function openEdit(record: MessageEventTypeRecord) {
  formMode.value = 'edit'
  Object.assign(form, {
    typeCode: record.typeCode,
    typeName: record.typeName,
    category: record.category,
    sourceMode: record.sourceMode,
    sourceCode: record.sourceCode || '',
    conditions: (record.conditions ?? []).map<ConditionForm>((condition) => ({
      column: condition.column,
      op: condition.op,
      value:
        condition.value === null || condition.value === undefined ? '' : String(condition.value),
    })),
    titleTemplate: record.titleTemplate,
    bodyTemplate: record.bodyTemplate || '',
    actionTemplate: record.actionTemplate || '',
    dedupeTemplate: record.dedupeTemplate || '',
    level: record.level,
    visibility: record.visibility,
    intervalMinutes: record.intervalMinutes ?? 5,
    retentionDays: record.retentionDays ?? 90,
    creatable: record.creatable ?? false,
    enabled: record.enabled ?? true,
    sortNo: record.sortNo ?? 0,
  })
  formVisible.value = true
}

function onSourceModeChange() {
  if (form.sourceMode === 'manual') {
    form.creatable = true
    form.intervalMinutes = 1
    form.retentionDays = 365
  } else {
    form.creatable = false
    if (!form.sourceCode) {
      form.sourceCode = sources.value[0]?.sourceCode ?? ''
    }
  }
}

function addCondition() {
  form.conditions.push({ column: '', op: 'gt', value: '' })
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (form.sourceMode === 'scan') {
    if (!form.sourceCode) {
      ElMessage.warning('巡检类型必须选择数据源视图')
      return
    }
    if (!form.dedupeTemplate.trim()) {
      ElMessage.warning('巡检类型必须配置去重键模板')
      return
    }
  }
  const payload: MessageEventTypePayload = {
    typeCode: form.typeCode.trim(),
    typeName: form.typeName.trim(),
    category: form.category,
    sourceMode: form.sourceMode,
    sourceCode: form.sourceMode === 'scan' ? form.sourceCode : undefined,
    conditions:
      form.sourceMode === 'scan'
        ? form.conditions
            .filter((condition) => condition.column.trim() && condition.value !== '')
            .map<MessageCondition>((condition) => ({
              column: condition.column.trim(),
              op: condition.op,
              value: condition.value,
            }))
        : [],
    titleTemplate: form.titleTemplate,
    bodyTemplate: form.bodyTemplate,
    actionTemplate: form.actionTemplate || undefined,
    dedupeTemplate: form.dedupeTemplate,
    level: form.level,
    visibility: form.visibility,
    intervalMinutes: form.intervalMinutes,
    retentionDays: form.retentionDays,
    creatable: form.creatable,
    enabled: form.enabled,
    sortNo: form.sortNo,
  }
  saving.value = true
  try {
    if (formMode.value === 'create') {
      await createMessageType(payload)
      ElMessage.success('事件类型已创建')
    } else {
      await updateMessageType(form.typeCode, payload)
      ElMessage.success('事件类型已更新')
    }
    formVisible.value = false
    await load()
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function validateSource(source: MessageSourceRecord) {
  validating.value = source.sourceCode
  try {
    const refreshed = await validateMessageSource(source.sourceCode)
    const row = sources.value.find((item) => item.sourceCode === refreshed.sourceCode)
    if (row) {
      row.columns = refreshed.columns
      row.lastValidatedAt = refreshed.lastValidatedAt
    }
    ElMessage.success(`已校验 ${refreshed.viewName}，共 ${refreshed.columns.length} 列`)
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '校验失败')
  } finally {
    validating.value = ''
  }
}

function categoryLabel(value?: string | null) {
  return dictStore.displayLabel('message_category', value)
}

function levelLabel(value?: string | null) {
  return MESSAGE_LEVEL_OPTIONS.find((item) => item.value === value)?.label || value || '提示'
}

function levelTag(value?: string | null) {
  return MESSAGE_LEVEL_OPTIONS.find((item) => item.value === value)?.tag || 'info'
}

function visibilityLabel(value?: string | null) {
  return MESSAGE_VISIBILITY_OPTIONS.find((item) => item.value === value)?.label || value || '—'
}

function formatTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}
</script>

<style scoped>
.mt-name {
  font-weight: 600;
  color: var(--text-primary);
}

.mt-code {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--text-muted);
}

.mt-muted {
  font-size: 12px;
  color: var(--text-muted);
}

.mt-columns {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.mt-subhead {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin: 22px 0 10px;
}

.mt-subhead h3 {
  margin: 0;
  font-size: 14px;
  color: var(--text-primary);
}

.mt-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 16px;
}

.mt-switches {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  padding-top: 4px;
}

.mt-conditions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.mt-condition-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.mt-hint {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-muted);
}

.mt-col-code {
  padding: 0 5px;
  border-radius: 4px;
  background: var(--subtle-bg);
  color: var(--text-secondary);
  font-size: 11.5px;
}

@media (max-width: 900px) {
  .mt-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
