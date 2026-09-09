import http from 'k6/http'
import { check, sleep } from 'k6'

export const options = {
  scenarios: {
    group_buy_order: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: 30 },
        { duration: '1m', target: 80 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.25'],
    http_req_duration: ['p(95)<1800'],
  },
}

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8080'
const token = __ENV.AUTH_TOKEN || ''
const packageId = Number(__ENV.PACKAGE_ID || 1)

export default function () {
  const res = http.post(
    `${baseUrl}/api/trade/orders/group-buy`,
    JSON.stringify({ packageId, quantity: 1 }),
    { headers: { 'Content-Type': 'application/json', authorization: token } },
  )
  check(res, {
    'group buy accepted or limited': (r) => [200, 429].includes(r.status),
  })
  sleep(0.3)
}
