#!/bin/bash

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULT_DIR="results/${TIMESTAMP}"

mkdir -p ${RESULT_DIR}

echo "Collecting metrics from Prometheus..."

# Example: Query Prometheus for cache hit rate
# Note: This is a simplified example. Real collection would involve more complex queries
# and potentially using Grafana's API for dashboard snapshots.

# For now, just copy k6 results
cp k6/load-test-cache.js ${RESULT_DIR}/
cp k6/load-test-penetration.js ${RESULT_DIR}/
cp results/cache-off.json ${RESULT_DIR}/
cp results/cache-on.json ${RESULT_DIR}/
cp results/penetration-test.json ${RESULT_DIR}/

echo "Results saved to ${RESULT_DIR}"
