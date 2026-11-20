<template>
  <section class="campus-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <!-- 本页只留机构级两张档案：账号默认校区在「校区默认账号」，教室在「教室管理」，节次在「时段模板」 -->
    <div class="table-toolbar">
      <el-button :icon="Refresh" circle :loading="lookupLoading" @click="refreshAll" />
      <el-button @click="goCampusAccounts">校区默认账号</el-button>
      <span class="education-context-text">
        校区决定教室与时间段的归属；学期是排课的周期维度，班级可以不挂学期。
      </span>
    </div>

    <div class="campus-grid">
      <article class="panel-card resource-card campus-card">
        <div class="education-table-head">
          <div class="panel-title">校区列表</div>
        </div>
        <div class="table-toolbar">
          <el-button
            v-if="!campusRestricted"
            v-permission="'organization:write'"
            type="primary"
            @click="openCampusDialog('create')"
            >新增校区</el-button
          >
        </div>
        <TableFillBox v-slot="{ height }">
          <el-table :data="campuses" border row-key="id" highlight-current-row :height="height">
            <el-table-column prop="campusCode" label="校区编码" width="150" fixed="left" sortable />
            <el-table-column
              prop="campusName"
              label="校区名称"
              min-width="180"
              show-overflow-tooltip
              sortable
            />
            <el-table-column prop="campusType" label="校区类型" width="120" sortable>
              <template #default="{ row }">{{ campusTypeLabel(row.campusType) }}</template>
            </el-table-column>
            <el-table-column prop="principalName" label="负责人" width="120" sortable />
            <el-table-column prop="phone" label="联系电话" width="150" sortable />
            <el-table-column
              prop="address"
              label="地址"
              min-width="220"
              show-overflow-tooltip
              sortable
            />
            <el-table-column prop="status" label="状态" width="120" sortable>
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)">{{
                  campusStatusLabel(row.status)
                }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-permission="'organization:write'"
                  link
                  type="primary"
                  @click="openCampusDialog('edit', row)"
                  >编辑</el-button
                >
                <el-button
                  v-permission="'organization:write'"
                  link
                  type="danger"
                  @click="confirmDeleteCampus(row)"
                  >删除</el-button
                >
              </template>
            </el-table-column>
          </el-table>
        </TableFillBox>
      </article>

      <article class="panel-card resource-card campus-card">
        <div class="education-table-head">
          <div class="panel-title">学期列表</div>
        </div>
        <div class="table-toolbar">
          <el-button
            v-permission="'organization:write'"
            type="primary"
            @click="openTermDialog('create')"
            >新增学期</el-button
          >
        </div>
        <TableFillBox v-slot="{ height }">
          <el-table :data="terms" border row-key="id" highlight-current-row :height="height">
            <el-table-column prop="termCode" label="学期编码" width="150" fixed="left" sortable />
            <el-table-column
              prop="termName"
              label="学期名称"
              min-width="180"
              show-overflow-tooltip
              sortable
            />
            <el-table-column prop="academicYear" label="学年" width="120" sortable />
            <el-table-column label="学季" width="120" prop="season" sortable>
              <template #default="{ row }">{{ seasonLabel(row.season) }}</template>
            </el-table-column>
            <el-table-column label="起止日期" min-width="220" prop="startDate" sortable>
              <template #default="{ row }"
                >{{ row.startDate || '-' }} 至 {{ row.endDate || '-' }}</template
              >
            </el-table-column>
            <el-table-column prop="status" label="状态" width="120" sortable>
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)">{{ termStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-permission="'organization:write'"
                  link
                  type="primary"
                  @click="openTermDialog('edit', row)"
                  >编辑</el-button
                >
                <el-button
                  v-permission="'organization:write'"
                  link
                  type="danger"
                  @click="confirmDeleteTerm(row)"
                  >删除</el-button
                >
              </template>
            </el-table-column>
          </el-table>
        </TableFillBox>
      </article>
    </div>

    <el-dialog
      v-model="campusDialogVisible"
      :title="campusDialogMode === 'create' ? '新增校区' : '编辑校区'"
      width="560px"
    >
      <el-alert
        v-if="campusFormError"
        :title="campusFormError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />
      <el-form label-position="top" class="education-form">
        <el-form-item label="校区编码">
          <CodeInput v-model="campusForm.campusCode" resource="campus" :mode="campusDialogMode" />
        </el-form-item>
        <el-form-item label="校区名称"><el-input v-model="campusForm.campusName" /></el-form-item>
        <el-form-item label="校区类型">
          <DictSelect
            v-model="campusForm.campusType"
            type-code="campus_type"
            placeholder="选择类型"
          />
        </el-form-item>
        <el-form-item label="负责人"><el-input v-model="campusForm.principalName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="campusForm.phone" /></el-form-item>
        <el-form-item label="地址"
          ><el-input v-model="campusForm.address" type="textarea" :rows="2"
        /></el-form-item>
        <el-form-item label="容量上限"
          ><el-input-number
            v-model="campusForm.capacityLimit"
            :min="0"
            :step="10"
            style="width: 100%"
        /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="campusForm.status">
            <el-option label="规划中" value="planning" />
            <el-option label="启用中" value="active" />
            <el-option label="暂停中" value="suspended" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="campusDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="campusSubmitting" @click="submitCampus">{{
          campusDialogMode === 'create' ? '提交校区' : '保存修改'
        }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="termDialogVisible"
      :title="termDialogMode === 'create' ? '新增学期' : '编辑学期'"
      width="560px"
    >
      <el-alert
        v-if="termFormError"
        :title="termFormError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />
      <el-form label-position="top" class="education-form">
        <el-form-item label="学期编码">
          <CodeInput v-model="termForm.termCode" resource="term" :mode="termDialogMode" />
        </el-form-item>
        <el-form-item label="学期名称"><el-input v-model="termForm.termName" /></el-form-item>
        <el-form-item label="学年"
          ><el-input v-model="termForm.academicYear" placeholder="例如 2026-2027"
        /></el-form-item>
        <el-form-item label="学季"
          ><DictSelect v-model="termForm.season" type-code="term_season"
        /></el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker
            v-model="termForm.startDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择开始日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker
            v-model="termForm.endDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择结束日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="termForm.status">
            <el-option label="草稿" value="draft" />
            <el-option label="启用" value="active" />
            <el-option label="归档" value="archived" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="termDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="termSubmitting" @click="submitTerm">{{
          termDialogMode === 'create' ? '提交学期' : '保存修改'
        }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import {
  createCampus,
  createTerm,
  deleteCampus,
  deleteTerm,
  fetchCampuses,
  fetchTerms,
  updateCampus,
  updateTerm,
  type CampusSummary,
  type TermSummary,
} from '@cornerstone/module-organization/api/organization'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import { useDictStore } from '@cornerstone/core/stores/dict'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'
import DictSelect from '@cornerstone/core/components/DictSelect.vue'
import CodeInput from '@cornerstone/core/components/CodeInput.vue'

