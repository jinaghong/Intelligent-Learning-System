<template>
  <div class="page">
    <div class="page-header">
      <div>
        <p class="eyebrow">AUDIT LOG</p>
        <h2>操作日志</h2>
        <p class="sub">AOP 自动记录写操作 · 时间倒序 · 只增不改</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Refresh" :loading="loading" @click="fetchLogs">刷新</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon>
      <template #title>
        日志由后端 <code>LogAspect</code> 自动写入；登录日志还没有操作人（此时尚未认证），
        密码参数已脱敏为 <code>******</code>。查询接口本身不记日志，避免淹没写操作。
      </template>
    </el-alert>

    <div class="panel">
      <div class="table-panel">
        <div class="table-toolbar">
          <span class="total-hint">共 <b>{{ total }}</b> 条日志</span>
          <span class="toolbar-note">接口仅支持分页，暂不支持按操作人 / 时间筛选</span>
        </div>

        <el-table v-loading="loading" :data="rows" row-key="id" stripe>
          <el-table-column label="操作时间" width="180">
            <template #default="{ row }">{{ formatDateTime(row.operateTime) }}</template>
          </el-table-column>
          <el-table-column label="操作人" width="130">
            <template #default="{ row }">
              <span v-if="row.empId">{{ empMap[row.empId] || `员工 #${row.empId}` }}</span>
              <el-tag v-else size="small" type="info" effect="plain">登录接口</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="方法" min-width="240">
            <template #default="{ row }">
              <div class="method-cell">
                <code>{{ shortClass(row.className) }}.{{ row.methodName }}</code>
                <span>{{ row.className }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="86">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'" effect="light" round>
                {{ row.status === 1 ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="96" align="right">
            <template #default="{ row }">
              <span :class="{ slow: row.costTime > 1000 }">{{ row.costTime }} ms</span>
            </template>
          </el-table-column>
          <el-table-column prop="ip" label="IP" width="130">
            <template #default="{ row }">{{ row.ip || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="86" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="View" @click="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="还没有操作日志" :image-size="80" />
          </template>
        </el-table>

        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="query.page"
            v-model:page-size="query.pageSize"
            :page-sizes="[10, 20, 50]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @current-change="fetchLogs"
            @size-change="handleSizeChange"
          />
        </div>
      </div>
    </div>

    <LogDetailDialog
      v-model="detailVisible"
      :log="currentLog"
      :operator-name="operatorName(currentLog)"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Refresh, View } from '@element-plus/icons-vue'

import { getEmps } from '@/api/emp'
import { getLogs } from '@/api/log'
import { formatDateTime } from '@/utils/format'
import LogDetailDialog from './components/LogDetailDialog.vue'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const empMap = ref({})
const detailVisible = ref(false)
const currentLog = ref(null)
const query = reactive({
  page: 1,
  pageSize: 10
})

function shortClass(className) {
  return className?.split('.').pop() || '—'
}

function operatorName(log) {
  if (!log) return '—'
  if (!log.empId) return '未登录 / 登录接口'
  return empMap.value[log.empId] || `员工 #${log.empId}`
}

async function fetchLogs() {
  loading.value = true
  try {
    const data = await getLogs({ page: query.page, pageSize: query.pageSize })
    rows.value = data.rows ?? []
    total.value = data.total ?? 0
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function fetchEmps() {
  try {
    const data = await getEmps({ page: 1, pageSize: 200 })
    empMap.value = Object.fromEntries((data.rows ?? []).map((emp) => [emp.id, emp.name]))
  } catch {
    // 拦截器已提示
  }
}

function handleSizeChange() {
  query.page = 1
  fetchLogs()
}

function openDetail(row) {
  currentLog.value = row
  detailVisible.value = true
}

onMounted(async () => {
  await Promise.all([fetchLogs(), fetchEmps()])
})
</script>

<style scoped>
.toolbar-note {
  font-size: 12px;
  color: var(--tl-muted);
}

.method-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.method-cell code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
  font-weight: 600;
  color: var(--tl-primary);
}

.method-cell span {
  overflow: hidden;
  font-size: 10px;
  color: var(--tl-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.slow {
  color: #d97706;
  font-weight: 600;
}

code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
}
</style>
