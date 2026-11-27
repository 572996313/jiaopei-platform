<template>
  <section class="education-page">
    <article class="panel-card education-panel" v-loading="loading">
      <div class="table-toolbar">
        <el-button type="primary" :disabled="!canWrite" :loading="saving" @click="saveRules">
          保存订阅
        </el-button>
        <el-button :icon="Refresh" circle :loading="loading" @click="load" />
        <NoteHint label="订阅口径">
          订阅只从全量事件里<b>取你关心的部分</b>，不是权限。没有任何规则时你会看到全部可见事件；
          漏勾选不会看不到，只是不在「我关心的」视图里。同维度多规则取 <b>OR</b>，跨维度取
          <b>AND</b>；被指派给你的事项始终会出现在「我关心的」里。
        </NoteHint>
      </div>

      <div class="sub-body">
        <section class="sub-section">
          <div class="sub-section-head">
            <h3>我的订阅规则</h3>
            <span class="sub-muted">
              {{
                rules.length === 0 ? '当前未收窄：等同全部可见事件' : `共 ${rules.length} 条规则`
              }}
            </span>
          </div>

          <el-table
            :data="rules"
            border
            empty-text="还没有订阅规则；直接加一条，或从下方推荐里一键订阅"
          >
            <el-table-column label="维度" width="130">
              <template #default="{ row }">{{ ruleTypeLabel(row.ruleType) }}</template>
            </el-table-column>
            <el-table-column label="取值" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ row.label || row.ruleValue }}</template>
            </el-table-column>
            <el-table-column label="启用" width="90" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" :disabled="!canWrite" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" align="center">
              <template #default="{ $index }">
                <el-button link type="danger" :disabled="!canWrite" @click="removeRule($index)">
                  移除
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="sub-add">
            <el-select v-model="newRule.ruleType" style="width: 150px">
              <el-option
                v-for="item in manualRuleTypes"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <el-select
              v-if="newRule.ruleType === 'type_code'"
              v-model="newRule.ruleValue"
              filterable
              placeholder="选择事件类型"
              style="width: 240px"
            >
              <el-option
                v-for="item in typeOptions"
                :key="item.typeCode"
                :label="item.typeName"
                :value="item.typeCode"
              />
            </el-select>
            <DictSelect
              v-else-if="newRule.ruleType === 'category'"
              v-model="newRule.ruleValue"
              type-code="message_category"
              placeholder="选择分类"
              style="width: 200px"
            />
            <el-select
              v-else-if="newRule.ruleType === 'level'"
              v-model="newRule.ruleValue"
              placeholder="选择级别"
              style="width: 160px"
            >
              <el-option
                v-for="item in MESSAGE_LEVEL_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <el-input
              v-else
              v-model="newRule.ruleValue"
              maxlength="128"
              placeholder="标题/正文关键字"
              style="width: 260px"
              @keyup.enter="addRule"
            />
            <el-button :disabled="!canWrite" @click="addRule">添加规则</el-button>
          </div>
        </section>

        <section class="sub-section">
          <div class="sub-section-head">
            <h3>推荐订阅</h3>
            <span class="sub-muted"
              >按你的任课班级、绑定学员与校区数据范围推荐；只推荐，不自动生效</span
            >
          </div>
          <el-table :data="recommendations" border empty-text="暂时没有可推荐的订阅">
            <el-table-column label="维度" width="110">
              <template #default="{ row }">{{ ruleTypeLabel(row.ruleType) }}</template>
            </el-table-column>
            <el-table-column prop="label" label="对象" min-width="200" show-overflow-tooltip />
            <el-table-column prop="reason" label="推荐原因" min-width="180" show-overflow-tooltip />
            <el-table-column label="操作" width="110" align="center">
              <template #default="{ row }">
                <el-button v-if="row.subscribed" link type="success" disabled>已订阅</el-button>
                <el-button v-else link type="primary" :disabled="!canWrite" @click="subscribe(row)">
                  订阅
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </div>
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import NoteHint from '@cornerstone/core/components/NoteHint.vue'
import DictSelect from '@cornerstone/core/components/DictSelect.vue'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { useDictStore } from '@cornerstone/core/stores/dict'
import {
  MANUAL_SUBSCRIPTION_RULE_TYPES,
  MESSAGE_LEVEL_OPTIONS,
  SUBSCRIPTION_RULE_TYPES,
  fetchMessageRecommendations,
  fetchMessageTypeOptions,
  fetchMySubscriptions,
  saveMySubscriptions,
  type MessageRecommendation,
  type MessageSubscriptionRule,
  type MessageSubscriptionRuleInput,
  type MessageTypeOption,
} from '@cornerstone/module-education/api/message'