const router = useRouter()
const institutionStore = useInstitutionStore()
const dictStore = useDictStore()
const scopeStore = useEducationScopeStore()
const institutionId = computed(() => institutionStore.currentInstitutionId)
// 受限角色只能维护自己名下的校区，新建校区会改变机构结构，后端也会拒
const campusRestricted = computed(() => scopeStore.campusRestricted)
const campuses = ref<CampusSummary[]>([])
const terms = ref<TermSummary[]>([])

const pageError = ref('')
const lookupLoading = ref(false)

const campusDialogVisible = ref(false)
const termDialogVisible = ref(false)

const campusDialogMode = ref<'create' | 'edit'>('create')
const termDialogMode = ref<'create' | 'edit'>('create')

const editingCampusId = ref<string | null>(null)
const editingTermId = ref<string | null>(null)

const campusSubmitting = ref(false)
const termSubmitting = ref(false)

const campusFormError = ref('')
const termFormError = ref('')

const campusForm = reactive({
  campusCode: '',
  campusName: '',
  campusType: '',
  principalName: '',
  phone: '',
  address: '',
  capacityLimit: 100,
  status: 'active',
})

const termForm = reactive({
  termCode: '',
  termName: '',
  academicYear: '2026-2027',
  season: '',
  startDate: '',
  endDate: '',
  status: 'draft',
})

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function trimToUndefined(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

function campusTypeLabel(value?: string | null) {
  return dictStore.displayLabel('campus_type', value)
}

function campusStatusLabel(value?: string | null) {
  switch (value) {
    case 'active':
      return '启用中'
    case 'suspended':
      return '暂停中'
    case 'planning':
    default:
      return '规划中'
  }
}

function termStatusLabel(value?: string | null) {
  switch (value) {
    case 'active':
      return '启用'
    case 'archived':
      return '归档'
    case 'draft':
    default:
      return '草稿'
  }
}

function statusTagType(value?: string | null) {
  switch (value) {
    case 'active':
      return 'success'
    case 'suspended':
      return 'warning'
    default:
      // 规划中/草稿/归档这类常态用中性色,不占警示位
      return 'info'
  }
}

function seasonLabel(value?: string | null) {
  return dictStore.displayLabel('term_season', value)
}

function resetCampusForm() {
  campusForm.campusCode = ''
  campusForm.campusName = ''
  campusForm.campusType = ''
  campusForm.principalName = ''
  campusForm.phone = ''
  campusForm.address = ''
  campusForm.capacityLimit = 100
  campusForm.status = 'active'
  campusFormError.value = ''
}

function resetTermForm() {
  termForm.termCode = ''
  termForm.termName = ''
  termForm.academicYear = '2026-2027'
  termForm.season = ''
  termForm.startDate = ''
  termForm.endDate = ''
  termForm.status = 'draft'
  termFormError.value = ''
}

async function loadResources() {
  if (!institutionId.value) {
    campuses.value = []
    terms.value = []
    return
  }

  lookupLoading.value = true
  pageError.value = ''
  try {
    const [campusList, termList] = await Promise.all([
      fetchCampuses(institutionId.value),
      fetchTerms(institutionId.value),
    ])

    campuses.value = campusList
    terms.value = termList
    // 本页是校区的维护入口：新增/改名/停用后把全站那一份校区清单一起刷掉，
    // 横幅那颗范围芯片与其余页面的名称回显才不会停在旧列表
    await scopeStore.loadCampuses(institutionId.value, true)
    // 删掉的正好是当前范围：收在「全部校区」，不然各页都在筛一个已经不存在的校区
    if (scopeStore.campusId && !campusList.some((item) => item.id === scopeStore.campusId)) {
      scopeStore.setCampus(null)
    }
  } catch (error) {
    pageError.value = toMessage(error, '组织资源加载失败')
  } finally {
    lookupLoading.value = false
  }
}

function openCampusDialog(mode: 'create' | 'edit', row?: CampusSummary) {
  campusDialogMode.value = mode
  if (mode === 'edit' && row) {
    editingCampusId.value = row.id
    campusForm.campusCode = row.campusCode ?? ''
    campusForm.campusName = row.campusName ?? ''
    campusForm.campusType = row.campusType ?? ''
    campusForm.principalName = row.principalName ?? ''
    campusForm.phone = row.phone ?? ''
    campusForm.address = row.address ?? ''
    campusForm.capacityLimit = row.capacityLimit ?? 100
    campusForm.status = row.status ?? 'active'
  } else {
    editingCampusId.value = null
    resetCampusForm()
  }
  campusFormError.value = ''
  campusDialogVisible.value = true
}

function openTermDialog(mode: 'create' | 'edit', row?: TermSummary) {
  termDialogMode.value = mode
  if (mode === 'edit' && row) {
    editingTermId.value = row.id
    termForm.termCode = row.termCode ?? ''
    termForm.termName = row.termName ?? ''
    termForm.academicYear = row.academicYear ?? ''
    termForm.season = row.season ?? ''
    termForm.startDate = row.startDate ?? ''
    termForm.endDate = row.endDate ?? ''
    termForm.status = row.status ?? 'draft'
  } else {
    editingTermId.value = null
    resetTermForm()
  }
  termFormError.value = ''
  termDialogVisible.value = true
}

async function submitCampus() {
  if (!institutionId.value) {
    campusFormError.value = '请先选择机构范围'
    return
  }
  if (
    !trimToUndefined(campusForm.campusCode) ||
    !trimToUndefined(campusForm.campusName) ||
    !trimToUndefined(campusForm.address)
  ) {
    campusFormError.value = '请完整填写校区编码、名称和地址'
    return
  }

  campusSubmitting.value = true
  campusFormError.value = ''
  try {
    if (campusDialogMode.value === 'create') {
      await createCampus({
        institutionId: institutionId.value,
        campusCode: campusForm.campusCode.trim(),
        campusName: campusForm.campusName.trim(),
        campusType: campusForm.campusType,
        principalName: trimToUndefined(campusForm.principalName),
        phone: trimToUndefined(campusForm.phone),
        address: campusForm.address.trim(),
        capacityLimit: campusForm.capacityLimit,
        status: campusForm.status,
      })
      ElMessage.success('校区已新增')
    } else if (editingCampusId.value) {
      await updateCampus(editingCampusId.value, {
        campusCode: campusForm.campusCode.trim(),
        campusName: campusForm.campusName.trim(),
        campusType: campusForm.campusType,
        principalName: trimToUndefined(campusForm.principalName),
        phone: trimToUndefined(campusForm.phone),
        address: campusForm.address.trim(),
        capacityLimit: campusForm.capacityLimit,
        status: campusForm.status,
      })
      ElMessage.success('校区已更新')
    }
    campusDialogVisible.value = false
    await loadResources()
  } catch (error) {
    campusFormError.value = toMessage(
      error,
      campusDialogMode.value === 'create' ? '新增校区失败' : '更新校区失败'
    )
  } finally {
    campusSubmitting.value = false
  }
}

async function confirmDeleteCampus(row: CampusSummary) {
  try {
    await ElMessageBox.confirm(
      `确定删除校区「${row.campusName || row.campusCode || row.id}」吗？`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      }
    )
  } catch {
    return
  }
  try {
    await deleteCampus(row.id)
    ElMessage.success('校区已删除')
    await loadResources()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除校区失败'))
  }
}

