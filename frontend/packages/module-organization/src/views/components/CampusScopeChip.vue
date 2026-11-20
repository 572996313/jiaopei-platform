<template>
  <!-- 全站唯一的校区范围入口：落在机构芯片右侧，选完全站页面都跟着收窄 -->
  <div v-if="campuses.length" class="banner-chip campus-chip">
    <el-icon class="campus-chip-icon"><Location /></el-icon>
    <el-select
      :model-value="campusId || (showAllOption ? ALL_CAMPUS : '')"
      :placeholder="allCampusesLabel"
      class="campus-chip-select"
      @change="choose"
    >
      <el-option v-if="showAllOption" :label="allCampusesLabel" :value="ALL_CAMPUS" />
      <el-option
        v-for="item in campuses"
        :key="item.id"
        :label="campusLabel(item)"
        :value="item.id"
      >
        <span class="campus-option-name">{{ campusLabel(item) }}</span>
        <span v-if="item.id === preferredCampusId" class="campus-option-flag">默认</span>
      </el-option>
    </el-select>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { Location } from '@element-plus/icons-vue'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import type { CampusSummary } from '@cornerstone/module-organization/api/organization'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'

/** el-select 的 v-model 不接 null，用哨兵值承载「全部校区」这一档 */
const ALL_CAMPUS = '__all__'

const scopeStore = useEducationScopeStore()
const institutionStore = useInstitutionStore()
const { campusId, campuses, preferredCampusId, campusRestricted, allCampusesLabel } =
  storeToRefs(scopeStore)
/** 受限且只有一个可访问校区时，「全部」与那个校区是同一份数据，不再给无意义的下拉档 */
const showAllOption = computed(() => !campusRestricted.value || campuses.value.length > 1)

function campusLabel(item: CampusSummary) {
  return item.campusName || item.campusCode || `校区 #${item.id}`
}

function choose(value: string) {
  // setCampus 里判过重，并且会把全部页签重挂一遍（范围是跨页的，失活页签不会补跑）
  scopeStore.setCampus(value === ALL_CAMPUS ? null : value)
}

onMounted(() => {
  void scopeStore.initScope(institutionStore.currentInstitutionId)
})

watch(
  () => institutionStore.currentInstitutionId,
  (id) => {
    void scopeStore.initScope(id)
  }
)
</script>

<style scoped>
.campus-chip {
  gap: 6px;
  padding-left: 10px;
}

.campus-chip-icon {
  flex: none;
  font-size: 15px;
  color: var(--chip-ink-dim);
}

/* 机构芯片那一段用的是通用 .banner-chip .el-select{width:186px}，校区名普遍更短 */
.campus-chip :deep(.campus-chip-select),
.campus-chip :deep(.campus-chip-select .el-select__wrapper) {
  width: 132px;
}

.campus-option-name {
  float: left;
}

.campus-option-flag {
  float: right;
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
