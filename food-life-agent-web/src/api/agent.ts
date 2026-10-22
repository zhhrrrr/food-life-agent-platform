import { http } from './http'

export interface TradeWorkflow {
  thread_id: string
  session_id: string
  request_id?: string
  status: string
  action: string
  trade_type?: 'NORMAL' | 'GROUP_BUY' | 'SECKILL' | null
  response: string
  interrupt?: {
    type: 'parameters' | 'confirmation' | 'seckill_wait'
    message: string
    missing_fields?: string[]
    confirmation_id?: string
    expires_at?: number
    preview?: Record<string, unknown>
  } | null
  result?: { status?: string; data?: Record<string, unknown> }
}

export interface AgentRequest {
  message: string
  session_id: string
  request_id: string
  trade_thread_id?: string
  run_id?: string
  task_id?: string
  confirmation_id?: string
  approved?: boolean
  recover?: boolean
}

export interface AgentInterrupt {
  type: 'selection' | 'trade' | 'paused' | 'reference'
  task_id?: string
  message: string
  choices?: Array<{ message: string; label: string; value?: number }>
  trade?: TradeWorkflow
}

export interface PurchaseOption {
  trade_type: string
  price_cents: number
  message: string
  conditions: string
  matches_preferences?: boolean
}

export interface AgentResponse {
  session_id: string
  run_id: string
  execution_status: string
  response: string
  trade?: TradeWorkflow | null
  interrupt?: AgentInterrupt | null
  purchase_options?: PurchaseOption[]
}

export interface AgentRunAccepted {
  session_id: string
  run_id: string
  request_id: string
  execution_status: 'running'
}

export interface AgentRunEvent {
  id: number
  event: string
  data: Record<string, unknown>
}

export async function startAgentRun(body: AgentRequest) {
  return (await http.post<AgentRunAccepted>('/api/agent/runs', body)).data
}

export async function getAgentRun(runId: string) {
  return (await http.get<AgentResponse>(`/api/agent/runs/${runId}`)).data
}

export async function resumeAgentRun(runId: string, body: AgentRequest, recover = false) {
  const { run_id: _runId, trade_thread_id: _threadId, ...payload } = body
  return (await http.post<AgentRunAccepted>(`/api/agent/runs/${runId}/resume`, { ...payload, recover })).data
}

export async function pauseAgentRun(runId: string) {
  return (await http.post<AgentResponse>(`/api/agent/runs/${runId}/pause`)).data
}

export async function streamAgentRun(
  runId: string,
  requestId: string,
  onEvent: (event: AgentRunEvent) => void,
  signal?: AbortSignal,
) {
  const token = localStorage.getItem('food-life-token') || ''
  const response = await fetch(`/api/agent/runs/${runId}/events?request_id=${encodeURIComponent(requestId)}`, {
    headers: token ? { authorization: token } : {},
    signal,
  })
  if (!response.ok || !response.body) {
    const body = await response.text()
    throw new Error(body || `SSE request failed: ${response.status}`)
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    while (true) {
      const { value, done } = await reader.read()
      // A CRLF pair can itself be split between two network chunks.
      buffer = (buffer + decoder.decode(value, { stream: !done })).replaceAll('\r\n', '\n')
      let boundary = buffer.indexOf('\n\n')
      while (boundary >= 0) {
        const block = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + 2)
        const id = Number(block.match(/^id: *(\d+)$/m)?.[1] || 0)
        const event = block.match(/^event: *([^\n]+)$/m)?.[1]
        const lines = [...block.matchAll(/^data: ?([^\n]*)$/gm)]
        if (event && lines.length) {
          const data = JSON.parse(lines.map(line => line[1]).join('\n')) as Record<string, unknown>
          onEvent({ id, event, data })
          if (event === 'turn_finished' || event === 'error') return
        }
        boundary = buffer.indexOf('\n\n')
      }
      if (done) return
    }
  } finally {
    try { await reader.cancel() } catch { /* An aborted transport is already closed. */ }
    reader.releaseLock()
  }
}

export async function getTradeWorkflow(threadId: string) {
  return (await http.get<TradeWorkflow>(`/api/agent/trade/threads/${threadId}`)).data
}

export async function recoverTradeWorkflow(trade: TradeWorkflow, requestId: string) {
  return (await http.post<TradeWorkflow>(`/api/agent/trade/threads/${trade.thread_id}/recover`, {
    session_id: trade.session_id, request_id: requestId,
  }, { timeout: 90000 })).data
}
