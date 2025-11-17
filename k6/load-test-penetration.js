import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '30s', target: 20 }, // Stage 1: Normal 조회 (20 VUs)
        { duration: '60s', target: 50 }, // Stage 2: Cache Penetration 공격 (50 VUs)
        { duration: '30s', target: 20 }, // Stage 3: Normal 조회 복귀 (20 VUs)
    ],
    thresholds: {
        http_req_duration: ['p(95)<1000', 'p(99)<2000'], // Adjust thresholds for penetration test
        http_req_failed: ['rate<0.05'], // Allow slightly more errors during penetration
    },
};

export default function () {
    const baseUrl = 'http://localhost:8080/api';
    const currentStage = __VU.toString(); // Virtual User ID

    if (__ITER < 300) { // First 30 seconds (approx 300 iterations per VU)
        // Stage 1: Normal 조회 - existing product IDs
        const productId = Math.floor(Math.random() * 100000) + 1; // Existing product IDs
        http.get(`${baseUrl}/products/${productId}`);
    } else if (__ITER < 900) { // Next 60 seconds (approx 600 iterations per VU)
        // Stage 2: Cache Penetration 공격 - non-existent product IDs
        const nonExistentProductId = 999999999 - (Math.floor(Math.random() * 1000)); // Large, non-existent IDs
        http.get(`${baseUrl}/products/${nonExistentProductId}`);
    } else { // Last 30 seconds
        // Stage 3: Normal 조회 복귀 - existing product IDs
        const productId = Math.floor(Math.random() * 100000) + 1; // Existing product IDs
        http.get(`${baseUrl}/products/${productId}`);
    }

    sleep(0.1); // Simulate user think time
}
