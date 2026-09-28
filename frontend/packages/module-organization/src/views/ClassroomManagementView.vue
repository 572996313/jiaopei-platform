<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <!-- 整页级开关:本页只有一张主表,没有统计条,这行留刷新 -->
    <div class="table-toolbar">
      <el-button :icon="Refresh" circle :loading="lookupLoading" @click="refreshAll" />
    </div>

    <!-- 统一筛选栏:换校区不再自动触发,点「查询」才按新校区拉教室 -->
    <FilterBar :loading="tableLoading" @query="loadClassrooms" @reset="resetToDefaultCampus">
      <!-- 教室必须挂在某个校区下,没有"全部校区"这一档 -->
      <el-select v-model="campusId" placeholder="选择校区" filterable>
        <el-option
          v-for="item in campuses"
          :key="item.id"
          :label="item.campusName || item.campusCode || `校区 #${item.id}`"
          :value="item.id"
        />
      </el-select>
    </FilterBar>

    <article class="panel-card education-panel">
      <div class="table-toolbar">
        <el-button type="primary" @click="openClassroomDialog('create')">新增教室</el-button>
        <div class="education-context-text">
          当前机构：{{ formatInstitution(institutionId) }}，当前校区：{{
            formatCampus(campusId)
          }}；教室编码与名称在同一校区内唯一
        </div>
      </div>

      <TableFillBox v-slot="{ height }">
        <el-table
          :data="classrooms"
          border
          row-key="id"
          highlight-current-row
          :height="height"
          v-loading="tableLoading"
        >
          <el-table-column
            prop="classroomCode"
            label="教室编码"
            width="150"
            fixed="left"
            sortable
          />
          <el-table-column
            prop="classroomName"
            label="教室名称"
            min-width="180"
            show-overflow-tooltip
            sortable
          />
          <el-table-column label="类型" width="120" prop="roomType" sortable>
            <template #default="{ row }">{{ roomTypeLabel(row.roomType) }}</template>
          </el-table-column>
          <el-table-column prop="floorNo" label="楼层" width="100" align="right" sortable />
          <el-table-column prop="seatCapacity" label="容量" width="100" align="right" sortable />
          <el-table-column prop="status" label="状态" width="120" sortable>
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{
                classroomStatusLabel(row.status)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="可排课" width="100" prop="allowScheduling" sortable>
            <template #default="{ row }">{{ row.allowScheduling ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column
            prop="remark"
            label="备注"
            min-width="180"
            show-overflow-tooltip
            sortable
          />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openClassroomDialog('edit', row)"
                >编辑</el-button
              >
              <el-button link type="danger" @click="confirmDeleteClassroom(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </TableFillBox>

      <div v-if="!tableLoading && classrooms.length === 0" class="education-empty">
        当前校区下暂无教室数据。
      </div>
    </article>

    <el-dialog
      v-model="classroomDialogVisible"
      :title="classroomDialogMode === 'create' ? '新增教室' : '编辑教室'"
      width="560px"
    >
      <el-alert
        v-if="classroomFormError"
        :title="classroomFormError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />
      <el-form label-position="top" class="education-form">
        <el-form-item label="归属校区">
          <el-select v-model="classroomForm.campusId" placeholder="选择校区" filterable>
            <el-option
              v-for="item in campuses"
              :key="item.id"
              :label="item.campusName || item.campusCode || `校区 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="教室编码"
          ><el-input v-model="classroomForm.classroomCode"
        /></el-form-item>
        <el-form-item label="教室名称"
          ><el-input v-model="classroomForm.classroomName"
        /></el-form-item>
        <el-form-item label="教室类型"
          ><DictSelect v-model="classroomForm.roomType" type-code="room_type"
        /></el-form-item>
        <el-form-item label="楼层"><el-input v-model="classroomForm.floorNo" /></el-form-item>
        <el-form-item label="座位数"
          ><el-input-number v-model="classroomForm.seatCapacity" :min="0" style="width: 100%"
        /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="classroomForm.status">
            <el-option label="空闲" value="idle" />
            <el-option label="占用" value="occupied" />
            <el-option label="维护" value="maintenance" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="classroomDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="classroomSubmitting" @click="submitClassroom">{{
          classroomDialogMode === 'create' ? '提交教室' : '保存修改'
        }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import FilterBar from '@cornerstone/core/components/FilterBar.vue'
