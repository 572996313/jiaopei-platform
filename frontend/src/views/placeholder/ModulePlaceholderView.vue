<template>
  <section class="placeholder-card">
    <div class="page-kicker">模块占位</div>
    <h2 class="placeholder-title">{{ title }}</h2>
    <p class="placeholder-description">{{ description }}</p>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  description: string
}>()
</script>
