import request from './request'

export const login = (data) => request.post('/login', data)
export const logout = () => request.post('/logout')
export const getMe = () => request.get('/login/me')
