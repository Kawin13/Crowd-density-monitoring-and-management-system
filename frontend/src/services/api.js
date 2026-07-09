import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('accessToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// RACE CONDITION FIX: if several requests expire at the same moment (e.g.
// the dashboard's two parallel calls to /dashboard and /analytics/daily
// both 401 together), each one previously called /auth/refresh
// independently — racing each other and doing redundant work. This shared
// promise ensures only ONE refresh call is ever in flight at a time; every
// concurrent 401 waits on the same result.
let refreshPromise = null

function performTokenRefresh(baseURL) {
  if (!refreshPromise) {
    const refreshToken = localStorage.getItem('refreshToken')
    refreshPromise = axios
      .post(`${baseURL}/auth/refresh?token=${refreshToken}`)
      .then(res => {
        const { accessToken } = res.data.data
        localStorage.setItem('accessToken', accessToken)
        api.defaults.headers.common['Authorization'] = `Bearer ${accessToken}`
        return accessToken
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

api.interceptors.response.use(
  res => res,
  async err => {
    const original = err.config
    if (err.response?.status === 401 && !original._retry) {
      original._retry = true
      const refreshToken = localStorage.getItem('refreshToken')

      if (refreshToken) {
        try {
          const accessToken = await performTokenRefresh(original.baseURL)
          original.headers.Authorization = `Bearer ${accessToken}`
          return api(original)
        } catch {
          // Refresh token itself is invalid/expired — force a clean logout.
          redirectToLogin()
          return Promise.reject(err)
        }
      }

      // BUG FIX: previously, if refreshToken was missing entirely (e.g.
      // already cleared, or never set), execution fell through to
      // Promise.reject(err) below WITHOUT ever clearing localStorage or
      // redirecting — leaving the user stuck on whatever page they were on
      // with every subsequent API call failing the exact same way forever.
      // This was the root cause of pages appearing to "load forever": the
      // component's own catch-block would show an error toast, but nothing
      // ever navigated the user back to a working state.
      redirectToLogin()
    }
    return Promise.reject(err)
  }
)

function redirectToLogin() {
  localStorage.clear()
  delete api.defaults.headers.common['Authorization']
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

export default api
