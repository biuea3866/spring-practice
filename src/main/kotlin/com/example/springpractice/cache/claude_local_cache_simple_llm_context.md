# Spring Local Cache 성능 측정 프로젝트 (단순화 버전)

## 개요
- **목적**: 단일 애플리케이션에서 Caffeine 로컬 캐시 적용 전/후의 성능 차이를 정량적으로 측정하고, Cache Penetration 현상 관찰
- **범위**:
    - 포함: 로컬 캐시 적용, 캐시 ON/OFF 성능 비교, Cache Penetration 시나리오 테스트, 통합 모니터링 대시보드
    - 제외: 멀티 Pod 구성, 복잡한 캐시 전략

---

## 기술 스택
- **언어**: Kotlin
- **프레임워크**: Spring Boot 3.2.x
- **데이터베이스**: MySQL 8.0
- **캐시**: Caffeine
- **모니터링**: Prometheus, Grafana
- **부하 테스트**: k6
- **인프라**: Docker Compose

---

## 도메인 모델

### Product (상품)
- **필드**: id, name, price, stock, category, createdAt
- **카테고리**: ELECTRONICS, FASHION, FOOD, BOOK
- **인덱스**: category, createdAt

### Order (주문)
- **필드**: id, userId, productId, quantity, totalAmount, status, createdAt
- **상태**: PENDING, PAID, CANCELLED
- **인덱스**: userId, createdAt

**참고**: 단순화를 위해 주문-상품 1:1 관계로 설계 (OrderItem 제외)

---

## API 명세

### GET /api/products
상품 목록 조회 (페이징)
- **Query Parameters**: page(default: 0), size(default: 20), category(optional)

### GET /api/products/{id}
상품 상세 조회
- **Path Variable**: productId

### GET /api/orders/user/{userId}
사용자별 주문 목록 조회
- **Path Variable**: userId
- **Query Parameters**: page, size

### POST /api/orders
주문 생성
- **Request Body**: { userId, productId, quantity }

---

## 기능 요구사항

### 1. 캐시 적용

#### 캐시 대상 및 설정
- **상품 단건 조회** (id 기반)
    - **TTL: 60초** (1분)
    - 최대 크기: 10,000개

- **상품 목록 조회** (카테고리 기반)
    - **TTL: 60초** (1분)
    - 최대 크기: 100개

- **사용자별 주문 목록**
    - **TTL: 60초** (1분)
    - 최대 크기: 5,000개

**중요**: TTL을 1분으로 설정하여 2분 테스트 동안 캐시 만료 및 재적재 과정을 관찰할 수 있도록 함

#### 캐시 전략
- **읽기**: Cache-Aside 패턴
    - 캐시 확인 → 미스 시 DB 조회 → 캐시 저장

- **쓰기**: Write-Invalidate 패턴
    - 주문 생성 시 → 해당 상품 캐시 삭제

#### 캐시 ON/OFF 제어
- `application.yml`에 `cache.enabled` 설정으로 캐시 활성화/비활성화 가능
- 환경 변수 `CACHE_ENABLED`로 런타임 제어 가능

### 2. Cache Penetration 방지 및 관찰

#### Cache Penetration이란?
존재하지 않는 데이터를 반복적으로 조회할 때, 캐시에 없으므로 매번 DB에 쿼리가 발생하는 현상

#### 구현 요구사항

**Null 값 캐싱**:
- 존재하지 않는 데이터 조회 시 null 값도 캐시에 저장
- Null 캐시 TTL: **10초** (일반 TTL보다 짧게 설정)
- Caffeine은 null을 직접 캐싱하지 않으므로 Optional 또는 별도의 NullValue 객체 사용

**적용 지점**:
```kotlin
// 예시 로직 (코드 작성 X, 요구사항 명시)
fun getProduct(id: Long): Product? {
    // 1. 캐시 확인
    // 2. 캐시 미스 시 DB 조회
    // 3. DB에도 없으면 NullValue를 캐시에 저장 (TTL 10초)
    // 4. 다음 요청 시 NullValue를 찾으면 DB 조회 없이 null 반환
}
```

#### Cache Penetration 테스트 시나리오

