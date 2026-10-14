import { FormEvent, useEffect, useMemo, useRef, useState } from 'react'
import { Client, StompSubscription } from '@stomp/stompjs'
import { loadDashboard, login, runDemo, setIncidentStatus } from './api'
import type { Camera, Incident, Stats } from './types'

type Page =
  | 'dashboard'
  | 'incidents'
  | 'details'
  | 'cameras'
  | 'analytics'

type Filter = 'ALL' | 'CRITICAL' | 'HIGH' | 'OPEN'

type StoredUser = {
  displayName?: string
  role?: string
}

const emptyStats: Stats = {
  open: 0,
  acknowledged: 0,
  resolved: 0,
  critical: 0,
  high: 0,
}

function formatDate(value?: string) {
  if (!value) return '—'

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) return value

  return date.toLocaleString()
}

function percent(value: number) {
  return `${Math.round(value * 100)}%`
}

function readStoredUser(): StoredUser {
  try {
    return JSON.parse(localStorage.getItem('cypher_user') || '{}')
  } catch {
    return {}
  }
}

export function App() {
  const [authenticated, setAuthenticated] = useState(
    () => !!localStorage.getItem('cypher_token')
  )

  const [loginEmail, setLoginEmail] = useState('')
  const [loginPassword, setLoginPassword] = useState('')
  const [loginLoading, setLoginLoading] = useState(false)
  const [loginError, setLoginError] = useState<string | null>(null)

  const [page, setPage] = useState<Page>('dashboard')
  const [incidents, setIncidents] = useState<Incident[]>([])
  const [cameras, setCameras] = useState<Camera[]>([])
  const [stats, setStats] = useState<Stats>(emptyStats)

  const [selectedIncident, setSelectedIncident] =
    useState<Incident | null>(null)

  const [filter, setFilter] = useState<Filter>('ALL')
  const [online, setOnline] = useState(false)
  const [loading, setLoading] = useState(false)
  const [actionLoading, setActionLoading] = useState(false)
  const [toast, setToast] = useState<string | null>(null)

  const subscriptionRef = useRef<StompSubscription | null>(null)

  const user = readStoredUser()

  const refreshDashboard = async () => {
    if (!localStorage.getItem('cypher_token')) return

    try {
      setLoading(true)

      const data = await loadDashboard()

      setStats(data.stats)
      setIncidents(data.incidents)
      setCameras(data.cameras)
    } catch (error) {
      console.error('Dashboard load failed:', error)

      const status =
        typeof error === 'object' &&
        error !== null &&
        'response' in error
          ? (error as { response?: { status?: number } }).response?.status
          : undefined

      if (status === 401 || status === 403) {
        logout()
        setToast('Session expired. Please sign in again.')
      } else {
        setToast('Unable to connect to CYPHER Gateway')
      }
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (!authenticated) {
      setOnline(false)
      return
    }

    refreshDashboard()

    const protocol =
      window.location.protocol === 'https:' ? 'wss' : 'ws'

    const client = new Client({
      brokerURL: `${protocol}://${window.location.host}/ws`,
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    })

    client.onConnect = () => {
      setOnline(true)

      subscriptionRef.current?.unsubscribe()

      subscriptionRef.current = client.subscribe(
        '/topic/incidents',
        (message) => {
          try {
            const incident: Incident = JSON.parse(message.body)

            setIncidents((current) => [
              incident,
              ...current.filter((item) => item.id !== incident.id),
            ])

            setToast(
              `${incident.priority} ${incident.incidentType} detected on ${incident.cameraName}`
            )

            refreshDashboard()
          } catch (error) {
            console.error('WebSocket message error:', error)
          }
        }
      )
    }

    client.onWebSocketClose = () => setOnline(false)
    client.onStompError = () => setOnline(false)

    client.activate()

    return () => {
      subscriptionRef.current?.unsubscribe()
      subscriptionRef.current = null
      client.deactivate()
    }
  }, [authenticated])

  useEffect(() => {
    if (!toast) return

    const timer = window.setTimeout(() => setToast(null), 4000)

    return () => window.clearTimeout(timer)
  }, [toast])

  const handleLogin = async (
    event: FormEvent<HTMLFormElement>
  ) => {
    event.preventDefault()

    try {
      setLoginLoading(true)
      setLoginError(null)

      await login(loginEmail.trim(), loginPassword)

      setAuthenticated(true)
      setLoginPassword('')
      setPage('dashboard')
    } catch (error) {
      console.error(error)
      setLoginError('Invalid email or password')
    } finally {
      setLoginLoading(false)
    }
  }

  const logout = () => {
    localStorage.removeItem('cypher_token')
    localStorage.removeItem('cypher_user')

    setAuthenticated(false)
    setSelectedIncident(null)
    setPage('dashboard')
    setIncidents([])
    setCameras([])
    setStats(emptyStats)
  }

  const openIncident = (incident: Incident) => {
    setSelectedIncident(incident)
    setPage('details')
  }

  const changeIncidentStatus = async (
    incident: Incident,
    status: Incident['status']
  ) => {
    try {
      setActionLoading(true)

      const updated = await setIncidentStatus(incident.id, status)

      setIncidents((current) =>
        current.map((item) =>
          item.id === updated.id ? updated : item
        )
      )

      setSelectedIncident(updated)
      setToast(`Incident #${updated.id} marked ${status}`)

      await refreshDashboard()
    } catch (error) {
      console.error(error)
      setToast('Unable to update incident status')
    } finally {
      setActionLoading(false)
    }
  }

  const triggerDemo = async (
    scenario: 'threat' | 'health'
  ) => {
    try {
      setActionLoading(true)

      await runDemo(scenario)

      setToast(
        scenario === 'threat'
          ? 'Threat simulation triggered'
          : 'Health simulation triggered'
      )

      await refreshDashboard()
    } catch (error) {
      console.error(error)
      setToast('AI Engine simulation failed')
    } finally {
      setActionLoading(false)
    }
  }

  const filteredIncidents = useMemo(() => {
    return incidents.filter((incident) => {
      if (filter === 'CRITICAL') {
        return incident.priority === 'CRITICAL'
      }

      if (filter === 'HIGH') {
        return incident.priority === 'HIGH'
      }

      if (filter === 'OPEN') {
        return incident.status === 'OPEN'
      }

      return true
    })
  }, [incidents, filter])

  const activeCameras =
    cameras.filter((camera) => camera.active).length

  const latestIncident = incidents[0]

  const pageTitle = {
    dashboard: 'Live Cockpit',
    incidents: 'Incidents',
    details: 'Incident Details',
    cameras: 'Camera Network',
    analytics: 'Analytics & Reports',
  }[page]

  if (!authenticated) {
    return (
      <div className="login-page">
        <div className="login-visual">
          <div className="login-brand">
            <div className="logo-mark">C</div>
            <div>
              <strong>CYPHER</strong>
              <span>Intelligent Surveillance</span>
            </div>
          </div>

          <div className="login-copy">
            <span className="kicker">AI-POWERED SECURITY</span>

            <h1>
              See threats.
              <br />
              Respond faster.
            </h1>

            <p>
              Unified surveillance, intelligent incident detection
              and real-time response in one operational platform.
            </p>

            <div className="login-status-grid">
              <div>
                <strong>24/7</strong>
                <span>Monitoring</span>
              </div>

              <div>
                <strong>Live</strong>
                <span>Detection</span>
              </div>

              <div>
                <strong>AWS</strong>
                <span>Cloud Connected</span>
              </div>
            </div>
          </div>
        </div>

        <div className="login-form-side">
          <form className="login-card" onSubmit={handleLogin}>
            <div className="mobile-brand">
              <div className="logo-mark">C</div>
              <strong>CYPHER</strong>
            </div>

            <span className="kicker">SECURE ACCESS</span>

            <h2>Welcome back</h2>

            <p className="muted">
              Sign in to access the surveillance command center.
            </p>

            <label>
              Email address
              <input
                type="email"
                value={loginEmail}
                onChange={(event) =>
                  setLoginEmail(event.target.value)
                }
                placeholder="operator@cypher.ai"
                required
              />
            </label>

            <label>
              Password
              <input
                type="password"
                value={loginPassword}
                onChange={(event) =>
                  setLoginPassword(event.target.value)
                }
                placeholder="Enter password"
                required
              />
            </label>

            {loginError && (
              <div className="login-error">{loginError}</div>
            )}

            <button
              className="primary-button login-button"
              disabled={loginLoading}
              type="submit"
            >
              {loginLoading ? 'Signing in…' : 'Sign in to CYPHER'}
            </button>

            <small className="login-footnote">
              Protected by JWT authentication and encrypted cloud
              services.
            </small>
          </form>
        </div>
      </div>
    )
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="logo-mark">C</div>

          <div>
            <strong>CYPHER</strong>
            <span>Operations</span>
          </div>
        </div>

        <div className="nav-label">WORKSPACE</div>

        <nav>
          <button
            className={page === 'dashboard' ? 'nav active' : 'nav'}
            onClick={() => setPage('dashboard')}
          >
            <span className="nav-icon">⌂</span>
            Live Cockpit
          </button>

          <button
            className={
              page === 'incidents' || page === 'details'
                ? 'nav active'
                : 'nav'
            }
            onClick={() => setPage('incidents')}
          >
            <span className="nav-icon">!</span>
            Incidents

            {stats.open > 0 && (
              <b className="nav-count">{stats.open}</b>
            )}
          </button>

          <button
            className={page === 'cameras' ? 'nav active' : 'nav'}
            onClick={() => setPage('cameras')}
          >
            <span className="nav-icon">◉</span>
            Camera Network
          </button>

          <button
            className={page === 'analytics' ? 'nav active' : 'nav'}
            onClick={() => setPage('analytics')}
          >
            <span className="nav-icon">⌁</span>
            Analytics
          </button>
        </nav>

        <div className="sidebar-system">
          <div className="nav-label">SYSTEM</div>

          <div className="system-line">
            <span
              className={`status-dot ${online ? 'online' : ''}`}
            />
            Gateway
            <strong>{online ? 'LIVE' : 'CONNECTING'}</strong>
          </div>

          <div className="system-line">
            <span
              className={`status-dot ${
                activeCameras > 0 ? 'online' : ''
              }`}
            />
            Cameras
            <strong>
              {activeCameras}/{cameras.length}
            </strong>
          </div>

          <div className="system-line">
            <span className="status-dot online" />
            AWS RDS
            <strong>CONNECTED</strong>
          </div>
        </div>

        <div className="sidebar-user">
          <div className="avatar">
            {(user.displayName || 'OP')
              .slice(0, 2)
              .toUpperCase()}
          </div>

          <div className="user-copy">
            <strong>{user.displayName || 'Operator'}</strong>
            <span>{user.role || 'OPERATOR'}</span>
          </div>

          <button
            className="logout-button"
            onClick={logout}
            title="Sign out"
          >
            ↗
          </button>
        </div>
      </aside>

      <main className="main">
        <header className="topbar">
          <div>
            <span className="breadcrumb">
              CYPHER / {pageTitle.toUpperCase()}
            </span>

            <h1>{pageTitle}</h1>

            <p>
              {page === 'dashboard' &&
                'Real-time operational awareness across your surveillance network.'}

              {page === 'incidents' &&
                'Review, prioritize and manage detected security events.'}

              {page === 'details' &&
                'Investigate evidence, source and response state.'}

              {page === 'cameras' &&
                'Monitor connected surveillance and edge devices.'}

              {page === 'analytics' &&
                'Understand incident trends and operational performance.'}
            </p>
          </div>

          <div className="topbar-actions">
            <div className={`live-badge ${online ? 'online' : ''}`}>
              <span />
              {online ? 'LIVE' : 'RECONNECTING'}
            </div>

            <button
              className="secondary-button"
              onClick={refreshDashboard}
              disabled={loading}
            >
              {loading ? 'Refreshing…' : 'Refresh'}
            </button>
          </div>
        </header>

        {page === 'dashboard' && (
          <>
            <section className="metric-grid">
              <Metric
                label="OPEN INCIDENTS"
                value={stats.open}
                subtitle="Require attention"
                tone="danger"
              />

              <Metric
                label="CRITICAL"
                value={stats.critical}
                subtitle="Immediate response"
                tone="danger"
              />

              <Metric
                label="ACTIVE CAMERAS"
                value={`${activeCameras}/${cameras.length}`}
                subtitle="Surveillance nodes"
                tone="success"
              />

              <Metric
                label="RESOLVED"
                value={stats.resolved}
                subtitle="Successfully handled"
                tone="success"
              />
            </section>

            <section className="cockpit-grid">
              <div className="panel live-panel">
                <div className="panel-heading">
                  <div>
                    <span className="kicker">
                      LIVE SURVEILLANCE
                    </span>
                    <h2>Operational Feed</h2>
                  </div>

                  <div className="camera-live">
                    <span />
                    {activeCameras > 0 ? 'CAMERA ONLINE' : 'NO FEED'}
                  </div>
                </div>

                <div className="video-placeholder">
                  <div className="scan-line" />

                  <div className="camera-overlay top-left">
                    <strong>
                      {cameras[0]?.name || 'CAM-01'}
                    </strong>
                    <span>
                      {cameras[0]?.location ||
                        'Awaiting camera telemetry'}
                    </span>
                  </div>

                  <div className="camera-center">
                    <div className="target-ring">
                      <div />
                    </div>

                    <strong>
                      {latestIncident
                        ? latestIncident.incidentType
                        : 'MONITORING'}
                    </strong>

                    <span>
                      {latestIncident
                        ? `${percent(
                            latestIncident.confidence
                          )} confidence`
                        : 'No active detection'}
                    </span>
                  </div>

                  <div className="camera-overlay bottom-left">
                    <span>
                      EDGE NODE
                    </span>
                    <strong>
                      {cameras[0]?.active
                        ? 'ONLINE'
                        : 'AWAITING DEVICE'}
                    </strong>
                  </div>

                  <div className="camera-overlay bottom-right">
                    <span>PIR</span>
                    <strong>TELEMETRY PENDING</strong>
                  </div>
                </div>

                <div className="demo-bar">
                  <div>
                    <strong>AI Simulation</strong>
                    <span>
                      Test the current end-to-end incident pipeline.
                    </span>
                  </div>

                  <button
                    className="secondary-button"
                    disabled={actionLoading}
                    onClick={() => triggerDemo('health')}
                  >
                    Health Demo
                  </button>

                  <button
                    className="primary-button"
                    disabled={actionLoading}
                    onClick={() => triggerDemo('threat')}
                  >
                    Threat Demo
                  </button>
                </div>
              </div>

              <div className="panel">
                <div className="panel-heading">
                  <div>
                    <span className="kicker">
                      PRIORITY QUEUE
                    </span>
                    <h2>Recent Incidents</h2>
                  </div>

                  <button
                    className="text-button"
                    onClick={() => setPage('incidents')}
                  >
                    View all
                  </button>
                </div>

                <IncidentList
                  incidents={incidents.slice(0, 6)}
                  onSelect={openIncident}
                />
              </div>
            </section>

            <section className="bottom-grid">
              <div className="panel compact-panel">
                <div className="panel-heading">
                  <div>
                    <span className="kicker">NETWORK</span>
                    <h2>Camera Status</h2>
                  </div>

                  <button
                    className="text-button"
                    onClick={() => setPage('cameras')}
                  >
                    Manage
                  </button>
                </div>

                <div className="camera-mini-grid">
                  {cameras.length === 0 ? (
                    <EmptyState message="No cameras registered." />
                  ) : (
                    cameras.slice(0, 4).map((camera) => (
                      <div className="camera-mini" key={camera.id}>
                        <span
                          className={`status-dot ${
                            camera.active ? 'online' : ''
                          }`}
                        />

                        <div>
                          <strong>{camera.name}</strong>
                          <span>{camera.location}</span>
                        </div>

                        <b>
                          {camera.active ? 'ONLINE' : 'OFFLINE'}
                        </b>
                      </div>
                    ))
                  )}
                </div>
              </div>

              <div className="panel compact-panel">
                <div className="panel-heading">
                  <div>
                    <span className="kicker">SYSTEM HEALTH</span>
                    <h2>Infrastructure</h2>
                  </div>
                </div>

                <div className="health-list">
                  <HealthRow
                    label="Spring Boot Gateway"
                    value={online ? 'Operational' : 'Connecting'}
                    healthy={online}
                  />

                  <HealthRow
                    label="AWS RDS MySQL"
                    value="Connected"
                    healthy
                  />

                  <HealthRow
                    label="AI Engine"
                    value="Available"
                    healthy
                  />

                  <HealthRow
                    label="Hardware Telemetry"
                    value="Pending integration"
                    healthy={false}
                  />
                </div>
              </div>
            </section>
          </>
        )}

        {page === 'incidents' && (
          <section className="panel page-panel">
            <div className="panel-heading incident-heading">
              <div>
                <span className="kicker">
                  INCIDENT MANAGEMENT
                </span>

                <h2>All Incidents</h2>

                <p>
                  {incidents.length} events currently stored in
                  CYPHER.
                </p>
              </div>

              <div className="filter-tabs">
                {(
                  [
                    'ALL',
                    'CRITICAL',
                    'HIGH',
                    'OPEN',
                  ] as Filter[]
                ).map((item) => (
                  <button
                    key={item}
                    className={filter === item ? 'active' : ''}
                    onClick={() => setFilter(item)}
                  >
                    {item}
                  </button>
                ))}
              </div>
            </div>

            <IncidentList
              incidents={filteredIncidents}
              onSelect={openIncident}
              detailed
            />
          </section>
        )}

        {page === 'details' && (
          <>
            {!selectedIncident ? (
              <div className="panel page-panel">
                <EmptyState message="Select an incident to inspect." />
              </div>
            ) : (
              <IncidentDetails
                incident={selectedIncident}
                actionLoading={actionLoading}
                onBack={() => setPage('incidents')}
                onStatusChange={changeIncidentStatus}
              />
            )}
          </>
        )}

        {page === 'cameras' && (
          <section className="page-stack">
            <div className="camera-summary">
              <div>
                <span className="kicker">SURVEILLANCE NETWORK</span>
                <h2>{cameras.length} Registered Cameras</h2>
              </div>

              <div className="camera-summary-values">
                <span>
                  <b>{activeCameras}</b>
                  Online
                </span>

                <span>
                  <b>{cameras.length - activeCameras}</b>
                  Offline
                </span>
              </div>
            </div>

            <div className="camera-card-grid">
              {cameras.length === 0 ? (
                <div className="panel">
                  <EmptyState message="No cameras registered." />
                </div>
              ) : (
                cameras.map((camera) => (
                  <div className="camera-card" key={camera.id}>
                    <div className="camera-preview">
                      <div className="camera-preview-icon">◎</div>

                      <div
                        className={`camera-status ${
                          camera.active ? 'online' : ''
                        }`}
                      >
                        <span />
                        {camera.active ? 'ONLINE' : 'OFFLINE'}
                      </div>
                    </div>

                    <div className="camera-card-body">
                      <span className="kicker">
                        CAMERA #{camera.id}
                      </span>

                      <h3>{camera.name}</h3>
                      <p>{camera.location}</p>

                      <div className="device-grid">
                        <div>
                          <span>Project</span>
                          <strong>
                            {camera.projectName || 'CYPHER'}
                          </strong>
                        </div>

                        <div>
                          <span>Node State</span>
                          <strong>
                            {camera.active ? 'Active' : 'Inactive'}
                          </strong>
                        </div>

                        <div>
                          <span>PIR</span>
                          <strong>Pending</strong>
                        </div>

                        <div>
                          <span>Wi-Fi RSSI</span>
                          <strong>Pending</strong>
                        </div>
                      </div>
                    </div>
                  </div>
                ))
              )}
            </div>
          </section>
        )}

        {page === 'analytics' && (
          <Analytics
            incidents={incidents}
            cameras={cameras}
            stats={stats}
          />
        )}
      </main>

      {toast && (
        <div className="toast">
          <div className="toast-mark">C</div>

          <div>
            <strong>CYPHER</strong>
            <span>{toast}</span>
          </div>
        </div>
      )}
    </div>
  )
}

