import { defineStore } from 'pinia'
import { login, queryCurrentUser, sendLoginCode } from '../api/user'
import type { CurrentUser } from '../types/user'

const TOKEN_KEY = 'food-life-token'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: null as CurrentUser | null,
    loading: false,
  }),
  getters: {
    isLogin: (state) => Boolean(state.token),
  },
  actions: {
    async sendCode(phone: string) {
      await sendLoginCode(phone)
    },
    async loginByCode(phone: string, code: string) {
      this.loading = true
      try {
        const result = await login({ phone, code })
        this.token = result.token
        localStorage.setItem(TOKEN_KEY, result.token)
        await this.fetchMe()
      } finally {
        this.loading = false
      }
    },
    async fetchMe() {
      const token = this.token
      if (!token) return
      const user = await queryCurrentUser()
      if (this.token === token) this.user = user
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      sessionStorage.removeItem('food-life-pending-payment')
    },
  },
})