**시나리오 설정**:
- 존재하지 않는 상품 ID를 반복 조회
- 예: productId = 999999999 (DB에 없는 ID)
- 1초당 100회 요청 (2분간 총 12,000회)

**측정 지표**:
- DB 쿼리 발생 횟수
- Cache Penetration 방지 효과 (null 캐싱 ON/OFF 비교)

### 3. 메트릭 수집

#### Cache Penetration 관련 메트릭 추가

**애플리케이션 메트릭**:
- `cache_null_value_hit_total`: Null 값 캐시 히트 수 (DB 조회 방지 성공)
- `cache_null_value_stored_total`: Null 값 캐싱 횟수
- `db_query_for_nonexistent_data_total`: 존재하지 않는 데이터에 대한 DB 쿼리 수

**MySQL 메트릭**:
- `mysql_global_status_queries`: 총 쿼리 수
- Cache Penetration 시나리오 실행 시 쿼리 수 급증 여부 확인

### 4. 패키지 구조

```
com.example.springpractice.cache/
├── config/
│   └── CacheConfig.kt              # Caffeine 캐시 설정 (TTL 1분)
├── domain/
│   ├── Product.kt
│   ├── Order.kt
│   └── NullValue.kt                # Null 값 캐싱용 객체
├── repository/
│   ├── ProductRepository.kt
│   └── OrderRepository.kt
├── service/
│   ├── ProductService.kt           # 캐시 적용 + Null 캐싱
│   └── OrderService.kt             # 캐시 무효화
├── controller/
│   ├── ProductController.kt
│   └── OrderController.kt
└── metrics/
    └── CacheMetrics.kt             # Cache Penetration 메트릭 수집
```

### 5. 테스트 데이터

#### 데이터 생성 요구사항
- **상품(Product)**: 10만 건
    - ID: 1 ~ 100,000
    - 카테고리별 균등 분포 (각 25,000 건)
    - 가격: 1,000원 ~ 100,000원 랜덤
    - 재고: 10 ~ 1,000 랜덤

- **주문(Order)**: 5만 건
    - userId: 1 ~ 10,000 랜덤
    - productId: 1 ~ 100,000 범위에서 랜덤 선택
    - 상태: PAID 90%, PENDING 10%

**참고**: 2분 테스트에 맞춰 데이터 규모 축소 (빠른 데이터 생성)

#### SQL 파일
- `init-schema.sql`: 테이블 생성 + 인덱스
- `generate-data.sql`: 데이터 INSERT (프로시저 또는 배치 INSERT)

---

## Docker Compose 구성

### 서비스 목록

1. **MySQL**
    - 포트: 3306
    - 볼륨:
        - `./sql/init-schema.sql`
        - `./sql/generate-data.sql`
    - 환경변수:
        - MYSQL_ROOT_PASSWORD: root
        - MYSQL_DATABASE: cache_test

2. **Spring Boot App**
    - 포트: 8080
    - 환경변수:
        - CACHE_ENABLED: true/false (테스트용)

3. **Prometheus**
    - 포트: 9090
    - 수집 주기: **5초** (2분 테스트에 맞춰 세밀하게)
    - 수집 대상:
        - Spring Boot 메트릭 (actuator)
        - MySQL Exporter 메트릭

4. **MySQL Exporter**
    - MySQL 성능 메트릭 수출

5. **Grafana**
    - 포트: 3000
    - 데이터소스: Prometheus 자동 연결

---

## 모니터링 요구사항

### Prometheus 메트릭 수집

#### 애플리케이션 메트릭
- **캐시 메트릭**:
    - `cache_hit_total`: 캐시 히트 수
    - `cache_miss_total`: 캐시 미스 수
    - `cache_hit_rate`: 캐시 히트율 (%)
    - `cache_size`: 현재 캐시 엔트리 수
    - `cache_eviction_total`: TTL 만료로 인한 캐시 축출 수
    - **`cache_null_value_hit_total`**: Null 값 캐시 히트 (Cache Penetration 방지 성공)
    - **`cache_null_value_stored_total`**: Null 값 캐싱 횟수
    - **`db_query_for_nonexistent_data_total`**: 존재하지 않는 데이터 조회로 인한 DB 쿼리

