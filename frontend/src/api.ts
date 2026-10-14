import axios from 'axios'
import type { Camera, Incident, Stats } from './types'

export const api = axios.create({ baseURL: import.meta.env.VITE_API_URL || '/api' })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('cypher_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export async function login(email: string, password: string) {
  const { data } = await api.post<{ token: string; user: { displayName: string; role: string } }>('/auth/login', { email, password })
  localStorage.setItem('cypher_token', data.token)
  localStorage.setItem('cypher_user', JSON.stringify(data.user))
  return data
}

export async function loadDashboard() {
  const [stats, incidents, cameras] = await Promise.all([
    api.get<Stats>('/dashboard/stats'),
    api.get<Incident[]>('/incidents'),
    api.get<Camera[]>('/cameras'),
  ])
  return { stats: stats.data, incidents: incidents.data, cameras: cameras.data }
}

export async function setIncidentStatus(id: number, status: Incident['status']) {
  const { data } = await api.patch<Incident>(`/incidents/${id}/status`, { status })
  return data
}

export async function runDemo(scenario: 'threat' | 'health') {
  const response = await fetch(`/ai/demo/${scenario}?camera_id=1`, { method: 'POST' })
  if (!response.ok) throw new Error(await response.text())
  return response.json()
}