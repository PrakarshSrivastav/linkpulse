import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const KEYS = Number(__ENV.KEYS || 1000);

export const options = {
    vus: Number(__ENV.VUS || 50),
    duration: __ENV.DURATION || '30s',
    summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
    thresholds: {
        checks: ['rate>0.99'],
    },
};

export default function () {
    const n = Math.floor(Math.random() * KEYS) + 1;
    const code = 'b' + String(n).padStart(6, '0');
    const res = http.get(`${BASE_URL}/${code}`, { redirects: 0 });
    check(res, { 'status is 302': (r) => r.status === 302 });
}