- **API 메트릭**:
    - `http_server_requests_seconds`: API 응답 시간 (p50, p95, p99)
    - `http_server_requests_total`: API 호출 수

- **JVM 메트릭**:
    - `jvm_memory_used_bytes{area="heap"}`: Heap 메모리 사용량
    - `jvm_gc_pause_seconds`: GC 일시 정지 시간
    - `jvm_threads_live`: 활성 스레드 수
    - `process_cpu_usage`: 프로세스 CPU 사용률

#### MySQL 메트릭
- `mysql_global_status_queries`: 총 쿼리 수 (QPS 계산용)
- `mysql_global_status_threads_running`: 실행 중인 쿼리 스레드
- `mysql_global_status_slow_queries`: 느린 쿼리 수
- `rate(mysql_global_status_innodb_buffer_pool_reads)`: 디스크 읽기 (IOPS)
- MySQL 프로세스 메트릭:
    - CPU 사용률
    - 메모리 사용량

### Grafana 통합 대시보드

**`performance-comparison.json` 파일 작성**

**대시보드 레이아웃** (단일 페이지에 모든 메트릭):

#### Row 1: 캐시 성능 (3개 패널)
1. **캐시 히트율** (게이지 + 시계열)
    - 현재 히트율 게이지 (큰 숫자)
    - 시간별 히트율 추이 그래프
    - **1분 TTL로 인한 주기적 히트율 변화 관찰**

2. **캐시 히트/미스/축출** (시계열)
    - 캐시 히트 수 (초록색 선)
    - 캐시 미스 수 (빨간색 선)
    - **캐시 축출 수 (주황색 선) - TTL 만료 시점 확인**
    - 총 캐시 크기 (파란색 선)

3. **Cache Penetration 방지 효과** (시계열)
    - Null 값 캐시 히트 수 (초록색 선)
    - 존재하지 않는 데이터 DB 쿼리 수 (빨간색 선)
    - **Null 캐싱 ON/OFF 비교 가능하도록 주석 표시**

#### Row 2: 성능 비교 - 애플리케이션 (3개 패널)
4. **API 응답 시간** (듀얼 축 그래프)
    - 좌축: 캐시 OFF 응답 시간 (p95)
    - 우축: 캐시 ON 응답 시간 (p95)
    - 엔드포인트별 그룹핑

5. **프로세스 CPU 사용률** (시계열)
    - Spring Boot 프로세스 CPU (%)
    - 범례에 평균값 표시

6. **JVM Heap 메모리** (시계열)
    - 사용 중인 Heap (MB)
    - 최대 Heap (점선)
    - 캐시로 인한 증가량 확인

#### Row 3: 성능 비교 - MySQL (3개 패널)
7. **MySQL QPS** (시계열)
    - Queries Per Second
    - **주석: 캐시 ON 시점, Cache Penetration 테스트 시점 표시 (수직선)**

8. **MySQL CPU & 메모리** (듀얼 축 그래프)
    - 좌축: CPU 사용률 (%)
    - 우축: 메모리 사용량 (GB)

9. **MySQL 디스크 I/O** (시계열)
    - InnoDB 디스크 읽기 (IOPS)
    - 버퍼 풀 히트율 (보조 축)

#### Row 4: 통합 비교 (1개 패널)
10. **전체 시스템 부하 비교** (복합 그래프)
- MySQL QPS (막대)
- API 응답 시간 (선)
- 프로세스 CPU (선)
- 캐시 히트율 (선)
- **Cache Penetration DB 쿼리 수 (선)**
- **모든 메트릭을 정규화하여 0-100 범위로 표시** (비교 용이)

**대시보드 기능**:
- 시간 범위 선택: 최근 2분 / 5분 / 15분 (기본: 5분)
- 변수: `cache_status` (ON/OFF) - 필터링용
- 자동 새로고침: **5초** (2분 테스트에 맞춰)
- Annotation: 주요 이벤트 표시
    - 캐시 활성화 시점
    - Cache Penetration 테스트 시작/종료 시점
    - TTL 만료 예상 시점 (1분마다)

