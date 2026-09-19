<template>
  <section class="education-page">
    <article class="panel-card education-panel">
      <article v-if="dataSourceSectionError" class="education-empty">
        {{ dataSourceSectionError }}
      </article>
      <template v-else>
        <div class="education-table-head">
          <div class="panel-title">
            数据源配置<span class="panel-count">共 {{ dataSourceRecords.length }} 条</span>
          </div>
          <div class="table-actions">
            <span class="education-context-text"
              >维护页面下拉选项的数据源(接口字段映射),改一条配置即可切换接口,无需改代码。</span
            >
            <el-button @click="guideDialogVisible = true">帮助</el-button>
            <el-button
              v-if="permissions.canManageDataSources"
              type="primary"
              @click="openCreateDialog"
              >新增数据源</el-button
            >
          </div>
        </div>

        <el-table
          :data="dataSourceRecords"
          border
          v-loading="dataSourceLoading"
          :height="TABLE_FILL_HEIGHT"
        >
          <el-table-column
            prop="sourceCode"
            label="数据源编码"
            min-width="140"
            show-overflow-tooltip
          />
          <el-table-column prop="sourceName" label="名称" min-width="120" show-overflow-tooltip />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">
              <el-tag :type="row.sourceType === 'dict' ? 'warning' : 'primary'" size="small">{{
                sourceTypeLabel(row.sourceType)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="接口路径 / 字典类型" min-width="210">
            <template #default="{ row }">
              <code class="source-endpoint">{{
                row.sourceType === 'dict' ? row.dictType : row.endpoint
              }}</code>
            </template>
          </el-table-column>
          <el-table-column label="值/标签字段" min-width="130">
            <template #default="{ row }">
              <span v-if="row.sourceType === 'api'" class="source-keys"
                >{{ row.valueKey || 'id' }} → {{ row.labelKey }}</span
              >
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{
                statusLabel(row.status, statusLabelMap)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
          <el-table-column
            v-if="permissions.canManageDataSources"
            label="操作"
            width="140"
            fixed="right"
          >
            <template #default="{ row }">
              <div class="system-operation-group">
                <el-button text type="primary" @click="openEditDialog(row)">编辑</el-button>
                <el-button
                  text
                  type="danger"
                  :disabled="seedDataSourceIds.has(row.id)"
                  @click="handleDelete(row)"
                  >删除</el-button
                >
              </div>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </article>

    <el-dialog
      v-model="dataSourceDialogVisible"
      :title="dataSourceDialogMode === 'create' ? '新增数据源' : '编辑数据源'"
      width="640px"
    >
      <el-alert
        v-if="dataSourceFormError"
        :title="dataSourceFormError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />
      <el-form label-position="top" class="education-form">
        <div class="source-form-grid">
          <el-form-item label="数据源编码">
            <el-input
              v-model="dataSourceForm.sourceCode"
              :disabled="dataSourceDialogMode === 'edit'"
              placeholder="如 institutions,建后不可改"
            />
          </el-form-item>
          <el-form-item label="名称">
            <el-input
              v-model="dataSourceForm.sourceName"
              maxlength="64"
              placeholder="如 机构列表"
            />
          </el-form-item>
        </div>
        <el-form-item label="数据源类型">
          <el-radio-group v-model="dataSourceForm.sourceType">
            <el-radio value="api">接口数据(api)</el-radio>
            <el-radio value="dict">字典(dict)</el-radio>
          </el-radio-group>
        </el-form-item>

        <template v-if="dataSourceForm.sourceType === 'api'">
          <el-form-item label="接口路径">
            <el-input
              v-model="dataSourceForm.endpoint"
              maxlength="128"
              placeholder="如 /organizations/institutions"
            />
          </el-form-item>
          <div class="source-form-grid">
            <el-form-item label="选项值字段(缺省 id)">
              <el-input v-model="dataSourceForm.valueKey" maxlength="64" placeholder="如 id" />
            </el-form-item>
            <el-form-item label="选项标签字段">
              <el-input v-model="dataSourceForm.labelKey" maxlength="64" placeholder="如 orgName" />
            </el-form-item>
          </div>
          <el-form-item label="请求参数定义(JSON 数组,选填)">
            <el-input
              v-model="dataSourceForm.paramDefs"
              type="textarea"
              :rows="3"
              placeholder='如 [{"name":"institutionId","required":false}]'
            />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="字典类型编码">
            <el-input
              v-model="dataSourceForm.dictType"
              maxlength="64"
              placeholder="如 common_status"
            />
          </el-form-item>
        </template>

        <div class="source-form-grid">
          <el-form-item label="状态">
            <el-select v-model="dataSourceForm.status">
              <el-option
                v-for="item in statusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="说明">
            <el-input
              v-model="dataSourceForm.description"
              maxlength="500"
              placeholder="选填,说明该数据源的用途"
            />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dataSourceDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dataSourceSubmitting" @click="submit">
          {{ dataSourceDialogMode === 'create' ? '创建数据源' : '保存编辑' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 帮助弹窗 -->
    <el-dialog v-model="guideDialogVisible" title="数据源配置说明" width="640px">
      <div class="guide-section">
        <h4 class="guide-title">这是什么?</h4>
        <p class="guide-text">
          数据源把「页面下拉框、筛选框的选项数据」收敛成一条配置: 编码(如 institutions)→ 接口路径 /
          字典类型 → 选项的值字段、标签字段。 「列表配置」里下拉控件的
          <code>optionsLoader</code>
          填的就是数据源编码。
        </p>
      </div>
      <div class="guide-section">
        <h4 class="guide-title">改接口要怎么做?</h4>
        <ul class="guide-list">
          <li>编辑这条数据源,把「接口路径」换成新地址,保存即可。</li>
          <li>返回字段变了,同步改「值字段 / 标签字段」。</li>
          <li>所有引用该编码的页面下次加载立即生效,不用改代码、不用发版。</li>
        </ul>
      </div>
      <div class="guide-section">
        <h4 class="guide-title">请求参数怎么配?</h4>
        <p class="guide-text">
          「请求参数定义」是 JSON 数组,每项含
          <code>name</code>
          (与接口参数同名)。前端组件按名字传参,例如校区表依赖机构:
          <code>[{"name":"institutionId","required":false}]</code>。
        </p>
      </div>
      <div class="guide-section">
        <h4 class="guide-title">内置数据源能删吗?</h4>
        <p class="guide-text">
          前 6 条(机构/校区/学期/教室/时段/角色)是内置的,支撑现有页面,不能删除,可改为停用。
        </p>
      </div>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { TABLE_FILL_HEIGHT } from '@/utils/table-view'
import { onMounted, reactive, ref } from 'vue'
import {
  createDataSource,
  deleteDataSource,
  fetchDataSources,
  updateDataSource,
} from '@/api/system'
import type { DataSourceSummary } from '@/api/system'
import { useDataSourceStore } from '@/stores/dataSource'
import {
  ensureStatusDictLoaded,
  statusLabel,
  statusTagType,
  toMessage,
  trimToUndefined,
  useStatusLabelMap,
  useStatusOptions,
} from './shared'
import { useSystemPermissions } from './shared'

const dataSourceStore = useDataSourceStore()
const permissions = useSystemPermissions()
const statusOptions = useStatusOptions()
const statusLabelMap = useStatusLabelMap()

const dataSourceSectionError = ref('')
const dataSourceLoading = ref(false)
const dataSourceRecords = ref<DataSourceSummary[]>([])
const dataSourceDialogVisible = ref(false)
const dataSourceDialogMode = ref<'create' | 'edit'>('create')
const dataSourceSubmitting = ref(false)
const dataSourceFormError = ref('')
const selectedDataSource = ref<DataSourceSummary | null>(null)
// V33 种子的六条内置数据源(id 1-6)支撑现有页面,后端禁删,前端同步禁用删除按钮
const seedDataSourceIds = new Set(['1', '2', '3', '4', '5', '6'])

const guideDialogVisible = ref(false)

const dataSourceForm = reactive({
  sourceCode: '',
  sourceName: '',
  endpoint: '',
  valueKey: '',
  labelKey: '',
  paramDefs: '',
  sourceType: 'api',
  dictType: '',
  description: '',
  status: 'active',
})

function sourceTypeLabel(type: string) {
  return type === 'dict' ? '字典' : '接口'
}

/* ---------------- 数据加载 ---------------- */

async function loadDataSources() {
  dataSourceLoading.value = true
  dataSourceSectionError.value = ''
  try {
    dataSourceRecords.value = await fetchDataSources()
  } catch (error) {
    dataSourceRecords.value = []
    dataSourceSectionError.value = toMessage(error, '数据源加载失败')
  } finally {
    dataSourceLoading.value = false
  }
}

/* ---------------- 打开 / 关闭 ---------------- */

function openCreateDialog() {
  dataSourceDialogMode.value = 'create'
  selectedDataSource.value = null
  dataSourceForm.sourceCode = ''
  dataSourceForm.sourceName = ''
  dataSourceForm.endpoint = ''
  dataSourceForm.valueKey = ''
  dataSourceForm.labelKey = ''
  dataSourceForm.paramDefs = ''
  dataSourceForm.sourceType = 'api'
  dataSourceForm.dictType = ''
  dataSourceForm.description = ''
  dataSourceForm.status = 'active'
  dataSourceFormError.value = ''
  dataSourceDialogVisible.value = true
}

function openEditDialog(row: DataSourceSummary) {
  dataSourceDialogMode.value = 'edit'
  selectedDataSource.value = row
  dataSourceForm.sourceCode = row.sourceCode
  dataSourceForm.sourceName = row.sourceName
  dataSourceForm.endpoint = row.endpoint || ''
  dataSourceForm.valueKey = row.valueKey || ''
  dataSourceForm.labelKey = row.labelKey || ''
  dataSourceForm.paramDefs = row.paramDefs || ''
  dataSourceForm.sourceType = row.sourceType || 'api'
  dataSourceForm.dictType = row.dictType || ''
  dataSourceForm.description = row.description || ''
  dataSourceForm.status = row.status || 'active'
  dataSourceFormError.value = ''
  dataSourceDialogVisible.value = true
}

/** 校验 paramDefs 是合法 JSON 数组(空串跳过);每项需含 name */
function validateParamDefs(raw: string) {
  const trimmed = raw.trim()
  if (!trimmed) {
    return undefined
  }
  let parsed: unknown
  try {
    parsed = JSON.parse(trimmed)
  } catch (error) {
    throw new Error(toMessage(error, '请求参数定义不是合法的 JSON'))
  }
  if (!Array.isArray(parsed)) {
    throw new Error('请求参数定义必须是 JSON 数组,如 [{"name":"institutionId","required":false}]')
  }
  for (let i = 0; i < parsed.length; i++) {
    const item = parsed[i]
    if (
      typeof item !== 'object' ||
      item === null ||
      typeof item.name !== 'string' ||
      !item.name.trim()
    ) {
      throw new Error(`请求参数定义[${i}] 缺少必填字段 name`)
    }
  }
  return trimmed
}

/* ---------------- 提交 ---------------- */

async function submit() {
  if (dataSourceDialogMode.value === 'create' && !trimToUndefined(dataSourceForm.sourceCode)) {
    dataSourceFormError.value = '请填写数据源编码'
    return
  }
  if (!trimToUndefined(dataSourceForm.sourceName)) {
    dataSourceFormError.value = '请填写名称'
    return
  }
  if (dataSourceForm.sourceType === 'api' && !trimToUndefined(dataSourceForm.endpoint)) {
    dataSourceFormError.value = '请填写接口路径'
    return
  }
  if (dataSourceForm.sourceType === 'dict' && !trimToUndefined(dataSourceForm.dictType)) {
    dataSourceFormError.value = '请填写字典类型编码'
    return
  }

  let paramDefs: string | undefined
  try {
    paramDefs = validateParamDefs(dataSourceForm.paramDefs)
  } catch (error) {
    dataSourceFormError.value = toMessage(error, '校验失败')
    return
  }

  const common = {
    sourceName: dataSourceForm.sourceName.trim(),
    endpoint: trimToUndefined(dataSourceForm.endpoint),
    valueKey: trimToUndefined(dataSourceForm.valueKey),
    labelKey: trimToUndefined(dataSourceForm.labelKey),
    paramDefs,
    sourceType: dataSourceForm.sourceType,
    dictType: trimToUndefined(dataSourceForm.dictType),
    description: trimToUndefined(dataSourceForm.description),
    status: dataSourceForm.status,
  }

  dataSourceSubmitting.value = true
  dataSourceFormError.value = ''
  try {
    if (dataSourceDialogMode.value === 'create') {
      await createDataSource({
        ...common,
        sourceCode: dataSourceForm.sourceCode.trim(),
        method: 'GET',
      })
      ElMessage.success('数据源已创建')
    } else if (selectedDataSource.value) {
      await updateDataSource(selectedDataSource.value.id, common)
      ElMessage.success('数据源已更新')
    }
    dataSourceDialogVisible.value = false
    dataSourceStore.invalidate()
    await loadDataSources()
  } catch (error) {
    dataSourceFormError.value = toMessage(
      error,
      dataSourceDialogMode.value === 'create' ? '创建数据源失败' : '保存数据源失败'
    )
  } finally {
    dataSourceSubmitting.value = false
  }
}

async function handleDelete(row: DataSourceSummary) {
  try {
    await ElMessageBox.confirm(
      `确定删除数据源「${row.sourceName || row.sourceCode}」吗?引用该数据源的页面下拉将无选项。`,
      '删除确认',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  try {
    await deleteDataSource(row.id)
    ElMessage.success('数据源已删除')
    dataSourceStore.invalidate()
    await loadDataSources()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除数据源失败'))
  }
}

onMounted(async () => {
  await Promise.allSettled([ensureStatusDictLoaded(), loadDataSources()])
})
</script>

<style scoped>
.panel-count {
  margin-left: 8px;
  font-size: 13px;
  font-weight: 400;
  color: var(--text-secondary);
}

.source-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.source-endpoint {
  font-family: var(--el-font-family-mono, monospace);
  font-size: 12px;
  color: #234c77;
  background: rgba(35, 76, 119, 0.08);
  border-radius: 4px;
  padding: 1px 6px;
}

.source-keys {
  font-size: 13px;
  color: var(--text-primary);
}

.guide-section {
  margin-bottom: 18px;
}

.guide-title {
  margin: 0 0 6px;
  font-size: 14px;
  color: #234c77;
}

.guide-text {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-primary);
}

.guide-text code {
  background: rgba(35, 76, 119, 0.08);
  border-radius: 4px;
  padding: 1px 5px;
  font-size: 12px;
}

.guide-list {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--text-primary);
}
</style>
