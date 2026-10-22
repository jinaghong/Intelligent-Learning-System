<template>
  <div class="dashboard">
    <div class="page-head">
      <div>
        <p class="eyebrow">OVERVIEW</p>
        <h2>数据总览</h2>
        <p class="page-sub">{{ greeting }}，{{ auth.displayName }} · {{ today }}</p>
      </div>
      <div class="head-actions">
        <span class="sync-hint">数据来自 MySQL 实时聚合</span>
        <el-button :icon="Refresh" :loading="loading" @click="fetchAll">刷新数据</el-button>
      </div>
    </div>

    <div class="kpi-grid">
      <article v-for="item in kpis" :key="item.label" class="kpi-card">
        <div class="kpi-icon" :style="{ background: item.bg, color: item.color }">
          <el-icon :size="22"><component :is="item.icon" /></el-icon>
        </div>
        <div class="kpi-copy">
          <span class="kpi-label">{{ item.label }}</span>
          <strong class="kpi-value">{{ item.value }}</strong>
          <span class="kpi-sub">{{ item.sub }}</span>
        </div>
      </article>
    </div>

    <div class="chart-grid">
      <ChartCard
        title="班级人数分布"
        subtitle="按班级统计在读学员，男生 / 女生堆叠"
        :option="clazzStudentOption"
        :loading="loading"
        :empty="!data.clazzStudent.length"
      />
      <ChartCard
        title="学员学历分布"
        subtitle="六个学历层级，缺失数据补 0"
        :option="degreeOption"
        :loading="loading"
        :empty="!data.degree.length"
      />
      <ChartCard
        title="学员性别比例"
        subtitle="男生与女生占比"
        :option="genderOption"
        :loading="loading"
        :empty="!data.gender.length"
      />
      <ChartCard
        title="课程成绩概览"
        subtitle="平均分（柱）+ 及格率（线）"
        :option="courseScoreOption"
        :loading="loading"
        :empty="!data.courseScore.length"
      />
      <ChartCard
        title="分数段分布"
        subtitle="0-59 / 60-69 / 70-79 / 80-89 / 90-100"
        :option="scoreDistOption"
        :loading="loading"
        :empty="!data.scoreDist.length"
      />
      <ChartCard
        title="班级违纪统计"
        subtitle="违纪次数（线）+ 扣分合计（柱）"
        :option="clazzViolationOption"
        :loading="loading"
        :empty="!data.clazzViolation.length"
      />
      <ChartCard
        title="违纪 Top 10"
        subtitle="按扣分降序，扣分相同看次数"
        :option="violationTopOption"
        :loading="loading"
        :empty="!data.violationTop.length"
      />
      <ChartCard
        title="部门员工分布"
        subtitle="在职 / 离职堆叠"
        :option="deptEmpOption"
        :loading="loading"
        :empty="!data.deptEmp.length"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from '@/utils/feedback'
import { Refresh } from '@element-plus/icons-vue'

import ChartCard from '@/components/ChartCard.vue'
import { useAuthStore } from '@/stores/auth'
import * as reportApi from '@/api/report'
import { getStudents } from '@/api/student'
import { getClazzList, getClazzs } from '@/api/clazz'
import { getEmps } from '@/api/emp'
import { getCourses } from '@/api/course'
import { axisLabel, chartColors, palette, splitLine, tooltipStyle } from '@/utils/chart'

const auth = useAuthStore()
const loading = ref(true)
const data = reactive({
  clazzStudent: [],
  degree: [],
  gender: [],
  courseScore: [],
  scoreDist: [],
  clazzViolation: [],
  violationTop: [],
  deptEmp: []
})
const kpi = reactive({
  students: 0,
  classes: 0,
  emps: 0,
  courses: 0
})
const classList = ref([])
const empRows = ref([])