async function submitTerm() {
  if (!institutionId.value) {
    termFormError.value = '请先选择机构范围'
    return
  }
  if (
    !trimToUndefined(termForm.termCode) ||
    !trimToUndefined(termForm.termName) ||
    !trimToUndefined(termForm.academicYear)
  ) {
    termFormError.value = '请完整填写学期编码、名称和学年'
    return
  }
  if (!termForm.startDate || !termForm.endDate) {
    termFormError.value = '请选择学期开始和结束日期'
    return
  }
  if (termForm.startDate > termForm.endDate) {
    termFormError.value = '开始日期不能晚于结束日期'
    return
  }

  termSubmitting.value = true
  termFormError.value = ''
  try {
    if (termDialogMode.value === 'create') {
      await createTerm({
        institutionId: institutionId.value,
        termCode: termForm.termCode.trim(),
        termName: termForm.termName.trim(),
        academicYear: termForm.academicYear.trim(),
        season: trimToUndefined(termForm.season),
        startDate: termForm.startDate,
        endDate: termForm.endDate,
        status: termForm.status,
      })
      ElMessage.success('学期已新增')
    } else if (editingTermId.value) {
      await updateTerm(editingTermId.value, {
        termCode: termForm.termCode.trim(),
        termName: termForm.termName.trim(),
        academicYear: termForm.academicYear.trim(),
        season: trimToUndefined(termForm.season),
        startDate: termForm.startDate,
        endDate: termForm.endDate,
        status: termForm.status,
      })
      ElMessage.success('学期已更新')
    }
    termDialogVisible.value = false
    terms.value = await fetchTerms(institutionId.value)
  } catch (error) {
    termFormError.value = toMessage(
      error,
      termDialogMode.value === 'create' ? '新增学期失败' : '更新学期失败'
    )
  } finally {
    termSubmitting.value = false
  }
}

