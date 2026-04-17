import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'https://next2cash-api.azurewebsites.net',
  timeout: 10000,
})

// Attach JWT token + force UTF-8 charset on every request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('n2c_token')
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  // Force charset=utf-8 for Greek characters (fixes Tomcat 403 issue)
  config.headers['Content-Type'] = 'application/json; charset=utf-8'
  config.headers['Accept'] = 'application/json'
  return config
})

// Handle 401 globally - redirect to login
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('n2c_token')
      localStorage.removeItem('n2c_user')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default api