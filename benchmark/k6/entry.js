import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';
import { Trend, Counter } from 'k6/metrics';

const auth = new SharedArray('fixture credentials', () => JSON.parse(open(__ENV.AUTH_FILE)).users);
const acceptedLatency = new Trend('accepted_entry_latency', true);
const accepted = new Counter('business_accepted');
const rejected = new Counter('business_rejected');
const unexpected = new Counter('unexpected_responses');
http.setResponseCallback(http.expectedStatuses(200, 202, 409, 429));
export const options = {
  scenarios: { burst: { executor: 'shared-iterations', vus: Number(__ENV.VUS || 40), iterations: Number(__ENV.REQUESTS || 3000), maxDuration: '120s' } },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(50)', 'p(95)', 'p(99)'],
  thresholds: { unexpected_responses: ['count==0'], http_req_failed: ['rate==0'] },
};
export default function () {
  const user = auth[exec.scenario.iterationInTest % auth.length];
  const path = __ENV.MODE === 'baseline' ? `/api/v1/seckill/baseline/${__ENV.SKU_ID}` : `/api/v1/seckill/${__ENV.SKU_ID}`;
  const t0 = Date.now();
  const r = http.post(`${__ENV.BASE_URL || 'http://localhost:8080'}${path}`, null, { headers: { Authorization: `Bearer ${user.token}` } });
  if (r.status === 200 || r.status === 202) {
    const body = r.json();
    if (body.reservationId && (body.status === 'QUEUED' || body.status === 'WAIT_PAY')) {
      accepted.add(1); acceptedLatency.add(r.timings.duration);
      console.log(JSON.stringify({ rid: body.reservationId, uid: user.id, start_ms: t0, entry_ms: r.timings.duration, status: r.status }));
    } else { unexpected.add(1); }
  } else if (r.status === 409 || r.status === 429) { rejected.add(1); }
  else { unexpected.add(1); }
  unexpected.add(0);
  check(r, { 'recognized business response': () => [200, 202, 409, 429].includes(r.status) });
}
export function handleSummary(data) { return { [__ENV.SUMMARY_FILE || 'k6-summary.json']: JSON.stringify(data, null, 2) }; }
