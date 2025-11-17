import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';

// Test data (product IDs, user IDs)
const productIds = new SharedArray('productIds', function () {
    const data = [];
    for (let i = 1; i <= 100000; i++) {
        data.push(i);
    }
    return data;
});

const userIds = new SharedArray('userIds', function () {
    const data = [];
    for (let i = 1; i <= 10000; i++) {
        data.push(i);
    }
    return data;
});

export const options = {
    stages: [
        { duration: '20s', target: 30 }, // Stage 1: Warm-up (0 -> 30 VUs)
        { duration: '60s', target: 30 }, // Stage 2: Steady load (30 VUs)
        { duration: '30s', target: 50 }, // Stage 3: Peak load (30 -> 50 VUs)
        { duration: '10s', target: 0 },  // Stage 4: Cool-down (50 -> 0 VUs)
    ],
    thresholds: {
        http_req_duration: ['p(95)<500', 'p(99)<1000'], // 95% of requests should be below 500ms, 99% below 1000ms
        http_req_failed: ['rate<0.01'], // http errors should be less than 1%
    },
};

export default function () {
    const baseUrl = 'http://localhost:8080/api';

    // API call distribution
    const rnd = Math.random();
    if (rnd < 0.50) { // 50% GET /api/products/{id}
        const productId = productIds[Math.floor(Math.random() * productIds.length)];
        http.get(`${baseUrl}/products/${productId}`);
    } else if (rnd < 0.80) { // 30% GET /api/products
        const categories = ['ELECTRONICS', 'FASHION', 'FOOD', 'BOOK'];
        const category = categories[Math.floor(Math.random() * categories.length)];
        http.get(`${baseUrl}/products?page=${Math.floor(Math.random() * 50)}&size=20&category=${category}`);
    } else if (rnd < 0.95) { // 15% GET /api/orders/user/{userId}
        const userId = userIds[Math.floor(Math.random() * userIds.length)];
        http.get(`${baseUrl}/orders/user/${userId}?page=${Math.floor(Math.random() * 10)}&size=20`);
    } else { // 5% POST /api/orders
        const userId = userIds[Math.floor(Math.random() * userIds.length)];
        const productId = productIds[Math.floor(Math.random() * productIds.length)];
        const quantity = Math.floor(Math.random() * 5) + 1; // 1 to 5
        const payload = JSON.stringify({ userId, productId, quantity });
        const params = {
            headers: {
                'Content-Type': 'application/json',
            },
        };
        http.post(`${baseUrl}/orders`, payload, params);
    }

    sleep(0.1); // Simulate user think time
}
