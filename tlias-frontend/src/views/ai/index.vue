<template>
  <div class="page">
    <div class="page-header">
      <div>
        <p class="eyebrow">AI STUDIO</p>
        <h2>AI 助手</h2>
        <p class="sub">学习建议 · 智能出题 · 每次调用都会写入 ai_call_log</p>
      </div>
    </div>

    <div class="ai-banner">
      <div class="banner-icon">
        <el-icon :size="22"><MagicStick /></el-icon>
      </div>
      <div class="banner-text">
        <strong>当前模型由后端 profile 决定</strong>
        <p>
          默认 Mock：不联网、不花 token，结果带 <code>[MOCK]</code> 标记；以
          <code>dev,deepseek</code> 启动后调用真实模型。返回值里的
          <code>model</code> 字段会如实显示来源（mock / deepseek-flash / FALLBACK）。
        </p>
      </div>
      <el-tag type="info" effect="plain">后端 /ai/*</el-tag>
    </div>

    <div class="ai-grid">
      <section class="ai-card">
        <div class="card-head">
          <div class="card-icon indigo">
            <el-icon :size="18"><Reading /></el-icon>
          </div>
          <div>
            <h3>学习建议</h3>
            <p>基于学员成绩与违纪数据，为班主任生成可执行建议</p>
          </div>
          <el-tooltip content="重新生成" placement="top">
            <el-button
              class="refresh-button"
              circle
              :icon="Refresh"
              :loading="adviceLoading"
              :disabled="!studentId"
              aria-label="重新生成学习建议"
              @click="generateAdvice"
            />
          </el-tooltip>
        </div>

        <div class="control-row">
          <el-select
            v-model="studentId"
            filterable
            placeholder="选择学员"
            style="flex: 1"
          >
            <el-option
              v-for="student in students"
              :key="student.id"
              :label="`${student.name}（${student.no}）`"
              :value="student.id"
            />
          </el-select>
          <el-button
            type="primary"
            :icon="MagicStick"
            :loading="adviceLoading"
            @click="generateAdvice"
          >
            生成建议
          </el-button>
        </div>

        <div class="result-area">
          <el-skeleton v-if="adviceLoading" :rows="4" animated />
          <template v-else-if="advice">
            <div class="result-meta">
              <el-tag :type="modelTagType(advice.model)" effect="plain">
                模型 {{ advice.model }}
              </el-tag>
              <span>{{ advice.studentName }} · {{ formatDateTime(advice.createTime) }}</span>
            </div>
            <p class="advice-text">{{ advice.advice }}</p>
          </template>
          <el-empty v-else description="选择学员后生成学习建议" :image-size="72" />
        </div>
      </section>

      <section class="ai-card">
        <div class="card-head">
          <div class="card-icon amber">
            <el-icon :size="18"><Promotion /></el-icon>
          </div>
          <div>
            <h3>智能出题</h3>
            <p>按课程、数量、难度生成练习题，含答案与解析</p>
          </div>
          <el-tooltip content="重新生成" placement="top">
            <el-button
              class="refresh-button"
              circle
              :icon="Refresh"
              :loading="questionsLoading"
              :disabled="!courseId"
              aria-label="重新生成题目"
              @click="generateQuestions"
            />
          </el-tooltip>
        </div>

        <div class="control-stack">
          <el-select v-model="courseId" filterable placeholder="选择课程">
            <el-option
              v-for="course in courses"
              :key="course.id"
              :label="`${course.name}（${course.no}）`"
              :value="course.id"
            />
          </el-select>
          <div class="control-row">
            <el-input-number v-model="questionCount" :min="1" :max="20" />
            <el-select v-model="difficulty" style="width: 120px">
              <el-option
                v-for="item in difficultyOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <el-button
              type="primary"
              :icon="MagicStick"
              :loading="questionsLoading"
              @click="generateQuestions"
            >
              生成题目
            </el-button>
          </div>
        </div>

        <div class="result-area question-area">
          <div v-if="questionsLoading" class="question-skeleton">
            <el-skeleton v-for="item in 2" :key="item" :rows="3" animated />
          </div>
          <template v-else-if="questions.length">
            <article v-for="(question, index) in questions" :key="index" class="question-card">
              <div class="q-stem">
                <span class="q-index">Q{{ index + 1 }}</span>
                <strong>{{ question.stem }}</strong>
              </div>
              <ul class="q-options">
                <li v-for="(option, optionIndex) in question.options" :key="optionIndex">
                  <span class="opt-letter">{{ 'ABCD'[optionIndex] || optionIndex + 1 }}</span>
                  {{ option }}
                </li>
              </ul>
              <div class="q-foot">
                <el-button link type="primary" @click="toggleAnswer(index)">
                  {{ answerVisible[index] ? '收起答案' : '查看答案与解析' }}
                </el-button>
                <el-tag v-if="answerVisible[index]" type="success" effect="plain">
                  答案 {{ question.answer }}
                </el-tag>
              </div>
              <p v-if="answerVisible[index]" class="q-analysis">{{ question.analysis }}</p>
            </article>
          </template>
          <el-empty v-else description="选择课程后生成练习题" :image-size="72" />
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from '@/utils/feedback'
import { MagicStick, Promotion, Reading, Refresh } from '@element-plus/icons-vue'

import { getAdvice, getQuestions } from '@/api/ai'
import { getCourses } from '@/api/course'
import { getStudents } from '@/api/student'
import { formatDateTime } from '@/utils/format'
import { getUser } from '@/utils/token'

const difficultyOptions = [
  { label: '简单', value: 1 },
  { label: '中等', value: 2 },
  { label: '困难', value: 3 }
]

const AI_CACHE_KEY = 'tlias_ai_cache'

function cacheOwnerId() {
  return getUser()?.id ?? 'anonymous'
}

function readAiCache() {
  try {
    const cache = JSON.parse(localStorage.getItem(AI_CACHE_KEY) || 'null')
    return cache?.ownerId === cacheOwnerId() ? cache : null
  } catch {
    return null
  }
}

function writeAiCache(patch) {
  try {
    localStorage.setItem(
      AI_CACHE_KEY,
      JSON.stringify({ ...(readAiCache() || {}), ...patch, ownerId: cacheOwnerId() })
    )
  } catch {
    // 存储被禁用或已满时，缓存失败不影响本次结果展示
  }
}

const students = ref([])
const courses = ref([])
const studentId = ref(null)
const courseId = ref(null)
const questionCount = ref(3)
const difficulty = ref(2)
const advice = ref(null)
const adviceLoading = ref(false)
const questions = ref([])
const questionsLoading = ref(false)
const answerVisible = reactive({})

function modelTagType(model) {
  if (model === 'FALLBACK') return 'warning'
  if (model === 'mock') return 'info'
  return 'success'
}

function toggleAnswer(index) {
  answerVisible[index] = !answerVisible[index]
}

async function loadOptions() {
  try {
    const [studentPage, courseList] = await Promise.all([
      getStudents({ page: 1, pageSize: 200 }),
      getCourses()
    ])
    students.value = studentPage.rows ?? []
    courses.value = courseList ?? []

    const cache = readAiCache()
    const cachedAdvice = cache?.advice
    const adviceOwnerExists =
      cachedAdvice && students.value.some((student) => student.id === cachedAdvice.studentId)
    studentId.value = adviceOwnerExists ? cachedAdvice.studentId : students.value[0]?.id ?? null
    if (adviceOwnerExists) advice.value = cachedAdvice

    const cachedQuestions = cache?.questions
    const questionsOwnerExists =
      cachedQuestions && courses.value.some((course) => course.id === cachedQuestions.courseId)
    courseId.value = questionsOwnerExists ? cachedQuestions.courseId : courses.value[0]?.id ?? null
    if (questionsOwnerExists) {
      questionCount.value = cachedQuestions.count ?? questionCount.value
      difficulty.value = cachedQuestions.difficulty ?? difficulty.value
      questions.value = cachedQuestions.list ?? []
    }
  } catch {
    // 拦截器已提示
  }
}

async function generateAdvice() {
  if (!studentId.value) {
    ElMessage.warning('请先选择学员')
    return
  }
  adviceLoading.value = true
  advice.value = null
  try {
    advice.value = await getAdvice(studentId.value)
    if (advice.value) writeAiCache({ advice: advice.value })
  } catch {
    // 拦截器已提示
  } finally {
    adviceLoading.value = false
  }
}

async function generateQuestions() {
  if (!courseId.value) {
    ElMessage.warning('请先选择课程')
    return
  }
  questionsLoading.value = true
  questions.value = []
  Object.keys(answerVisible).forEach((key) => delete answerVisible[key])
  try {
    questions.value = await getQuestions({
      courseId: courseId.value,
      count: questionCount.value,
      difficulty: difficulty.value
    })
    if (questions.value.length) {
      writeAiCache({
        questions: {
          courseId: courseId.value,
          count: questionCount.value,
          difficulty: difficulty.value,
          list: questions.value
        }
      })
    }
  } catch {
    // 拦截器已提示
  } finally {
    questionsLoading.value = false
  }
}

onMounted(loadOptions)
</script>

<style scoped>
.ai-banner {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  border: 1px solid #e0e7ff;
  border-radius: var(--tl-radius);
  background: linear-gradient(120deg, #f8faff, #eef2ff 60%, #f5f3ff);
}

.banner-icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 13px;
  color: #fff;
  background: linear-gradient(135deg, #818cf8, #4f46e5);
  box-shadow: 0 8px 18px rgba(79, 70, 229, 0.28);
}

.banner-text {
  flex: 1;
  min-width: 0;
}

.banner-text strong {
  font-size: 14px;
  color: var(--tl-ink);
}

.banner-text p {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--tl-muted);
}

.banner-text code {
  padding: 1px 5px;
  border-radius: 5px;
  background: #e0e7ff;
  color: #4338ca;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
}

.ai-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.15fr);
  gap: 16px;
  align-items: start;
}

.ai-card {
  padding: 20px;
  border: 1px solid var(--tl-border);
  border-radius: var(--tl-radius);
  background: var(--tl-card);
  box-shadow: var(--tl-shadow);
}

.card-head {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  margin-bottom: 16px;
}

.card-icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
}

.card-icon.indigo {
  color: #4f46e5;
  background: #eef2ff;
}

.card-icon.amber {
  color: #b45309;
  background: #fffbeb;
}

.card-head h3 {
  font-size: 15px;
  color: var(--tl-ink);
}

.card-head p {
  margin-top: 3px;
  font-size: 12px;
  color: var(--tl-muted);
}

.refresh-button {
  flex: none;
  margin-left: auto;
}

.control-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.control-stack {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.result-area {
  margin-top: 16px;
  padding: 16px;
  border: 1px dashed var(--tl-border);
  border-radius: 12px;
  background: #fbfcff;
  min-height: 260px;
}

.result-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.result-meta span {
  font-size: 11px;
  color: var(--tl-muted);
}

.advice-text {
  font-size: 13px;
  line-height: 1.9;
  color: var(--tl-text);
  white-space: pre-wrap;
}

.question-area {
  max-height: 560px;
  overflow: auto;
}

.question-skeleton {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.question-card {
  padding: 14px;
  border: 1px solid var(--tl-border);
  border-radius: 12px;
  background: #fff;
}

.question-card + .question-card {
  margin-top: 12px;
}

.q-stem {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  font-size: 13px;
  line-height: 1.65;
  color: var(--tl-ink);
}

.q-index {
  flex: none;
  padding: 1px 7px;
  border-radius: 6px;
  background: #eef2ff;
  color: var(--tl-primary);
  font-size: 11px;
  font-weight: 700;
}

.q-options {
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin: 11px 0 0;
  padding: 0;
  list-style: none;
}

.q-options li {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 12px;
  line-height: 1.55;
  color: var(--tl-text);
}

.opt-letter {
  display: grid;
  flex: none;
  place-items: center;
  width: 18px;
  height: 18px;
  border-radius: 5px;
  background: #f1f5f9;
  color: var(--tl-muted);
  font-size: 10px;
  font-weight: 700;
}

.q-foot {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}

.q-analysis {
  margin-top: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f0fdf4;
  color: #166534;
  font-size: 12px;
  line-height: 1.65;
}

@media (max-width: 1100px) {
  .ai-grid {
    grid-template-columns: 1fr;
  }
}
</style>
