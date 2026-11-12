<template>
  <div class="stats">
    <div v-for="stat in stats" :key="stat.label" class="sticker stats__item">
      <div class="stats__value">{{ report[stat.key] ?? '—' }}</div>
      <div class="stats__label">{{ $t(stat.label) }}</div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {getReport} from "@/scripts/dashboard";

const report = ref<Record<string, number | null>>({
  usersCount: null,
  recipesCount: null,
  likesCount: null,
  cookbooksCount: null,
  followsCount: null,
})

const stats = [
  {key: 'usersCount', label: 'users'},
  {key: 'recipesCount', label: 'recipes'},
  {key: 'likesCount', label: 'likes'},
  {key: 'cookbooksCount', label: 'cookbooks'},
  {key: 'followsCount', label: 'follows'},
]

getReport().then(response => {
  report.value = response.data
  console.log(report.value)
})
</script>


<style scoped>
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 16px;
  padding: 0 var(--lift) var(--lift) 0;
}

.stats__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 16px 12px;
  text-align: center;
}

.stats__value {
  font-size: 30px;
  font-weight: 700;
  line-height: 1.1;
}

.stats__label {
  font-size: 13px;
  font-weight: 700;
}
</style>
