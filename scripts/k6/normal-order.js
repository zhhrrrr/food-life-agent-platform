import http from 'k6/http'
import { check, sleep } from 'k6'

const smoke = __ENV.K6_PROFILE === 'smoke'

export const options = {
  scenarios: {
    normal_order: {
      executor: 'ramping-arrival-rate',
      startRate: Number(__ENV.NORMAL_START_RATE || (smoke ? 1 : 5)),
      timeUnit: '1s',
      preAllocatedVUs: Number(__ENV.NORMAL_PRE_ALLOCATED_VUS || (smoke ? 5 : 50)),
      maxVUs: Number(__ENV.NORMAL_MAX_VUS || (smoke ? 20 : 200)),
      stages: [
        { duration: __ENV.NORMAL_WARMUP_DURATION || (smoke ? '5s' : '30s'), target: Number(__ENV.NORMAL_WARMUP_TARGET || (smoke ? 2 : 20)) },
        { duration: __ENV.NORMAL_RUN_DURATION || (smoke ? '10s' : '1m'), target: Number(__ENV.NORMAL_RUN_TARGET || (smoke ? 3 : 60)) },
        { duration: __ENV.NORMAL_RAMPDOWN_DURATION || (smoke ? '5s' : '30s'), target: 0 },
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