---

## 부하 테스트

### k6 테스트 시나리오

**총 테스트 시간: 2분**

#### 시나리오 1: 일반 캐시 성능 테스트

**`load-test-cache.js` 파일**:

```javascript
// Stage 1: 워밍업 (20초)
// - VU: 0 → 30
// - 목적: 캐시 채우기

// Stage 2: 안정 부하 (60초)
// - VU: 30 유지
// - 목적: TTL 만료 및 재적재 관찰 (1분 TTL)

// Stage 3: 피크 부하 (30초)
// - VU: 30 → 50
// - 목적: 최대 부하 시 성능 측정

// Stage 4: 쿨다운 (10초)
// - VU: 50 → 0
```

**API 호출 분포**:
- GET /api/products/{id}: 50% (캐시 효과 극대화)
- GET /api/products: 30%
- GET /api/orders/user/{userId}: 15%
- POST /api/orders: 5%

**측정 지표**:
- `http_req_duration` p95, p99
- `http_req_failed` 비율
- `http_reqs` (RPS)

#### 시나리오 2: Cache Penetration 테스트

**`load-test-penetration.js` 파일**:

```javascript
// 총 2분 테스트

// Stage 1: 정상 조회 (30초)
// - VU: 20
// - 존재하는 상품 ID 조회 (1 ~ 100,000)

// Stage 2: Cache Penetration 공격 (60초)
// - VU: 50
// - 존재하지 않는 상품 ID 반복 조회
//   - productId: 999999999, 999999998, 999999997...
//   - 1초당 약 100회 요청

// Stage 3: 정상 조회 복귀 (30초)
// - VU: 20
// - 다시 존재하는 상품 ID 조회
```

**목적**:
- Null 캐싱 없이 실행 시 → DB 쿼리 폭증 관찰
- Null 캐싱 있을 때 실행 시 → DB 쿼리 급감 확인

**임계값 설정**:
- Cache Penetration 구간에서:
    - Null 캐싱 OFF: `mysql_queries > 5000/sec`
    - Null 캐싱 ON: `mysql_queries < 100/sec`

---

## 자동화 스크립트

### 전체 실행 스크립트

**`run-test.sh` 파일**:

```bash
#!/bin/bash

echo "=== Spring Local Cache Test (2-minute version) ==="

# 1. 인프라 시작
echo "[1/8] Starting infrastructure..."
docker-compose up -d mysql mysql-exporter prometheus grafana
sleep 20

# 2. MySQL 준비 대기 (데이터 로딩)
echo "[2/8] Waiting for MySQL (data loading: ~2 minutes)..."
# 헬스체크 로직
sleep 120

# 3. 캐시 OFF 테스트
echo "[3/8] Starting app with cache OFF..."
export CACHE_ENABLED=false
docker-compose up -d app
sleep 10

echo "[3/8] Running load test with cache OFF (2 minutes)..."
k6 run --out json=results/cache-off.json k6/load-test-cache.js

# 4. 안정화 대기
echo "[4/8] Stabilization period (30 seconds)..."
sleep 30

# 5. 캐시 ON 테스트
echo "[5/8] Restarting app with cache ON..."
docker-compose stop app
export CACHE_ENABLED=true
docker-compose up -d app
sleep 10

echo "[5/8] Running load test with cache ON (2 minutes)..."
k6 run --out json=results/cache-on.json k6/load-test-cache.js

# 6. Cache Penetration 테스트 (Null 캐싱 OFF)
echo "[6/8] Cache Penetration test - Null caching OFF (2 minutes)..."
export NULL_CACHING_ENABLED=false
docker-compose restart app
sleep 10
k6 run --out json=results/penetration-off.json k6/load-test-penetration.js

sleep 10

# 7. Cache Penetration 테스트 (Null 캐싱 ON)
echo "[7/8] Cache Penetration test - Null caching ON (2 minutes)..."
export NULL_CACHING_ENABLED=true
docker-compose restart app
sleep 10
k6 run --out json=results/penetration-on.json k6/load-test-penetration.js

# 8. 결과 수집
echo "[8/8] Collecting results..."
./scripts/collect-metrics.sh

echo "=== Test completed! ==="
echo "Total time: ~12 minutes (4 tests × 2 min + setup)"
echo "Check results/ directory and docs/PERFORMANCE_REPORT.md"
```

