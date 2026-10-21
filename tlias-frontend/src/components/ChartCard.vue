<template>
  <div v-loading="loading" class="chart-card">
    <div class="chart-head">
      <div>
        <h3>{{ title }}</h3>
        <p v-if="subtitle">{{ subtitle }}</p>
      </div>
      <slot name="extra" />
    </div>
    <div class="chart-body" :style="{ height }">
      <div ref="chartRef" class="chart-canvas"></div>
      <div v-if="empty" class="chart-empty">暂无数据</div>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  option: { type: Object, default: null },
  height: { type: String, default: '300px' },
  loading: { type: Boolean, default: false },
  empty: { type: Boolean, default: false }
})

const chartRef = ref()
let chart = null
let observer = null

function render() {
  if (!chart || !props.option) return
  chart.setOption(props.option, true)
}

onMounted(() => {
  chart = echarts.init(chartRef.value)
  render()
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(chartRef.value)
})

watch(() => props.option, render, { deep: true })

onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.chart-card {
  padding: 18px 20px 8px;
  border: 1px solid var(--tl-border);
  border-radius: var(--tl-radius);
  background: var(--tl-card);
  box-shadow: var(--tl-shadow);
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.chart-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--tl-shadow-hover);
}

.chart-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}

.chart-head h3 {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -0.2px;
  color: var(--tl-ink);
}

.chart-head p {
  margin-top: 4px;
  font-size: 12px;
  color: var(--tl-muted);
}

.chart-body {
  position: relative;
}

.chart-canvas {
  width: 100%;
  height: 100%;
}

.chart-empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.72);
  font-size: 13px;
  color: #94a3b8;
}
</style>