function Metric({
  label,
  value,
  subtitle,
  tone,
}: {
  label: string
  value: string | number
  subtitle: string
  tone?: 'danger' | 'success'
}) {
  return (
    <div className={`metric-card ${tone || ''}`}>
      <span className="kicker">{label}</span>
      <strong>{value}</strong>
      <p>{subtitle}</p>
    </div>
  )
}

function IncidentList({
  incidents,
  onSelect,
  detailed = false,
}: {
  incidents: Incident[]
  onSelect: (incident: Incident) => void
  detailed?: boolean
}) {
  if (incidents.length === 0) {
    return <EmptyState message="No incidents match this view." />
  }

  return (
    <div className="incident-list">
      {incidents.map((incident) => (
        <button
          className="incident-row"
          key={incident.id}
          onClick={() => onSelect(incident)}
        >
          <div
            className={`incident-priority ${incident.priority.toLowerCase()}`}
          />

          <div className="incident-copy">
            <div className="incident-tags">
              <span
                className={`priority-chip ${incident.priority.toLowerCase()}`}
              >
                {incident.priority}
              </span>

              <span>{incident.incidentType}</span>

              {detailed && (
                <span className="status-chip">
                  {incident.status}
                </span>
              )}
            </div>

            <strong>
              {incident.summary || `${incident.incidentType} incident`}
            </strong>

            <p>
              {incident.cameraName} · {incident.location}
            </p>
          </div>

          <div className="incident-meta">
            <strong>
              {incident.confidence !== undefined
                ? percent(incident.confidence)
                : '—'}
            </strong>

            <span>{formatDate(incident.createdAt)}</span>
          </div>

          <div className="row-arrow">›</div>
        </button>
      ))}
    </div>
  )
}

