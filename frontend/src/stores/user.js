import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * User store — holds the currently authenticated user and auth state.
 * Placeholder until the Spring Boot backend auth is wired up.
 */
export const useUserStore = defineStore('user', () => {
  const profile = ref({
    name: 'Guest',
    email: '',
    role: 'viewer'
  })
  const token = ref(null)

  const isAuthenticated = computed(() => !!token.value)

  function setUser(newProfile, newToken = null) {
    profile.value = { ...profile.value, ...newProfile }
    if (newToken) token.value = newToken
  }

  function logout() {
    profile.value = { name: 'Guest', email: '', role: 'viewer' }
    token.value = null
  }

  return { profile, token, isAuthenticated, setUser, logout }
})