const today = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long'
})

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 9) return '早上好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const kpis = computed(() => {
  const activeClasses = classList.value.filter((item) => item.status === 1).length
  const finishedClasses = classList.value.filter((item) => item.status === 2).length
  const activeEmps = empRows.value.filter((item) => item.status === 1).length
  const inactiveEmps = empRows.value.filter((item) => item.status === 2).length
  const male = data.gender.find((item) => item.gender === 1)?.count ?? 0
  const female = data.gender.find((item) => item.gender === 2)?.count ?? 0

  return [
    {
      label: '学员总数',
      value: kpi.students,
      sub: `男 ${male} · 女 ${female}`,
      icon: 'UserFilled',
      bg: '#eef2ff',
      color: palette.indigo
    },
    {
      label: '班级总数',
      value: kpi.classes,
      sub: `在读 ${activeClasses} · 结课 ${finishedClasses}`,
      icon: 'School',
      bg: '#ecfeff',
      color: palette.cyan
    },
    {
      label: '员工总数',
      value: kpi.emps,
      sub: `在职 ${activeEmps} · 离职 ${inactiveEmps}`,
      icon: 'User',
      bg: '#ecfdf5',
      color: palette.emerald
    },
    {
      label: '课程总数',
      value: kpi.courses,
      sub: '课程库中的课程数量',
      icon: 'Reading',
      bg: '#fff7ed',
      color: palette.amber
    }
  ]
})

function emptyOption(text = '暂无数据') {
  return {
    title: {
      text,
      left: 'center',
      top: 'middle',
      textStyle: { color: '#94a3b8', fontSize: 13, fontWeight: 400 }
    }
  }
}

const clazzStudentOption = computed(() => {
  const rows = data.clazzStudent
  if (!rows.length) return emptyOption()
  return {
    color: [palette.indigo, palette.rose],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...tooltipStyle },
    legend: {
      top: 0,
      right: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: '#64748b', fontSize: 12 }
    },
    grid: { left: 8, right: 12, top: 38, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: rows.map((item) => item.clazzName),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisLabel: { ...axisLabel, interval: 0, rotate: rows.length > 5 ? 18 : 0 }
    },
    yAxis: { type: 'value', minInterval: 1, axisLabel, splitLine },
    series: [
      {
        name: '男生',
        type: 'bar',
        stack: 'total',
        barWidth: 20,
        data: rows.map((item) => item.maleCount)
      },
      {
        name: '女生',
        type: 'bar',
        stack: 'total',
        barWidth: 20,
        data: rows.map((item) => item.femaleCount),
        itemStyle: { borderRadius: [6, 6, 0, 0] }
      }
    ]
  }
})

const degreeOption = computed(() => {
  const rows = data.degree
  if (!rows.length) return emptyOption()
  const total = rows.reduce((sum, item) => sum + item.count, 0)
  return {
    color: chartColors,
    tooltip: { trigger: 'item', formatter: '{b}<br/>{c} 人（{d}%）', ...tooltipStyle },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: '#64748b', fontSize: 12 }
    },
    graphic: [
      {
        type: 'text',
        left: 'center',
        top: '36%',
        style: {
          text: String(total),
          fill: '#0f172a',
          fontSize: 26,
          fontWeight: 700,
          textAlign: 'center'
        }
      },
      {
        type: 'text',
        left: 'center',
        top: '49%',
        style: { text: '学员', fill: '#94a3b8', fontSize: 12, textAlign: 'center' }
      }
    ],
    series: [
      {
        type: 'pie',
        radius: ['50%', '72%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 3 },
        label: { show: false },
        emphasis: {
          scaleSize: 6,
          label: { show: true, fontSize: 13, fontWeight: 600, formatter: '{b}\n{c} 人' }
        },
        data: rows.map((item) => ({ name: item.degreeName, value: item.count }))
      }
    ]
  }
})

const genderOption = computed(() => {
  const rows = data.gender
  if (!rows.length) return emptyOption()
  return {
    color: [palette.sky, palette.rose],
    tooltip: { trigger: 'item', formatter: '{b}<br/>{c} 人（{d}%）', ...tooltipStyle },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: '#64748b', fontSize: 12 }
    },
    series: [
      {
        type: 'pie',
        radius: ['46%', '70%'],
        center: ['50%', '44%'],
        itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 4 },
        label: { color: '#475569', fontSize: 12, formatter: '{b}\n{d}%' },
        labelLine: { length: 10, length2: 8, lineStyle: { color: '#cbd5e1' } },
        data: rows.map((item) => ({ name: item.genderName, value: item.count }))
      }
    ]
  }
})

