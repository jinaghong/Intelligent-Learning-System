<template>
  <el-dialog v-model="visible" title="日志详情" width="760px" top="6vh">
    <template v-if="log">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="日志 ID">{{ log.id }}</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ formatDateTime(log.operateTime) }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ operatorName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="log.status === 1 ? 'success' : 'danger'" effect="light" round>
            {{ log.status === 1 ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="方法" :span="2">
          <code>{{ shortClass }}.{{ log.methodName }}</code>
        </el-descriptions-item>
        <el-descriptions-item label="耗时">{{ log.costTime }} ms</el-descriptions-item>
        <el-descriptions-item label="IP">{{ log.ip || '—' }}</el-descriptions-item>
      </el-descriptions>

      <div class="payload">
        <div class="payload-head">
          <h4>请求参数</h4>
          <span>密码等敏感字段已由后端脱敏为 ******</span>
        </div>
        <pre>{{ formatJson(log.methodParams) }}</pre>
      </div>

      <div class="payload">
        <div class="payload-head">
          <h4>{{ log.status === 1 ? '返回值' : '异常信息' }}</h4>
        </div>
        <pre v-if="log.status === 1">{{ formatJson(log.returnValue) }}</pre>
        <pre v-else class="error-text">{{ log.errorMsg || '—' }}</pre>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'

import { formatDateTime, formatJson } from '@/utils/format'

const props = defineProps({
  log: {
    type: Object,
    default: null
  },
  operatorName: {
    type: String,
    default: '—'
  }
})
const visible = defineModel({ type: Boolean, default: false })

const shortClass = computed(() => props.log?.className?.split('.').pop() || '—')
</script>

<style scoped>
.payload {
  margin-top: 18px;
}

.payload-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.payload-head h4 {
  font-size: 13px;
  color: var(--tl-ink);
}

.payload-head span {
  font-size: 11px;
  color: var(--tl-muted);
}

pre {
  max-height: 220px;
  margin: 0;
  padding: 12px 14px;
  overflow: auto;
  border: 1px solid var(--tl-border);
  border-radius: 10px;
  background: #0f172a;
  color: #e2e8f0;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-all;
}

pre.error-text {
  background: #450a0a;
  color: #fecaca;
}

code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
  color: var(--tl-primary);
}
</style>