async function confirmDeleteTerm(row: TermSummary) {
  try {
    await ElMessageBox.confirm(
      `确定删除学期「${row.termName || row.termCode || row.id}」吗？`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      }
    )
  } catch {
    return
  }
  try {
    await deleteTerm(row.id)
    ElMessage.success('学期已删除')
    terms.value = await fetchTerms(institutionId.value)
  } catch (error) {
    ElMessage.error(toMessage(error, '删除学期失败'))
  }
}

function goCampusAccounts() {
  // 两张页共用同一个校区范围 store，跳过去落地就在同一个校区，不用带参数
  router.push('/campus-accounts')
}

async function refreshAll() {
  await loadResources()
}

async function initializePage() {
  lookupLoading.value = true
  pageError.value = ''
  try {
    await institutionStore.loadInstitutions()
  } catch (error) {
    pageError.value = toMessage(error, '页面初始化失败')
  } finally {
    lookupLoading.value = false
  }
  await refreshAll()
}

watch(
  () => institutionStore.currentInstitutionId,
  async () => {
    await loadResources()
  }
)

onMounted(() => {
  // 列表回显要用的字典在进页面时就拉取（表单里的 DictSelect 会自己拉，表格列不会）
  void dictStore.ensureLoaded(['campus_type', 'term_season']).catch(() => undefined)
  initializePage()
})
</script>

<style scoped>
/* 两张同类卡并排、等高吃满页签行以下那一段。
   不走 core 的 .resource-grid：那条规则把末张卡整行铺开（grid-column:1/-1）、
   又给表体钉了 398px 的十行保底，两张卡照它排反而装不进 729。
   grid-template-rows 必须是 minmax(0,1fr)：默认行高由内容撑开，TableFillBox 就量不到剩余空间。 */
.campus-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: minmax(0, 1fr);
  gap: 18px;
  align-items: stretch;
  flex: 1 1 0;
  min-height: 0;
}

.campus-card {
  min-height: 0;
  overflow: hidden;
}

@media (max-width: 1280px) {
  .campus-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