**총 실행 시간**: 약 12분
- MySQL 데이터 로딩: 2분
- 캐시 OFF 테스트: 2분
- 캐시 ON 테스트: 2분
- Cache Penetration OFF: 2분
- Cache Penetration ON: 2분
- 준비 및 안정화: 2분

### 메트릭 수집 스크립트

**`collect-metrics.sh` 파일**:

```bash
#!/bin/bash

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULT_DIR="results/${TIMESTAMP}"

mkdir -p ${RESULT_DIR}

echo "Collecting metrics from Prometheus..."

# Prometheus 쿼리 실행 및 저장
# - 캐시 히트율 (시간별)
# - API 응답 시간 (p95, p99)
# - MySQL QPS
# - CPU/메모리 사용량
# - Cache Penetration 메트릭 (Null 캐시 히트, DB 쿼리 수)

# Grafana 대시보드 스크린샷
# - 각 테스트 구간별로 PNG export
# - 파일명: cache-off.png, cache-on.png, penetration-off.png, penetration-on.png

# k6 결과 복사
cp results/cache-off.json ${RESULT_DIR}/
cp results/cache-on.json ${RESULT_DIR}/
cp results/penetration-off.json ${RESULT_DIR}/
cp results/penetration-on.json ${RESULT_DIR}/

# 성능 리포트 생성 (템플릿 기반)
./scripts/generate-report.sh ${RESULT_DIR}

echo "Results saved to ${RESULT_DIR}"
```

---

## 성능 리포트

### 리포트 파일

**`PERFORMANCE_REPORT.md` (한글, 자동 생성)**

#### 필수 포함 내용

**1. 테스트 환경**
- 데이터 규모: 상품 10만 건, 주문 5만 건
- 부하 테스트: k6 (최대 50 VU)
- 테스트 시간: 각 2분 (4가지 시나리오)
- 캐시 TTL: **60초 (1분)**

**2. 캐시 설정**
| 캐시 이름 | TTL | Null 캐시 TTL | 최대 크기 |
|---------|-----|--------------|---------|
| 상품 상세 | 60초 | 10초 | 10,000개 |
| 상품 목록 | 60초 | 10초 | 100개 |
| 주문 목록 | 60초 | 10초 | 5,000개 |

**3. 성능 비교 결과**

**테스트 1: 캐시 OFF vs ON**

**API 응답 시간** (단위: ms):
| API | 캐시 OFF (p95) | 캐시 ON (p95) | 개선율 |
|-----|---------------|--------------|-------|
| GET /products/{id} | X ms | Y ms | Z% |
| GET /products | A ms | B ms | C% |
| GET /orders/user/{userId} | D ms | E ms | F% |

**처리량** (RPS):
| 구분 | 캐시 OFF | 캐시 ON | 증가율 |
|-----|---------|---------|-------|
| 평균 RPS | X | Y | Z% |
| 최대 RPS | A | B | C% |

**MySQL 부하**:
| 메트릭 | 캐시 OFF | 캐시 ON | 감소율 |
|-------|---------|---------|-------|
| 평균 QPS | X | Y | Z% |
| 최대 QPS | A | B | C% |
| 평균 CPU (%) | D | E | F% |
| 디스크 IOPS | G | H | I% |

**캐시 동작 특성** (TTL 1분):
- 캐시 히트율 변화:
    - 0~60초: X% → Y% (상승)
    - 60초 시점: 급격한 미스 발생 (TTL 만료)
    - 60~120초: Y% → Z% (재상승)
- 캐시 축출 발생: 60초 시점에 대량 축출 관찰됨

**테스트 2: Cache Penetration 방지 효과**

**Null 캐싱 OFF (Cache Penetration 발생)**:
| 메트릭 | 정상 구간 | Penetration 구간 | 증가율 |
|-------|----------|----------------|-------|
| MySQL QPS | X | Y | +Z% |
| DB 쿼리 (존재하지 않는 데이터) | A | B | +C배 |
| API 응답 시간 (p95) | D ms | E ms | +F% |
| MySQL CPU (%) | G | H | +I% |

