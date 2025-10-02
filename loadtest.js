import http from 'k6/http';
import { check } from 'k6';
export const options = {
    discardResponseBodies: true,
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)']
};
export default function () {
    const res = http.get(`http://localhost:8080/test1`);
    check(res, { 'status was 200': (r) => r.status === 200 });
};