function IncidentDetails({
  incident,
  actionLoading,
  onBack,
  onStatusChange,
}: {
  incident: Incident
  actionLoading: boolean
  onBack: () => void
  onStatusChange: (
    incident: Incident,
    status: Incident['status']
  ) => void
}) {
  return (
    <section className="detail-page">
      <button className="back-button" onClick={onBack}>
        ← Back to incidents
      </button>

      <div className="detail-title-row">
        <div>
          <div className="incident-tags">
            <span
              className={`priority-chip ${incident.priority.toLowerCase()}`}
            >
              {incident.priority}
            </span>

            <span className="status-chip">
              {incident.status}
            </span>
          </div>

          <h2>
            {incident.incidentType} Incident #{incident.id}
          </h2>

          <p>{incident.summary}</p>
        </div>

        <div className="detail-actions">
          {incident.status === 'OPEN' && (
            <button
              className="secondary-button"
              disabled={actionLoading}
              onClick={() =>
                onStatusChange(incident, 'ACKNOWLEDGED')
              }
            >
              Acknowledge
            </button>
          )}

          {incident.status !== 'RESOLVED' && (
            <button
              className="primary-button"
              disabled={actionLoading}
              onClick={() =>
                onStatusChange(incident, 'RESOLVED')
              }
            >
              Resolve Incident
            </button>
          )}
        </div>
      </div>

      <div className="detail-grid">
        <div className="panel detail-evidence">
          <div className="panel-heading">
            <div>
              <span className="kicker">VISUAL EVIDENCE</span>
              <h2>Detection Snapshot</h2>
            </div>
          </div>

          <div className="evidence-placeholder">
            <div className="target-ring">
              <div />
            </div>

            <strong>{incident.assetType || 'DETECTED OBJECT'}</strong>

            <span>
              {percent(incident.confidence)} AI confidence
            </span>
          </div>
        </div>

        <div className="panel detail-information">
          <div className="panel-heading">
            <div>
              <span className="kicker">INCIDENT DATA</span>
              <h2>Event Information</h2>
            </div>
          </div>

          <div className="detail-data">
            <DataRow label="Incident ID" value={`#${incident.id}`} />
            <DataRow label="Type" value={incident.incidentType} />
            <DataRow label="Priority" value={incident.priority} />
            <DataRow label="Status" value={incident.status} />
            <DataRow label="Camera" value={incident.cameraName} />
            <DataRow label="Location" value={incident.location} />
            <DataRow label="Camera ID" value={`${incident.cameraId}`} />
            <DataRow label="Asset" value={incident.assetType || '—'} />
            <DataRow
              label="Confidence"
              value={percent(incident.confidence)}
            />
            <DataRow
              label="Detected"
              value={formatDate(incident.createdAt)}
            />
            <DataRow
              label="Resolved"
              value={formatDate(incident.resolvedAt)}
            />
          </div>
        </div>

        <div className="panel telemetry-panel">
          <div className="panel-heading">
            <div>
              <span className="kicker">EDGE TELEMETRY</span>
              <h2>Hardware State</h2>
            </div>
          </div>

          <div className="telemetry-grid">
            <Telemetry label="Source Device" value={incident.cameraName} />
            <Telemetry label="PIR State" value="Integration pending" />
            <Telemetry label="ESP32-CAM" value="Integration pending" />
            <Telemetry label="Local Alert" value="Integration pending" />
            <Telemetry label="Wi-Fi Signal" value="Integration pending" />
            <Telemetry label="Cloud State" value="Stored in RDS" />
          </div>
        </div>

        <div className="panel metadata-panel">
          <div className="panel-heading">
            <div>
              <span className="kicker">METADATA</span>
              <h2>Detection Payload</h2>
            </div>
          </div>

          <pre>
            {JSON.stringify(
              {
                metadata: incident.metadata,
                boundingBox: incident.boundingBox,
              },
              null,
              2
            )}
          </pre>
        </div>
      </div>
    </section>
  )
}

