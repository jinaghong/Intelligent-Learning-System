import request from './request'

export const getCourses = () => request.get('/courses')
export const getCourseDetail = (id) => request.get(`/courses/${id}`)
export const createCourse = (data) => request.post('/courses', data)
export const updateCourse = (data) => request.put('/courses', data)
export const deleteCourse = (id) => request.delete(`/courses/${id}`)