import DictSelect from '@cornerstone/core/components/DictSelect.vue'
import { useDictStore } from '@cornerstone/core/stores/dict'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import {
  createClassroom,
  deleteClassroom,
  fetchCampuses,
  fetchClassrooms,
  updateClassroom,
  type CampusSummary,
  type ClassroomSummary,
} from '@cornerstone/module-organization/api/organization'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'

const institutionStore = useInstitutionStore()
const scopeStore = useEducationScopeStore()
const dictStore = useDictStore()

const institutionId = computed(() => institutionStore.currentInstitutionId)
/** 校区是本页唯一的范围条件,与别的页共用同一个 store,切页时保持在同一校区 */
const { campusId } = storeToRefs(scopeStore)
const campuses = ref<CampusSummary[]>([])
const classrooms = ref<ClassroomSummary[]>([])

const pageError = ref('')
const lookupLoading = ref(false)
const tableLoading = ref(false)

const campusMap = computed(() => {
  return new Map(
    campuses.value.map((item) => [
      item.id,
      item.campusName || item.campusCode || `校区 #${item.id}`,
    ])
  )
})

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function trimToUndefined(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

function formatInstitution(id?: string | null) {
  return institutionStore.currentInstitutionName || (id ? `机构 #${id}` : '未选择')
}

function formatCampus(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return campusMap.value.get(id) || `校区 #${id}`
}

function roomTypeLabel(value?: string | null) {
  return dictStore.displayLabel('room_type', value)
}

function classroomStatusLabel(value?: string | null) {
  switch (value) {
    case 'occupied':
      return '占用'
    case 'maintenance':
      return '维护'
    case 'idle':
    default:
      return '空闲'
  }
}

function statusTagType(value?: string | null) {
  switch (value) {
    case 'active':
      return 'success'
    case 'suspended':
    case 'maintenance':
      return 'warning'
    default:
      // 空闲/占用这类常态用中性色,不占警示位
      return 'info'
  }
}

// ---- 弹窗 ----
const classroomDialogVisible = ref(false)
const classroomDialogMode = ref<'create' | 'edit'>('create')
const classroomSubmitting = ref(false)
const classroomFormError = ref('')
const editingClassroomId = ref<string | null>(null)

const classroomForm = reactive({
  campusId: '' as string | null,
  classroomCode: '',
  classroomName: '',
  roomType: '',
  floorNo: '',
  seatCapacity: 20,
  status: 'idle',
})

function resetClassroomForm() {
  classroomForm.campusId = campusId.value
  classroomForm.classroomCode = ''
  classroomForm.classroomName = ''
  classroomForm.roomType = ''
  classroomForm.floorNo = ''
  classroomForm.seatCapacity = 20
  classroomForm.status = 'idle'
  classroomFormError.value = ''
}

function openClassroomDialog(mode: 'create' | 'edit', row?: ClassroomSummary) {
  classroomDialogMode.value = mode
  if (mode === 'edit' && row) {
    editingClassroomId.value = row.id
    classroomForm.campusId = row.campusId ?? campusId.value
    classroomForm.classroomCode = row.classroomCode ?? ''
    classroomForm.classroomName = row.classroomName ?? ''
    classroomForm.roomType = row.roomType ?? ''
    classroomForm.floorNo = row.floorNo ?? ''
    classroomForm.seatCapacity = row.seatCapacity ?? 20
    classroomForm.status = row.status ?? 'idle'
  } else {
    editingClassroomId.value = null
    resetClassroomForm()
  }
  classroomFormError.value = ''
  classroomDialogVisible.value = true
}

async function loadClassrooms() {
  if (!campusId.value) {
    classrooms.value = []
    return
  }
  tableLoading.value = true
  try {
    classrooms.value = await fetchClassrooms(campusId.value)
  } catch (error) {
    classrooms.value = []
    pageError.value = toMessage(error, '教室列表加载失败')
  } finally {
    tableLoading.value = false
  }
}

async function submitClassroom() {
  if (!classroomForm.campusId) {
    classroomFormError.value = '请选择归属校区'
    return
  }
  if (
    !trimToUndefined(classroomForm.classroomCode) ||
    !trimToUndefined(classroomForm.classroomName)
  ) {
    classroomFormError.value = '请完整填写教室编码和名称'
    return
  }

  classroomSubmitting.value = true
  classroomFormError.value = ''
  try {
    if (classroomDialogMode.value === 'create') {
      await createClassroom({
        campusId: classroomForm.campusId,
        classroomCode: classroomForm.classroomCode.trim(),
        classroomName: classroomForm.classroomName.trim(),
        roomType: trimToUndefined(classroomForm.roomType),
        floorNo: trimToUndefined(classroomForm.floorNo),
        seatCapacity: classroomForm.seatCapacity,
        status: classroomForm.status,
      })
      ElMessage.success('教室已新增')
    } else if (editingClassroomId.value) {
      await updateClassroom(editingClassroomId.value, {
        campusId: classroomForm.campusId,
        classroomCode: classroomForm.classroomCode.trim(),
        classroomName: classroomForm.classroomName.trim(),
        roomType: trimToUndefined(classroomForm.roomType),
        floorNo: trimToUndefined(classroomForm.floorNo),
        seatCapacity: classroomForm.seatCapacity,
        status: classroomForm.status,
      })
      ElMessage.success('教室已更新')
    }
    classroomDialogVisible.value = false
    // 存到别的校区时跟着切过去,否则刚保存的那条会被当前校区筛选挡掉
    if (campusId.value !== classroomForm.campusId) {
      campusId.value = classroomForm.campusId
    }
    await loadClassrooms()
  } catch (error) {
    classroomFormError.value = toMessage(
      error,
      classroomDialogMode.value === 'create' ? '新增教室失败' : '更新教室失败'
    )
  } finally {
    classroomSubmitting.value = false
  }
}

async function confirmDeleteClassroom(row: ClassroomSummary) {
  try {
    await ElMessageBox.confirm(
      `确定删除教室「${row.classroomName || row.classroomCode || row.id}」吗？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteClassroom(row.id)
    ElMessage.success('教室已删除')
    await loadClassrooms()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除教室失败'))
  }
}

async function loadCampuses() {
  if (!institutionId.value) {
    campuses.value = []
    return
  }
  lookupLoading.value = true
  pageError.value = ''
  try {
    campuses.value = await fetchCampuses(institutionId.value)
    if (!campusId.value || !campuses.value.some((item) => item.id === campusId.value)) {
      campusId.value = campuses.value[0]?.id || null
    }
  } catch (error) {
    pageError.value = toMessage(error, '校区列表加载失败')
  } finally {
    lookupLoading.value = false
  }
  await loadClassrooms()
}

async function refreshAll() {
  await loadCampuses()
}

/** 教室没有"全部校区"档,重置 = 回到第一个校区并重查 */
function resetToDefaultCampus() {
  campusId.value = campuses.value[0]?.id || null
  void loadClassrooms()
}

async function initializePage() {
  try {
    await institutionStore.loadInstitutions()
  } catch (error) {
    pageError.value = toMessage(error, '页面初始化失败')
  }
  await refreshAll()
}

watch(institutionId, async () => {
  campusId.value = null
  await loadCampuses()
})

onMounted(() => {
  // 列表回显要用的字典在进页面时就拉取（表单里的 DictSelect 会自己拉，表格列不会）
  void dictStore.ensureLoaded(['room_type']).catch(() => undefined)
  initializePage()
})
</script>
