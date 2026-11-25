// Состояние входа в систему и функции входа / регистрации / выхода
import { ref, computed } from 'vue'
import { parseApiError } from '../utils/errorHandler.js'

// Адреса входа лежат под /api, поэтому проксирование /api (nginx, vite) менять не нужно
const AUTH_URL = '/api/auth'

// Реактивное состояние. Берём из localStorage, чтобы вход переживал перезагрузку страницы
const accessToken = ref(localStorage.getItem('accessToken') || null)
const refreshToken = ref(localStorage.getItem('refreshToken') || null)
const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))

export function useAuth() {
  // Пользователь вошёл, если есть access-токен
  const isAuthenticated = computed(() => !!accessToken.value)
  // Администратор видит в истории импорта операции всех пользователей
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const username = computed(() => user.value?.username || '')

  // Запомнить токены и данные пользователя (ответ сервера: accessToken, refreshToken, username, role)
  function setAuth(data) {
    accessToken.value = data.accessToken
    refreshToken.value = data.refreshToken
    user.value = { username: data.username, role: data.role }
    localStorage.setItem('accessToken', data.accessToken)
    localStorage.setItem('refreshToken', data.refreshToken)
    localStorage.setItem('user', JSON.stringify(user.value))
  }

  // Забыть всё (выход или недействительный токен)
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

  // Получить новую пару токенов по refresh-токену. Возвращает true, если получилось
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

  // Отправка логина и пароля на сервер (общая часть входа и регистрации)
  async function sendCredentials(path, body, defaultMessage) {
    const response = await fetch(`${AUTH_URL}/${path}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(body)
    })

    if (!response.ok) {
      // Сообщение берём у сервера (например, "Пользователь с таким именем уже существует")
      const data = await parseApiError(response)
      throw new Error(data.message || defaultMessage)
    }

    setAuth(await response.json())
  }

  // Регистрация нового пользователя (после неё пользователь сразу считается вошедшим)
  async function register(usernameValue, password, role = 'USER') {
    await sendCredentials('registration', { username: usernameValue, password, role }, 'Ошибка регистрации')
  }

  // Вход
  async function login(usernameValue, password) {
    await sendCredentials('login', { username: usernameValue, password }, 'Ошибка входа')
  }

  // Выход: сообщаем серверу, чтобы он отозвал токен, и забываем его у себя
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
