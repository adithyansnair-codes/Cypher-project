export type Incident = {
  id: number
  incidentType: 'THREAT' | 'HEALTH' | 'ACCESS' | 'OTHER'
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  status: 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED'
  assetType: string
  confidence: number
  summary: string
  cameraId: number
  cameraName: string
  location: string
  boundingBox: Record<string, unknown>
  metadata: Record<string, unknown>
  createdAt: string
  resolvedAt?: string
}

export type Stats = {
  open: number
  acknowledged: number
  resolved: number
  critical: number
  high: number
}

export type Camera = {
  id: number
  name: string
  location: string
  active: boolean
  projectName: string
}