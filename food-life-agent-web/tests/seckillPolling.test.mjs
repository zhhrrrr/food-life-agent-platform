import assert from 'node:assert/strict'
import test from 'node:test'
import { createSeckillPoller } from '../src/utils/seckillPolling.ts'

function setup(refresh) {
  let time = 0
  let sequence = 0
  let calls = 0
  let phase
  const tasks = new Map()
  const state = { key: 'user:thread', waiting: true, ready: true }
  const poller = createSeckillPoller({
    snapshot: () => ({ ...state }),
    refresh: async () => { calls++; await refresh?.(state) },
    onPhase: (value) => { phase = value },
    now: () => time,
    setTimer: (callback, delay) => {
      const id = ++sequence
      tasks.set(id, { at: time + delay, callback })
      return id
    },
    clearTimer: (id) => tasks.delete(id),
  })
  async function advance(milliseconds) {
    const end = time + milliseconds
    for (;;) {
      const next = [...tasks].filter(([, task]) => task.at <= end).sort((a, b) => a[1].at - b[1].at)[0]
      if (!next) break
      time = next[1].at
      tasks.delete(next[0])
      next[1].callback()
      // Drain the async refresh and its finally block before advancing the clock.
      for (let i = 0; i < 8; i++) await Promise.resolve()
    }
    time = end
  }
  return { state, poller, advance, tasks, calls: () => calls, phase: () => phase }
}

test('pending results poll every two seconds; a terminal result stops polling', async () => {
  let count = 0
  const f = setup((state) => { if (++count === 3) state.waiting = false })
  f.poller.sync()
  await f.advance(1_999)
  assert.equal(f.calls(), 0)
  await f.advance(4_001)
  assert.equal(f.calls(), 3)
  assert.equal(f.phase(), 'idle')
  assert.equal(f.tasks.size, 0)
  await f.advance(60_000)
  assert.equal(f.calls(), 3)
})

test('a slow request never overlaps another poll, and the window expires', async () => {
  let finish
  const f = setup(() => new Promise((resolve) => { finish = resolve }))
  f.poller.sync()
  await f.advance(2_000)
  f.poller.sync()
  await f.advance(28_000)
  assert.equal(f.calls(), 1)
  assert.equal(f.phase(), 'timed_out')
  finish()
  for (let i = 0; i < 8; i++) await Promise.resolve()
  await f.advance(60_000)
  assert.equal(f.calls(), 1)
})

test('pending requests stop after thirty seconds without becoming business failures', async () => {
  const f = setup()
  f.poller.sync()
  await f.advance(30_000)
  assert.equal(f.calls(), 14)
  assert.equal(f.phase(), 'timed_out')
  assert.equal(f.state.waiting, true)
  assert.equal(f.tasks.size, 0)
  f.poller.sync()
  await f.advance(10_000)
  assert.equal(f.calls(), 14)
})

test('transport errors back off to four then eight seconds', async () => {
  const f = setup(() => { throw new Error('offline') })
  f.poller.sync()
  await f.advance(2_000)
  assert.equal(f.calls(), 1)
  await f.advance(3_999)
  assert.equal(f.calls(), 1)
  await f.advance(1)
  assert.equal(f.calls(), 2)
  await f.advance(7_999)
  assert.equal(f.calls(), 2)
  await f.advance(1)
  assert.equal(f.calls(), 3)
})

test('hidden, paused or busy state stops polling and resumes within the original window', async () => {
  const f = setup()
  f.poller.sync()
  f.state.ready = false
  f.poller.sync()
  await f.advance(5_000)
  assert.equal(f.calls(), 0)
  assert.equal(f.phase(), 'paused')
  f.state.ready = true
  f.poller.sync()
  await f.advance(2_000)
  assert.equal(f.calls(), 1)
  f.state.ready = false
  f.poller.sync()
  await f.advance(23_000)
  f.state.ready = true
  f.poller.sync()
  assert.equal(f.phase(), 'timed_out')
  assert.equal(f.calls(), 1)
})

test('leaving awaiting_result, including uncertain, cancels further automatic calls', async () => {
  const f = setup()
  f.poller.sync()
  await f.advance(2_000)
  f.state.waiting = false
  f.poller.sync()
  await f.advance(60_000)
  assert.equal(f.calls(), 1)
  assert.equal(f.phase(), 'idle')
})

test('a new account or thread gets its own window; disposal cancels all timers', async () => {
  const f = setup()
  f.poller.sync()
  await f.advance(30_000)
  f.state.key = 'another-user:another-thread'
  f.poller.sync()
  await f.advance(2_000)
  assert.equal(f.calls(), 15)
  f.poller.dispose()
  assert.equal(f.tasks.size, 0)
  f.poller.sync()
  await f.advance(60_000)
  assert.equal(f.calls(), 15)
})

test('disposal during an in-flight request cannot restart polling', async () => {
  let finish
  const f = setup(() => new Promise((resolve) => { finish = resolve }))
  f.poller.sync()
  await f.advance(2_000)
  f.poller.dispose()
  finish()
  for (let i = 0; i < 8; i++) await Promise.resolve()
  await f.advance(60_000)
  assert.equal(f.calls(), 1)
  assert.equal(f.tasks.size, 0)
})
