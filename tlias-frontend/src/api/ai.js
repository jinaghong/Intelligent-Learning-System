import request from './request'

export const getAdvice = (studentId) => request.get(`/ai/advice/${studentId}`)
export const getQuestions = (params) => request.get('/ai/questions', { params })