const courseScoreOption = computed(() => {
  const rows = data.courseScore
  if (!rows.length) return emptyOption()
  return {
    color: [palette.indigo, palette.amber],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...tooltipStyle },
    legend: {
      top: 0,
      right: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: '#64748b', fontSize: 12 }
    },
    grid: { left: 8, right: 8, top: 38, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: rows.map((item) => item.courseName),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisLabel: { ...axisLabel, interval: 0, rotate: rows.length > 6 ? 20 : 0 }
    },
    yAxis: [
      {
        type: 'value',
        axisLabel,
        splitLine
      },
      {
        type: 'value',
        min: 0,
        max: 100,
        axisLabel,
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '平均分',
        type: 'bar',
        barWidth: 20,
        data: rows.map((item) => item.avgScore),
        itemStyle: { borderRadius: [6, 6, 0, 0] }
      },
      {
        name: '及格率(%)',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        data: rows.map((item) => item.passRate),
        lineStyle: { width: 3 }
      }
    ]
  }
})

const scoreDistOption = computed(() => {
  const rows = data.scoreDist
  if (!rows.length) return emptyOption()
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...tooltipStyle },
    grid: { left: 8, right: 12, top: 18, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: rows.map((item) => item.range),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisLabel
    },
    yAxis: { type: 'value', minInterval: 1, axisLabel, splitLine },
    series: [
      {
        type: 'bar',
        barWidth: 34,
        data: rows.map((item) => item.count),
        itemStyle: {
          borderRadius: [8, 8, 0, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: '#a78bfa' },
              { offset: 1, color: '#6366f1' }
            ]
          }
        },
        label: { show: true, position: 'top', color: '#64748b', fontSize: 11 }
      }
    ]
  }
})

const clazzViolationOption = computed(() => {
  const rows = data.clazzViolation
  if (!rows.length) return emptyOption()
  return {
    color: [palette.rose, palette.amber],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...tooltipStyle },
    legend: {
      top: 0,
      right: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: '#64748b', fontSize: 12 }
    },
    grid: { left: 8, right: 8, top: 38, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: rows.map((item) => item.clazzName),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisLabel: { ...axisLabel, interval: 0, rotate: rows.length > 5 ? 18 : 0 }
    },
    yAxis: [
      {
        type: 'value',
        name: '扣分',
        nameTextStyle: { color: '#94a3b8', fontSize: 11 },
        axisLabel,
        splitLine
      },
      {
        type: 'value',
        name: '次数',
        minInterval: 1,
        nameTextStyle: { color: '#94a3b8', fontSize: 11 },
        axisLabel,
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '扣分合计',
        type: 'bar',
        barWidth: 22,
        data: rows.map((item) => item.totalPoints),
        itemStyle: { borderRadius: [6, 6, 0, 0] }
      },
      {
        name: '违纪次数',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        data: rows.map((item) => item.violationCount),
        lineStyle: { width: 3 }
      }
    ]
  }
})

const violationTopOption = computed(() => {
  const rows = [...data.violationTop].reverse()
  if (!rows.length) return emptyOption()
  return {
    color: [palette.rose],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const item = params[0]
        const row = rows[item.dataIndex]
        return `${row.studentName}（${row.clazzName || '未分班'}）<br/>扣分 ${row.violationScore} · 违纪 ${row.violationCount} 次`
      },
      ...tooltipStyle
    },
    grid: { left: 8, right: 34, top: 8, bottom: 8, containLabel: true },
    xAxis: { type: 'value', minInterval: 1, axisLabel, splitLine },
    yAxis: {
      type: 'category',
      data: rows.map((item) => item.studentName),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisLabel: { ...axisLabel, color: '#475569' }
    },
    series: [
      {
        type: 'bar',
        barWidth: 14,
        data: rows.map((item) => item.violationScore),
        itemStyle: {
          borderRadius: [0, 6, 6, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 1,
            y2: 0,
            colorStops: [
              { offset: 0, color: '#fb7185' },
              { offset: 1, color: '#e11d48' }
            ]
          }
        },
        label: { show: true, position: 'right', color: '#64748b', fontSize: 11, formatter: '{c} 分' }
      }
    ]
  }
})

