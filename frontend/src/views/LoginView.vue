<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import api from '@/api'

const router = useRouter()
const userStore = useUserStore()
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

const login = async () => {
  error.value = ''
  loading.value = true
  try {
    const res = await api.post('/api/auth/login', {
      username: username.value,
      password: password.value
    })
    if (res.data.success) {
      userStore.setUser(res.data.user, res.data.token)
      router.push('/dashboard')
    } else {
      error.value = 'Λάθος username ή password'
    }
  } catch (err) {
    if (err.response?.status === 401) {
      error.value = 'Λάθος username ή password'
    } else {
      error.value = 'Σφάλμα σύνδεσης. Δοκιμάστε ξανά.'
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-logo">N2</div>
      <h1 class="login-title">Next2Cash</h1>
      <p class="login-sub">Group Cash Control</p>
      <div class="login-form">
        <div class="form-group">
          <label>Username</label>
          <input v-model="username" type="text" placeholder="username" @keyup.enter="login" />
        </div>
        <div class="form-group">
          <label>Κωδικός</label>
          <input v-model="password" type="password" placeholder="••••••" @keyup.enter="login" />
        </div>
        <div class="error-msg" v-if="error">⚠ {{ error }}</div>
        <button class="login-btn" @click="login" :disabled="loading">
          {{ loading ? 'Σύνδεση...' : 'Σύνδεση' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page { min-height: 100vh; background: #0f1e2e; display: flex; align-items: center; justify-content: center; }
.login-card { background: #162B40; border-radius: 16px; padding: 48px 40px; width: 360px; text-align: center; border: 1px solid rgba(255,255,255,0.06); }
.login-logo { width: 56px; height: 56px; border-radius: 12px; background: linear-gradient(135deg, #4FC3A1, #2FA585); display: grid; place-items: center; font-weight: 700; color: #0f1e2e; font-size: 20px; margin: 0 auto 16px; }
.login-title { color: #fff; font-size: 1.5rem; font-weight: 700; margin: 0 0 4px; }
.login-sub { color: #8899aa; font-size: 0.85rem; margin: 0 0 32px; }
.form-group { text-align: left; margin-bottom: 16px; }
.form-group label { display: block; color: #8899aa; font-size: 0.8rem; margin-bottom: 6px; }
.form-group input { width: 100%; background: #1e3448; border: 1px solid #2a4a6a; color: #e0e6ed; padding: 10px 14px; border-radius: 8px; font-size: 0.95rem; box-sizing: border-box; }
.form-group input:focus { outline: none; border-color: #4FC3A1; }
.error-msg { background: rgba(239,83,80,0.1); color: #ef5350; padding: 8px 12px; border-radius: 6px; font-size: 0.85rem; margin-bottom: 16px; }
.login-btn { width: 100%; background: #4FC3A1; color: #0f1e2e; border: none; padding: 12px; border-radius: 8px; font-size: 1rem; font-weight: 700; cursor: pointer; }
.login-btn:hover { background: #3db08e; }
.login-btn:disabled { opacity: 0.6; cursor: not-allowed; }
</style>
