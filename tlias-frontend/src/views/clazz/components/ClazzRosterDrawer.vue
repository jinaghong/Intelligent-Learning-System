<template>
  <el-drawer v-model="visible" :title="drawerTitle" size="640px">
    <div class="roster-body">
      <div class="roster-summary">
        <div>
          <h3>{{ clazz?.name || '班级学员' }}</h3>
          <p>{{ summary }}</p>
        </div>
        <el-button
          type="primary"
          :icon="Download"
          :loading="exporting"
          :disabled="!total || loading"
          @click="handleExport"
        >
          导出 Excel
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" row-key="id" stripe class="roster-table">
        <el-table-column label="姓名" min-width="100">
          <template #default="{ row }">
            <span class="roster-name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="no" label="学号" min-width="110" />
        <el-table-column label="性别" width="64">
          <template #default="{ row }">{{ labelOf(genderMap, row.gender) }}</template>
        </el-table-column>
        <el-table-column label="手机号" min-width="125">
          <template #default="{ row }">{{ row.phone || '—' }}</template>
        </el-table-column>
        <el-table-column label="学历" width="70">
          <template #default="{ row }">{{ labelOf(degreeMap, row.degree) }}</template>
        </el-table-column>
        <el-table-column label="违纪" min-width="110">
          <template #default="{ row }">
            <el-tag v-if="row.violationCount" size="small" type="danger" effect="plain">
              {{ row.violationCount }} 次 / {{ row.violationScore }} 分
            </el-tag>
            <span v-else class="no-violation">无</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="该班级还没有学员" :image-size="72" />
        </template>
      </el-table>

      <div class="roster-pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          small
          background
          @current-change="fetchRoster"
          @size-change="handleSizeChange"
        />
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from '@/utils/feedback'
import { Download } from '@element-plus/icons-vue'

import { exportStudents, getStudents } from '@/api/student'
import { degreeMap, genderMap, labelOf } from '@/utils/dict'
import { saveBlob } from '@/utils/download'

const props = defineProps({
  clazz: {
    type: Object,
    default: null
  }
})
const visible = defineModel({ type: Boolean, default: false })

const loading = ref(false)
const exporting = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

const drawerTitle = computed(() =>
  props.clazz?.name ? `${props.clazz.name} · 学员名单` : '班级学员'
)
const summary = computed(() => {
  const master = props.clazz?.masterName ? `班主任 ${props.clazz.masterName}` : '班主任未分配'
  return `${master} · 共 ${total.value} 人`
})

async function fetchRoster() {
  if (!props.clazz?.id) return
  loading.value = true
  try {
    const data = await getStudents({
      clazzId: props.clazz.id,
      page: page.value,
      pageSize: pageSize.value
    })
    rows.value = data.rows ?? []
    total.value = data.total ?? 0
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function resetAndFetch() {
  page.value = 1
  rows.value = []
  total.value = 0
  fetchRoster()
}

function handleSizeChange() {
  page.value = 1
  fetchRoster()
}

async function handleExport() {
  if (!props.clazz?.id || exporting.value) return
  exporting.value = true
  try {
    const response = await exportStudents({ clazzId: props.clazz.id })
    await saveBlob(response, `${props.clazz.name}-学员名单.xlsx`)
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error(error.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

watch(visible, (open) => {
  if (open) resetAndFetch()
})
watch(
  () => props.clazz?.id,
  () => {
    if (visible.value) resetAndFetch()
  }
)
</script>

<style scoped>
.roster-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 100%;
}

.roster-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--tl-border);
  border-radius: 14px;
  background: linear-gradient(135deg, #f8faff, #eef2ff);
}

.roster-summary h3 {
  font-size: 15px;
  color: var(--tl-ink);
}

.roster-summary p {
  margin-top: 4px;
  font-size: 12px;
  color: var(--tl-muted);
}

.roster-table {
  flex: 1;
}

.roster-name {
  font-weight: 600;
  color: var(--tl-ink);
}

.no-violation {
  font-size: 12px;
  color: var(--tl-muted);
}

.roster-pagination {
  display: flex;
  justify-content: flex-end;
}
</style>
