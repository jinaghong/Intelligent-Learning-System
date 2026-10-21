import { defineStore } from 'pinia'

import { getMe, login as loginApi, logout as logoutApi } from '@/api/auth'
import { clearToken, clearUser, getToken, getUser, setToken, setUser } from '@/utils/token'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getToken(),
    user: getUser()
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    displayName: (state) => state.user?.name || state.user?.username || '管理员',
    avatar: (state) => state.user?.image || ''
  },
  actions: {
    async login(form) {
      const data = await loginApi(form)
      this.token = data.token
      setToken(data.token)
      try {
        this.user = await getMe()
      } catch {
        this.user = { id: data.id, username: data.username, name: data.name }
      }
      setUser(this.user)
      return this.user
    },
    async fetchMe() {
      this.user = await getMe()
      setUser(this.user)
      return this.user
    },
    async logout() {
      try {
        await logoutApi()
      } catch {
        // 服务端不维护 token 黑名单，登出接口失败也照样清本地状态
      }
      this.token = ''
      this.user = null
      clearToken()
      clearUser()
    }
  }
})
