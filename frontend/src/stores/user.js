import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  const profile = ref(JSON.parse(localStorage.getItem('n2c_user') || 'null'))
  const token = ref(localStorage.getItem('n2c_token') || null)
  const isAuthenticated = computed(() => !!token.value)

  function setUser(userData, newToken) {
    profile.value = userData
    token.value = newToken
    localStorage.setItem('n2c_user', JSON.stringify(userData))
    localStorage.setItem('n2c_token', newToken)
  }

  function logout() {
    profile.value = null
    token.value = null
    localStorage.removeItem('n2c_user')
    localStorage.removeItem('n2c_token')
  }

  return { profile, token, isAuthenticated, setUser, logout }
})
