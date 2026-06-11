<template>
  <section class="dashboard-grid">
    <article
      v-for="card in cards"
      :key="card.label"
      class="stat-card"
    >
      <div class="stat-label">{{ card.label }}</div>
      <div class="stat-value">{{ card.value }}</div>
      <div class="stat-hint">{{ card.hint }}</div>
    </article>

    <article class="panel-card panel-card-wide">
      <div class="panel-title">今日教务重点</div>
      <ul class="panel-list">
        <li>08:30 数学提高班，3号教室，任课教师张老师</li>
        <li>10:00 英语培优班，1号教室，待确认1名请假学员</li>
        <li>14:00 作文班，需处理调课申请 2 条</li>
      </ul>
    </article>

    <article class="panel-card">
      <div class="panel-title">首期模块</div>
      <ul class="panel-list">
        <li>校区与教室</li>
        <li>教师与学员</li>
        <li>课程与班级</li>
        <li>排课与考勤</li>
      </ul>
    </article>

    <article class="panel-card">
      <div class="panel-title">当前状态</div>
      <ul class="panel-list">
        <li>前端骨架已完成</li>
        <li>后端骨架已完成</li>
        <li>等待下一步建模</li>
      </ul>
    </article>
  </section>
</template>

<script setup lang="ts">
const cards = [
  { label: '今日课次', value: '18', hint: '覆盖 4 个校区' },
  { label: '到课学员', value: '236', hint: '实时签到统计' },
  { label: '待处理请假', value: '7', hint: '需教务确认' },
  { label: '教师排课冲突', value: '0', hint: '当前无冲突' },
]
</script>
