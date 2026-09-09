import http from 'k6/http'
import { check, sleep } from 'k6'

export const options = {
  scenarios: {
    normal_order: {
      executor: 'ramping-arrival-rate',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 200,
      stages: [
        { duration: '30s', target: 20 },
        { duration: '1m', target: 60 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.2'],
    http_req_duration: ['p(95)<1500'],
  },
}

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8080'
const token = __ENV.AUTH_TOKEN || ''
const packageId = Number(__ENV.PACKAGE_ID || 1)

export default function () {
  const res = http.post(
    `${baseUrl}/api/trade/orders/normal`,
    JSON.stringify({ packageId, quantity: 1 }),
    { headers: { 'Content-Type': 'application/json', authorization: token } },
  )
  check(res, {
    'normal order accepted or limited': (r) => [200, 429].includes(r.status),
    'gateway returned json': (r) => String(r.headers['Content-Type'] || '').includes('application/json'),
  })
  sleep(0.2)
}
