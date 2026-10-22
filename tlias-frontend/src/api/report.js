import request from './request'

export const getClazzStudentCount = () => request.get('/report/clazz-student-count')
export const getStudentDegree = () => request.get('/report/student-degree')
export const getStudentGender = () => request.get('/report/student-gender')
export const getCourseScore = () => request.get('/report/course-score')
export const getScoreDistribution = () => request.get('/report/score-distribution')
export const getClazzViolation = () => request.get('/report/clazz-violation')
export const getViolationTop = () => request.get('/report/violation-top')
export const getDeptEmpCount = () => request.get('/report/dept-emp-count')
