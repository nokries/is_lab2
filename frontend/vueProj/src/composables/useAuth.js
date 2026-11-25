import { ref, computed } from 'vue'
import { parseApiError } from '../utils/errorHandler.js'

const AUTH_URL = '/api/auth'
const accessToken = ref(localStorage.getItem('accessToken') || null)
const refreshToken = ref(localStorage.getItem('refreshToken') || null)
const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))

export function useAuth() {
  const isAuthenticated = computed(() => !!accessToken.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const username = computed(() => user.value?.username || '')

  function setAuth(data) {
    accessToken.value = data.accessToken
    refreshToken.value = data.refreshToken
    user.value = { username: data.username, role: data.role }
    localStorage.setItem('accessToken', data.accessToken)
    localStorage.setItem('refreshToken', data.refreshToken)
    localStorage.setItem('user', JSON.stringify(user.value))
  }


  function clearAuth() {
    accessToken.value = null
    refreshToken.value = null
    user.value = null
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('user')
  }

  function getAccessToken() {
    return accessToken.value
  }


  async function refreshTokens() {
    if (!refreshToken.value) {
      clearAuth()
      return false
    }

    try {
      const response = await fetch(`${AUTH_URL}/refresh_token`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${refreshToken.value}`
        }
      })

      if (!response.ok) {
        clearAuth()
        return false
      }

      setAuth(await response.json())
      return true
    } catch (error) {
      console.error('Не удалось обновить токен:', error)
      clearAuth()
      return false
    }
  }

  async function sendCredentials(path, body, defaultMessage) {
    const response = await fetch(`${AUTH_URL}/${path}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(body)
    })

    if (!response.ok) {
      const data = await parseApiError(response)
      throw new Error(data.message || defaultMessage)
    }

    setAuth(await response.json())
  }

  async function register(usernameValue, password, role = 'USER') {
    await sendCredentials('registration', { username: usernameValue, password, role }, 'Ошибка регистрации')
  }

  async function login(usernameValue, password) {
    await sendCredentials('login', { username: usernameValue, password }, 'Ошибка входа')
  }


  async function logout() {
    try {
      await fetch(`${AUTH_URL}/logout`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${accessToken.value}`
        }
      })
    } catch (error) {
      console.error('Ошибка выхода:', error)
    }
    clearAuth()
  }

  return {
    isAuthenticated,
    isAdmin,
    username,
    user,
    getAccessToken,
    setAuth,
    clearAuth,
    refreshTokens,
    register,
    login,
    logout
  }
}
