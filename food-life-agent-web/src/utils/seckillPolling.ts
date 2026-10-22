export type PollingPhase = 'idle' | 'polling' | 'paused' | 'timed_out'

export interface SeckillPollingSnapshot {
  key: string | null
  waiting: boolean
  ready: boolean
}

interface PollingOptions {
  snapshot: () => SeckillPollingSnapshot
  refresh: () => Promise<void>
  onPhase: (phase: PollingPhase) => void
  now?: () => number
  setTimer?: (callback: () => void, delay: number) => unknown
  clearTimer?: (handle: unknown) => void
}

// Poll only the submitted seckill request. Never use this for uncertain writes.
export function createSeckillPoller(options: PollingOptions) {
  const now = options.now || Date.now
  const setTimer = options.setTimer || ((callback, delay) => setTimeout(callback, delay))
  const clearTimer = options.clearTimer || ((handle) => clearTimeout(handle as ReturnType<typeof setTimeout>))
  let key: string | null = null
  let deadline = 0
  let timer: unknown
  let expiryTimer: unknown
  let inFlight = false
  let disposed = false
  let failures = 0
  let revision = 0

  function clearNext() {
    if (timer !== undefined) clearTimer(timer)
    timer = undefined
  }

  function clearExpiry() {
    if (expiryTimer !== undefined) clearTimer(expiryTimer)
    expiryTimer = undefined
  }

  function sync() {
    if (disposed) return
    const state = options.snapshot()
    const nextKey = state.waiting ? state.key : null
    if (nextKey !== key) {
      clearNext()
      clearExpiry()
      revision++
      key = nextKey
      failures = 0
      deadline = now() + 30_000
      if (key) expiryTimer = setTimer(() => {
        expiryTimer = undefined
        sync()
      }, 30_000)
    }
    if (!key) {
      options.onPhase('idle')
      return
    }
    if (now() >= deadline) {
      clearNext()
      options.onPhase('timed_out')
      return
    }
    if (inFlight) return
    if (!state.ready) {
      clearNext()
      options.onPhase('paused')
      return
    }
    options.onPhase('polling')
    if (timer === undefined) {
      timer = setTimer(() => { void poll() }, Math.min(2_000 * 2 ** failures, 8_000, deadline - now()))
    }
  }

  async function poll() {
    timer = undefined
    const state = options.snapshot()
    if (disposed || inFlight || !state.waiting || state.key !== key || !state.ready || now() >= deadline) {
      sync()
      return
    }
    const activeRevision = revision
    inFlight = true
    try {
      await options.refresh()
      if (revision === activeRevision) failures = 0
    } catch {
      if (revision === activeRevision) failures++
    } finally {
      inFlight = false
      sync()
    }
  }

  function dispose() {
    disposed = true
    revision++
    clearNext()
    clearExpiry()
  }

  return { sync, dispose }
}
