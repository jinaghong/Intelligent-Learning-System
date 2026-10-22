export const genderOptions = [
  { label: '男', value: 1 },
  { label: '女', value: 2 }
]

export const jobOptions = [
  { label: '班主任', value: 1 },
  { label: '讲师', value: 2 },
  { label: '学工主管', value: 3 },
  { label: '教研主管', value: 4 },
  { label: '咨询师', value: 5 }
]

export const empStatusOptions = [
  { label: '在职', value: 1 },
  { label: '离职', value: 2 }
]

export const subjectOptions = [
  { label: 'Java', value: 1 },
  { label: '前端', value: 2 },
  { label: '大数据', value: 3 },
  { label: 'Python', value: 4 },
  { label: '测试', value: 5 }
]

export const clazzStatusOptions = [
  { label: '在读', value: 1 },
  { label: '结课', value: 2 }
]

export const degreeOptions = [
  { label: '初中', value: 1 },
  { label: '高中', value: 2 },
  { label: '专科', value: 3 },
  { label: '本科', value: 4 },
  { label: '硕士', value: 5 },
  { label: '博士', value: 6 }
]

export const isCollegeOptions = [
  { label: '否', value: 0 },
  { label: '是', value: 1 }
]

export const genderMap = Object.fromEntries(genderOptions.map((item) => [item.value, item.label]))
export const jobMap = Object.fromEntries(jobOptions.map((item) => [item.value, item.label]))
export const empStatusMap = Object.fromEntries(empStatusOptions.map((item) => [item.value, item.label]))
export const subjectMap = Object.fromEntries(subjectOptions.map((item) => [item.value, item.label]))
export const clazzStatusMap = Object.fromEntries(clazzStatusOptions.map((item) => [item.value, item.label]))
export const degreeMap = Object.fromEntries(degreeOptions.map((item) => [item.value, item.label]))
export const isCollegeMap = Object.fromEntries(isCollegeOptions.map((item) => [item.value, item.label]))

export const labelOf = (map, value) => map[value] ?? '—'