const deptEmpOption = computed(() => {
  const rows = data.deptEmp
  if (!rows.length) return emptyOption()
  return {
    color: [palette.emerald, palette.slate],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...tooltipStyle },
    legend: {
      top: 0,
      right: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: '#64748b', fontSize: 12 }
    },
    grid: { left: 8, right: 12, top: 38, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: rows.map((item) => item.deptName),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisLabel: { ...axisLabel, interval: 0, rotate: rows.length > 5 ? 18 : 0 }
    },
    yAxis: { type: 'value', minInterval: 1, axisLabel, splitLine },
    series: [
      {
        name: '在职',
        type: 'bar',
        stack: 'emp',
        barWidth: 22,
        data: rows.map((item) => item.activeCount)
      },
      {
        name: '离职',
        type: 'bar',
        stack: 'emp',
        barWidth: 22,
        data: rows.map((item) => item.inactiveCount),
        itemStyle: { borderRadius: [6, 6, 0, 0] }
      }
    ]
  }
})

async function fetchAll() {
  loading.value = true
  const tasks = [
    reportApi.getClazzStudentCount(),
    reportApi.getStudentDegree(),
    reportApi.getStudentGender(),
    reportApi.getCourseScore(),
    reportApi.getScoreDistribution(),
    reportApi.getClazzViolation(),
    reportApi.getViolationTop(),
    reportApi.getDeptEmpCount(),
    getStudents({ page: 1, pageSize: 1 }),
    getClazzs({ page: 1, pageSize: 1 }),
    getClazzList(),
    getEmps({ page: 1, pageSize: 500 }),
    getCourses()
  ]
  const results = await Promise.allSettled(tasks)
  const pick = (index, fallback) => (results[index].status === 'fulfilled' ? results[index].value : fallback)

  data.clazzStudent = pick(0, [])
  data.degree = pick(1, [])
  data.gender = pick(2, [])
  data.courseScore = pick(3, [])
  data.scoreDist = pick(4, [])
  data.clazzViolation = pick(5, [])
  data.violationTop = pick(6, [])
  data.deptEmp = pick(7, [])

  kpi.students = pick(8, { total: 0 }).total ?? 0
  kpi.classes = pick(9, { total: 0 }).total ?? 0
  classList.value = pick(10, [])

  const empPage = pick(11, { rows: [], total: 0 })
  empRows.value = empPage.rows ?? []
  kpi.emps = empPage.total ?? 0
  kpi.courses = (pick(12, []) ?? []).length

  const failed = results.filter((result) => result.status === 'rejected').length
  if (failed > 0) {
    ElMessage.warning(`${failed} 项数据加载失败，已展示其余数据`)
  }
  loading.value = false
}

onMounted(fetchAll)
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}

.eyebrow {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 2.2px;
  color: var(--tl-primary);
}

.page-head h2 {
  margin-top: 6px;
  font-size: 28px;
  line-height: 1.1;
  letter-spacing: -0.7px;
  color: var(--tl-ink);
}

.page-sub {
  margin-top: 7px;
  font-size: 13px;
  color: var(--tl-muted);
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sync-hint {
  font-size: 12px;
  color: #94a3b8;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.kpi-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  border: 1px solid var(--tl-border);
  border-radius: var(--tl-radius);
  background: var(--tl-card);
  box-shadow: var(--tl-shadow);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.kpi-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--tl-shadow-hover);
}

.kpi-icon {
  display: grid;
  place-items: center;
  flex: none;
  width: 50px;
  height: 50px;
  border-radius: 14px;
}

.kpi-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.kpi-label {
  font-size: 12px;
  color: var(--tl-muted);
}

.kpi-value {
  margin-top: 2px;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.8px;
  color: var(--tl-ink);
}

.kpi-sub {
  margin-top: 5px;
  overflow: hidden;
  font-size: 11px;
  color: #94a3b8;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

@media (max-width: 1200px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }

  .page-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .head-actions {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