**Null 캐싱 ON (Cache Penetration 방지)**:
| 메트릭 | 정상 구간 | Penetration 구간 | 변화 |
|-------|----------|----------------|-----|
| MySQL QPS | X | Y | ±Z% (거의 변화 없음) |
| DB 쿼리 (존재하지 않는 데이터) | A | B | 최초 1회만 발생 |
| Null 캐시 히트 | 0 | C회 | - |
| API 응답 시간 (p95) | D ms | E ms | ±F% (거의 변화 없음) |

**Cache Penetration 방지 효과 요약**:
- Null 캐싱으로 인해 존재하지 않는 데이터 조회 시 DB 쿼리가 **X회 → Y회로 감소 (Z% 감소)**
- MySQL CPU 사용률이 Penetration 구간에서 **안정적으로 유지됨**
- Null 캐시 히트율: W% (10초 TTL 내에서 효과적으로 동작)

**4. 주요 발견 사항**

**일반 캐시 효과**:
- MySQL QPS가 X% 감소하여 데이터베이스 부하가 크게 줄어듦
- API 응답 시간이 평균 Y% 개선됨
- 1분 TTL로 인해 60초마다 캐시 미스가 발생하지만, 빠르게 회복됨

**Cache Penetration 방지**:
- Null 캐싱이 없으면 존재하지 않는 데이터 조회 시 DB에 지속적으로 부하 발생
- Null 캐싱 적용으로 동일한 존재하지 않는 데이터 조회 시 **DB 쿼리가 최초 1회만 발생**
- 10초 짧은 TTL로도 충분히 효과적 (악의적 반복 조회 방어)

**5. 시각화**

각 테스트 구간별 Grafana 스크린샷 포함:
- **캐시 OFF 구간** (0~2분): 높은 MySQL QPS, 낮은 캐시 히트율
- **캐시 ON 구간** (0~2분):
    - 0~60초: 캐시 히트율 상승
    - 60초 시점: TTL 만료로 인한 일시적 미스 급증
    - 60~120초: 재적재 후 히트율 회복
- **Cache Penetration OFF** (0~2분):
    - 30~90초 구간: MySQL QPS 폭증
    - DB 쿼리 수 급증 그래프
- **Cache Penetration ON** (0~2분):
    - 30~90초 구간: MySQL QPS 안정적
    - Null 캐시 히트 수 증가 그래프

**6. TTL 1분 설정의 영향**
- **장점**:
    - 2분 테스트에서 캐시 만료 및 재적재 주기를 명확히 관찰 가능
    - 실제 운영 환경에서 데이터 신선도 유지 (1분마다 갱신)
- **단점**:
    - 60초마다 캐시 미스 발생으로 순간적인 DB 부하 증가
    - 안정적인 히트율 유지가 어려움 (주기적 변동)
- **권장 사항**:
    - 운영 환경에서는 TTL을 3~5분으로 설정 (데이터 특성에 따라 조정)
    - 자주 변경되는 데이터는 짧은 TTL, 정적 데이터는 긴 TTL

**7. 결론**
- **로컬 캐시 효과**: MySQL 부하 X% 감소, API 응답 시간 Y% 개선
- **Cache Penetration 방지**: Null 캐싱으로 악의적 조회 공격에 효과적 대응
- **TTL 전략**: 1분 TTL로 주기적 갱신 관찰, 실제로는 데이터 특성에 맞게 조정 필요
- **메모리 트레이드오프**: 캐시로 인한 메모리 증가 대비 성능 향상이 충분히 가치 있음

---

## 프로젝트 구조

