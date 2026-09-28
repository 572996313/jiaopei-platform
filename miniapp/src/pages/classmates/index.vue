<script setup lang="ts">
import { onLoad, onShow } from '@dcloudio/uni-app'
import { computed, ref, watch } from 'vue'
import { fetchClassmates, type ClassmateGroup } from '@/api/miniapp'
import { useBoundStudent } from '@/composables/use-bound-student'
import StudentSwitcher from '@/components/StudentSwitcher.vue'

const { students, studentId, ready, showSwitcher, init, switchTo } = useBoundStudent()
const groups = ref<ClassmateGroup[]>([])
const loading = ref(false)
const keyword = ref('')

// 名单满 12 人才给搜索框:一个班十几个人扫一眼就看完,提前放个输入框反而像缺内容
const SEARCH_THRESHOLD = 12
const totalClassmates = computed(() =>
  groups.value.reduce((sum, group) => sum + group.classmateNames.length, 0),
)
const showSearch = computed(() => totalClassmates.value > SEARCH_THRESHOLD)

/** 只按姓名过滤,分组结构保留;不匹配就不显示那一组 */
const visibleGroups = computed(() => {
  const text = keyword.value.trim()
  if (!text) {
    return groups.value
  }
  return groups.value
    .map((group) => ({
      ...group,
      classmateNames: group.classmateNames.filter((name) => name.includes(text)),
    }))
    .filter((group) => group.classmateNames.length > 0)
})

const noneMatched = computed(() => keyword.value.trim() !== '' && visibleGroups.value.length === 0)

function loadClassmates() {
  if (!studentId.value) {
    groups.value = []
    return
  }
  loading.value = true
  fetchClassmates(studentId.value)
    .then((data) => {
      groups.value = data
    })
    .catch(() => {
      groups.value = []
    })
    .finally(() => {
      loading.value = false
    })
}

onLoad((options) => {
  init(decodeURIComponent(options?.studentId || ''))
})

// PC 侧入班/换班后要回到这一页才看得到新名单,所以每次显示都重拉
onShow(() => {
  if (ready.value) {
    loadClassmates()
  }
})

watch(studentId, (id) => {
  keyword.value = ''
  if (id) {
    loadClassmates()
  }
})
</script>

<template>
  <view class="classmates-page">
    <StudentSwitcher
      v-if="showSwitcher"
      :students="students"
      :active-id="studentId"
      @select="switchTo"
    />

    <view v-if="showSearch" class="search-box">
      <input
        v-model="keyword"
        class="search-input"
        placeholder="输入姓名首字筛选"
        placeholder-class="search-placeholder"
      />
    </view>

    <view v-if="loading" class="page-tip">加载中…</view>

    <template v-else-if="ready && !studentId">
      <view class="page-tip">
        <text class="tip-main">还没有可查看的学员</text>
        <text class="tip-sub">先在「我的」页绑定学员档案</text>
      </view>
    </template>

    <template v-else-if="groups.length === 0">
      <view class="page-tip">
        <text class="tip-main">还没有分班</text>
        <text class="tip-sub">班级由机构安排,分班后这里才会出现同班同学</text>
      </view>
    </template>

    <template v-else>
      <view v-if="noneMatched" class="page-tip">
        <text class="tip-main">没有匹配的同学</text>
      </view>
      <view v-for="group in visibleGroups" :key="group.classId" class="group-card">
        <view class="group-head">
          <text class="group-title">{{ group.className || '所在班级' }}</text>
          <text class="group-count">共 {{ group.total }} 人</text>
        </view>
        <view v-if="group.classmateNames.length === 0" class="only-one">
          <text>这个班暂时只有你一个人</text>
        </view>
        <view v-else class="classmate-grid">
          <view v-for="name in group.classmateNames" :key="name" class="classmate-item">
            <view class="avatar">{{ name.slice(0, 1) }}</view>
            <text class="name">{{ name }}</text>
          </view>
        </view>
      </view>
      <view class="privacy-note">名单只显示姓名,联系方式不会下发到这里</view>
    </template>
  </view>
</template>

<style scoped>
.classmates-page {
  min-height: 100vh;
  padding: 24rpx;
  box-sizing: border-box;
}

.search-box {
  background: #ffffff;
  border-radius: 999rpx;
  padding: 0 28rpx;
  margin-bottom: 24rpx;
}

.search-input {
  height: 72rpx;
  font-size: 26rpx;
  color: #111827;
}

.search-placeholder {
  color: #c4c9d1;
}

.page-tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 100rpx 40rpx;
}

.tip-main {
  font-size: 28rpx;
  color: #6b7280;
}

.tip-sub {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #c4c9d1;
  text-align: center;
  line-height: 1.6;
}

.group-card {
  background: #ffffff;
  border-radius: 20rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(17, 24, 39, 0.04);
}

.group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 20rpx;
  border-bottom: 1rpx solid #f3f4f6;
}

.group-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #111827;
}

.group-count {
  font-size: 24rpx;
  color: #9ca3af;
}

.only-one {
  padding: 40rpx 0 20rpx;
  text-align: center;
  font-size: 24rpx;
  color: #c4c9d1;
}

.classmate-grid {
  display: flex;
  flex-wrap: wrap;
  padding-top: 24rpx;
}

.classmate-item {
  width: 33.33%;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 28rpx;
}

.avatar {
  width: 88rpx;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 50%;
  text-align: center;
  font-size: 34rpx;
  color: #2563eb;
  background: #eff6ff;
}

.name {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #374151;
  max-width: 180rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.privacy-note {
  padding: 8rpx 20rpx 24rpx;
  text-align: center;
  font-size: 22rpx;
  color: #c4c9d1;
}
</style>
