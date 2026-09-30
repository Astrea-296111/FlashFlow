import http from 'k6/http';
import { Counter } from 'k6/metrics';
const failures = new Counter('unexpected_responses');
export const options = { vus: Number(__ENV.VUS || 30), duration: __ENV.DURATION || '5s', summaryTrendStats: ['avg','med','p(95)','p(99)'], thresholds: { unexpected_responses: ['count==0'] } };
export default function () { const r = http.get(`${__ENV.BASE_URL || 'http://localhost:8080'}/api/v1/events/${__ENV.EVENT_ID}`); failures.add(r.status === 200 ? 0 : 1); }
export function handleSummary(data) { return { [__ENV.SUMMARY_FILE]: JSON.stringify(data, null, 2) }; }