function Analytics({
  incidents,
  cameras,
  stats,
}: {
  incidents: Incident[]
  cameras: Camera[]
  stats: Stats
}) {
  const total = incidents.length

  const resolvedRate =
    total === 0 ? 0 : Math.round((stats.resolved / total) * 100)

  const criticalRate =
    total === 0 ? 0 : Math.round((stats.critical / total) * 100)

  const highRate =
    total === 0 ? 0 : Math.round((stats.high / total) * 100)

  const typeCounts = {
    THREAT: incidents.filter((i) => i.incidentType === 'THREAT').length,
    HEALTH: incidents.filter((i) => i.incidentType === 'HEALTH').length,
    ACCESS: incidents.filter((i) => i.incidentType === 'ACCESS').length,
    OTHER: incidents.filter((i) => i.incidentType === 'OTHER').length,
  }

  const maxType = Math.max(...Object.values(typeCounts), 1)

  return (
    <section className="page-stack">
      <section className="metric-grid">
        <Metric
          label="TOTAL INCIDENTS"
          value={total}
          subtitle="Recorded events"
        />

        <Metric
          label="RESOLUTION RATE"
          value={`${resolvedRate}%`}
          subtitle="Resolved events"
          tone="success"
        />

        <Metric
          label="CRITICAL RATE"
          value={`${criticalRate}%`}
          subtitle="Critical events"
          tone="danger"
        />

        <Metric
          label="CAMERA UPTIME"
          value={
            cameras.length
              ? `${Math.round(
                  (cameras.filter((c) => c.active).length /
                    cameras.length) *
                    100
                )}%`
              : '0%'
          }
          subtitle="Currently online"
          tone="success"
        />
      </section>

      <div className="analytics-grid">
        <div className="panel chart-panel">
          <div className="panel-heading">
            <div>
              <span className="kicker">EVENT DISTRIBUTION</span>
              <h2>Incidents by Type</h2>
            </div>
          </div>

          <div className="bar-chart">
            {Object.entries(typeCounts).map(([label, value]) => (
              <div className="bar-row" key={label}>
                <span>{label}</span>

                <div className="bar-track">
                  <div
                    className="bar-value"
                    style={{
                      width: `${(value / maxType) * 100}%`,
                    }}
                  />
                </div>

                <strong>{value}</strong>
              </div>
            ))}
          </div>
        </div>

        <div className="panel chart-panel">
          <div className="panel-heading">
            <div>
              <span className="kicker">PRIORITY PROFILE</span>
              <h2>Operational Risk</h2>
            </div>
          </div>

          <div className="risk-display">
            <div>
              <strong>{criticalRate}%</strong>
              <span>Critical</span>
            </div>

            <div>
              <strong>{highRate}%</strong>
              <span>High</span>
            </div>

            <div>
              <strong>{stats.open}</strong>
              <span>Open</span>
            </div>

            <div>
              <strong>{stats.acknowledged}</strong>
              <span>Acknowledged</span>
            </div>
          </div>
        </div>
      </div>

      <div className="panel report-panel">
        <div className="panel-heading">
          <div>
            <span className="kicker">REPORTING</span>
            <h2>Operational Summary</h2>
          </div>

          <span className="coming-soon">
            EXPORT SERVICE NOT CONNECTED
          </span>
        </div>

        <div className="report-summary">
          <DataRow
            label="Incidents stored"
            value={`${total}`}
          />

          <DataRow
            label="Resolved"
            value={`${stats.resolved}`}
          />

          <DataRow
            label="Active surveillance nodes"
            value={`${cameras.filter((c) => c.active).length}`}
          />

          <DataRow
            label="Hardware telemetry"
            value="Pending ESP32 integration"
          />
        </div>
      </div>
    </section>
  )
}

function HealthRow({
  label,
  value,
  healthy,
}: {
  label: string
  value: string
  healthy: boolean
}) {
  return (
    <div className="health-row">
      <span className={`status-dot ${healthy ? 'online' : ''}`} />

      <strong>{label}</strong>

      <span>{value}</span>
    </div>
  )
}

function DataRow({
  label,
  value,
}: {
  label: string
  value: string
}) {
  return (
    <div className="data-row">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function Telemetry({
  label,
  value,
}: {
  label: string
  value: string
}) {
  return (
    <div className="telemetry-item">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function EmptyState({ message }: { message: string }) {
  return (
    <div className="empty-state">
      <div>◎</div>
      <strong>No data</strong>
      <span>{message}</span>
    </div>
  )
}