```
spring-practice/
├── src/
│   └── main/
│       ├── kotlin/
│       │   └── com/example/springpractice/cache/
│       │       ├── config/
│       │       │   └── CacheConfig.kt (TTL 60초 설정)
│       │       ├── domain/
│       │       │   ├── Product.kt
│       │       │   ├── Order.kt
│       │       │   └── NullValue.kt (Null 캐싱용)
│       │       ├── repository/
│       │       ├── service/
│       │       │   └── ProductService.kt (Null 캐싱 로직)
│       │       ├── controller/
│       │       └── metrics/
│       │           └── CacheMetrics.kt (Cache Penetration 메트릭)
│       └── resources/
│           ├── application.yml (cache.enabled, null-caching.enabled)
│           └── application-cache-off.yml
├── sql/
│   ├── init-schema.sql
│   └── generate-data.sql (10만 건)
├── docker/
│   ├── docker-compose.yml
│   ├── prometheus.yml (scrape_interval: 5s)
│   └── grafana/
│       ├── datasources/
│       │   └── prometheus.yml
│       └── dashboards/
│           └── performance-comparison.json (Cache Penetration 패널 포함)
├── k6/
│   ├── load-test-cache.js (2분 시나리오)
│   └── load-test-penetration.js (2분 시나리오)
├── scripts/
│   ├── run-test.sh (전체 자동화, 총 12분)
│   ├── collect-metrics.sh
│   └── generate-report.sh
├── results/
│   └── (테스트 결과 저장)
├── docs/
│   └── PERFORMANCE_REPORT.md
├── build.gradle.kts
└── README.md
```

---

## 제약사항

### 반드시 지켜야 할 것
- [ ] 모든 캐시 TTL을 **60초(1분)**로 설정
- [ ] Null 캐시 TTL은 **10초**로 설정
- [ ] 각 테스트 시나리오는 정확히 **2분** 실행
- [ ] Cache Penetration 테스트 시나리오 필수 포함
- [ ] Null 캐싱 ON/OFF 비교 테스트 수행
- [ ] Cache Penetration 관련 메트릭 수집 (Null 캐시 히트, DB 쿼리 수)
- [ ] 모든 메트릭(JVM, MySQL, Process, Cache Penetration)을 하나의 Grafana 대시보드에 표시
- [ ] 한글 성능 리포트에 Cache Penetration 결과 포함

### 권장사항
- [ ] Grafana 대시보드에 TTL 만료 시점 주석 표시 (1분마다)
- [ ] Cache Penetration 구간을 대시보드에 수직선으로 표시
- [ ] k6 결과에서 각 구간별 통계 추출

### 하지 말아야 할 것
- [ ] TTL을 1분 이외의 값으로 설정 금지
- [ ] 테스트 시간을 2분 이외로 변경 금지
- [ ] Cache Penetration 테스트 생략 금지

---

## 실행 가이드

### 사전 요구사항
- Docker, Docker Compose
- JDK 17+
- 최소 8GB RAM

### 실행 방법

```bash
# 1. 전체 자동 실행 (권장)
chmod +x scripts/run-test.sh
./scripts/run-test.sh

# 예상 소요 시간: 약 12분
# - MySQL 데이터 생성: 2분
# - 캐시 OFF 테스트: 2분
# - 캐시 ON 테스트: 2분
# - Cache Penetration OFF: 2분
# - Cache Penetration ON: 2분
# - 준비 및 안정화: 2분

# 2. 결과 확인
# - Grafana: http://localhost:3000 (admin/admin)
# - docs/PERFORMANCE_REPORT.md 확인

# 3. 정리
docker-compose down -v
```

### 모니터링 포인트

**1분 TTL 관찰**:
- 0초: 캐시 비어있음 (콜드 스타트)
- 0~60초: 캐시 히트율 점진적 상승
- **60초: TTL 만료, 캐시 축출 대량 발생, 히트율 급락**
- 60~120초: 캐시 재적재, 히트율 다시 상승

**Cache Penetration 관찰**:
- Null 캐싱 OFF: 존재하지 않는 ID 조회 시 매번 DB 쿼리 발생
- Null 캐싱 ON: 최초 1회만 DB 쿼리, 이후 10초간 캐시에서 처리

---

이 요구사항으로 Gemini CLI에 코드 생성을 요청하면, 2분 단위 테스트로 캐시 성능과 Cache Penetration 방지 효과를 명확하게 확인할 수 있는 프로젝트를 받을 수 있습니다.