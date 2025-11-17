#!/bin/bash

echo "=== Spring Local Cache Test (2-minute version) ==="

# 1. Build Spring Boot App
echo "[1/9] Building Spring Boot application..."
./gradlew clean build -x test

# 2. Start infrastructure
echo "[2/9] Starting infrastructure..."
docker-compose -f docker/docker-compose.yml up -d mysql mysql-exporter prometheus grafana
sleep 20

# 3. Wait for MySQL to be ready and data loaded
echo "[3/9] Waiting for MySQL (data loading: ~2 minutes)..."
# Health check for MySQL
docker-compose -f docker/docker-compose.yml ps | grep mysql | grep "(healthy)" > /dev/null
while [ $? -ne 0 ]; do
  echo "Waiting for MySQL to be healthy..."
  sleep 10
  docker-compose -f docker/docker-compose.yml ps | grep mysql | grep "(healthy)" > /dev/null
done
echo "MySQL is healthy."

# 4. Cache OFF Test
echo "[4/9] Starting app with cache OFF..."
export CACHE_ENABLED=false
docker-compose -f docker/docker-compose.yml up -d app
sleep 10 # Give app time to start

echo "[4/9] Running load test with cache OFF (2 minutes)..."
k6 run --out json=results/cache-off.json k6/load-test-cache.js

# 5. Stabilization period
echo "[5/9] Stabilization period (30 seconds)..."
sleep 30

# 6. Cache ON Test
echo "[6/9] Restarting app with cache ON..."
docker-compose -f docker/docker-compose.yml stop app
export CACHE_ENABLED=true
docker-compose -f docker/docker-compose.yml up -d app
sleep 10 # Give app time to start

echo "[6/9] Running load test with cache ON (2 minutes)..."
k6 run --out json=results/cache-on.json k6/load-test-cache.js

# 7. Cache Penetration Test (Null caching OFF) - This is controlled by CacheConfig now, so we'll just run the test
echo "[7/9] Cache Penetration test (2 minutes)..."
# Assuming CacheConfig is set up to handle null caching based on the application.yml
k6 run --out json=results/penetration-test.json k6/load-test-penetration.js

# 8. Collect results
echo "[8/9] Collecting results..."
./scripts/collect-metrics.sh

# 9. Generate report
echo "[9/9] Generating performance report..."
./scripts/generate-report.sh

echo "=== Test completed! ==="
echo "Total time: ~12 minutes (4 tests × 2 min + setup)"
echo "Check results/ directory and docs/PERFORMANCE_REPORT.md"

# Clean up
echo "Cleaning up Docker containers..."
docker-compose -f docker/docker-compose.yml down -v
