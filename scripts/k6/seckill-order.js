import http from 'k6/http'
import { check, sleep } from 'k6'

export const options = {
  scenarios: {
    seckill_order: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 80),
      timeUnit: '1s',
      duration: __ENV.DURATION || '1m',
      preAllocatedVUs: 100,
      maxVUs: 500,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.35'],
    http_req_duration: ['p(95)<1200'],
  },
}

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8080'
const token = __ENV.AUTH_TOKEN || ''
const activityId = Number(__ENV.ACTIVITY_ID || 1)

export default function () {
  const res = http.post(
    `${baseUrl}/api/trade/orders/seckill/async`,
    JSON.stringify({ activityId, quantity: 1 }),
    { headers: { 'Content-Type': 'application/json', authorization: token } },
  )
  check(res, {
    'seckill accepted or limited': (r) => [200, 429].includes(r.status),
  })
  sleep(0.1)
}