const auth = useAuthStore()
const dictStore = useDictStore()

const canWrite = computed(() => auth.hasPermission('message:self:write'))
const loading = ref(false)
const saving = ref(false)
const rules = ref<MessageSubscriptionRule[]>([])
const recommendations = ref<MessageRecommendation[]>([])
const typeOptions = ref<MessageTypeOption[]>([])

const manualRuleTypes = SUBSCRIPTION_RULE_TYPES.filter((item) =>
  MANUAL_SUBSCRIPTION_RULE_TYPES.includes(item.value)
)

const newRule = reactive({
  ruleType: 'type_code',
  ruleValue: '',
})

onMounted(async () => {
  void dictStore.ensureLoaded(['message_category'])
  await load()
})

async function load() {
  loading.value = true
  try {
    const [subscription, recommended, types] = await Promise.all([
      fetchMySubscriptions(),
      fetchMessageRecommendations(),
      fetchMessageTypeOptions(),
    ])
    rules.value = subscription.rules.map((rule) => ({ ...rule }))
    recommendations.value = recommended
    typeOptions.value = types
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '订阅加载失败')
  } finally {
    loading.value = false
  }
}

function ruleTypeLabel(ruleType: string) {
  return SUBSCRIPTION_RULE_TYPES.find((item) => item.value === ruleType)?.label || ruleType
}

function ruleLabel(ruleType: string, ruleValue: string) {
  if (ruleType === 'type_code') {
    return typeOptions.value.find((item) => item.typeCode === ruleValue)?.typeName || ruleValue
  }
  if (ruleType === 'category') {
    return dictStore.displayLabel('message_category', ruleValue)
  }
  if (ruleType === 'level') {
    return MESSAGE_LEVEL_OPTIONS.find((item) => item.value === ruleValue)?.label || ruleValue
  }
  return ruleValue
}

function addRule() {
  const value = newRule.ruleValue?.trim()
  if (!value) {
    ElMessage.warning('请选择或填写订阅值')
    return
  }
  if (rules.value.some((rule) => rule.ruleType === newRule.ruleType && rule.ruleValue === value)) {
    ElMessage.warning('这条规则已经在列表里了')
    return
  }
  rules.value.push({
    ruleType: newRule.ruleType,
    ruleValue: value,
    label: ruleLabel(newRule.ruleType, value),
    enabled: true,
  })
  newRule.ruleValue = ''
}

function removeRule(index: number) {
  rules.value.splice(index, 1)
}

async function saveRules() {
  if (!canWrite.value) {
    return
  }
  saving.value = true
  try {
    await saveMySubscriptions(
      rules.value.map<MessageSubscriptionRuleInput>((rule) => ({
        ruleType: rule.ruleType,
        ruleValue: rule.ruleValue,
        enabled: rule.enabled,
      }))
    )
    ElMessage.success('订阅规则已保存')
    await load()
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '订阅保存失败')
  } finally {
    saving.value = false
  }
}

/** 推荐项点「订阅」立即生效，避免用户忘了再点保存 */
async function subscribe(item: MessageRecommendation) {
  if (item.subscribed) {
    return
  }
  const next = [
    ...rules.value.map<MessageSubscriptionRuleInput>((rule) => ({
      ruleType: rule.ruleType,
      ruleValue: rule.ruleValue,
      enabled: rule.enabled,
    })),
    { ruleType: item.ruleType, ruleValue: item.ruleValue, enabled: true },
  ]
  saving.value = true
  try {
    await saveMySubscriptions(next)
    ElMessage.success(`已订阅：${item.label || item.ruleValue}`)
    await load()
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '订阅失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.sub-body {
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding-top: 4px;
}

.sub-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sub-section-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.sub-section-head h3 {
  margin: 0;
  font-size: 14px;
  color: var(--text-primary);
}

.sub-muted {
  font-size: 12px;
  color: var(--text-muted);
}

.sub-add {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding-top: 2px;
}
</style>
