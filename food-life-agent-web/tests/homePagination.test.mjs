import test from 'node:test'
import assert from 'node:assert/strict'
import { loadSource } from './sourceHarness.mjs'

const firstPage = Array.from({ length: 10 }, (_, index) => ({ id: index + 1 }))
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }

test('home appends page two for the applied search even when the search field changes', async () => {
  const calls = []
  const context = loadSource('views/HomeView.vue', {
    queryShopByName: async (name, current = 1) => { calls.push({ name, current }); return current === 1 ? firstPage : [{ id: 11 }] },
  }, ['keyword', 'loadShops', 'shops'])
  try {
    context.keyword.value = '火锅'; await context.loadShops()
    context.keyword.value = '烧烤'; await context.loadShops(true)
    assert.deepEqual(context.shops.value.map(shop => shop.id), [...firstPage.map(shop => shop.id), 11])
    assert.deepEqual(calls, [{ name: '火锅', current: 1 }, { name: '火锅', current: 2 }])
  } finally { context.dispose() }
})

test('switching home categories ignores a stale next page and resets pagination', async () => {
  const reply = deferred(), calls = []
  const context = loadSource('views/HomeView.vue', {
    queryShopsByCategory: async (id, current = 1) => {
      calls.push({ id, current })
      if (id === 1) return current === 1 ? firstPage : reply.promise
      return current === 1 ? Array.from({ length: 10 }, (_, index) => ({ id: 100 + index })) : [{ id: 110 }]
    },
  }, ['chooseCategory', 'loadShops', 'shops'])
  try {
    await context.chooseCategory(1); const pending = context.loadShops(true)
    await context.chooseCategory(2); reply.resolve([{ id: 11 }]); await pending
    assert.deepEqual(context.shops.value.map(shop => shop.id), [100, 101, 102, 103, 104, 105, 106, 107, 108, 109])
    await context.loadShops(true)
    assert.deepEqual(context.shops.value.map(shop => shop.id), [100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110])
    assert.deepEqual(calls, [{ id: 1, current: 1 }, { id: 1, current: 2 }, { id: 2, current: 1 }, { id: 2, current: 2 }])
  } finally { reply.resolve([]); context.dispose() }
})

test('a failed home next page can retry the same page without duplicating the list', async () => {
  const calls = []; let failed = false
  const context = loadSource('views/HomeView.vue', {
    queryShopsByCategory: async (_id, current = 1) => {
      calls.push(current)
      if (current === 1) return firstPage
      if (!failed) { failed = true; throw new Error('offline') }
      return [{ id: 11 }]
    },
  }, ['chooseCategory', 'loadShops', 'shops'])
  try {
    await context.chooseCategory(1); await context.loadShops(true)
    assert.equal(context.shops.value.length, 10)
    await context.loadShops(true)
    assert.deepEqual(calls, [1, 2, 2])
    assert.equal(context.shops.value.length, 11)
  } finally { context.dispose() }
})
