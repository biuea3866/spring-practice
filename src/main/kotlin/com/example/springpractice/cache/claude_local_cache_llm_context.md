# Spring Local Cache 성능 개선 프로젝트

## 개요
- **목적**: Caffeine 로컬 캐시를 적용하여 MySQL 데이터베이스 부하를 줄이고, 캐시 전략에 따른 성능 개선을 정량적으로 측정
- **범위**:
    - 포함: 로컬 캐시 적용, Cache Penetration 방지(PER 패턴), 캐시 읽기/쓰기 전략, 성능 모니터링, 부하 테스트, 자동화 스크립트
    - 제외: Redis 같은 분산 캐시, 캐시 워밍, 복잡한 캐시 동기화 전략

---

## 기술 스택
- **언어**: Kotlin 1.9+
- **프레임워크**: Spring Boot 3.2.x
- **데이터베이스**: MySQL 8.0
- **캐시 라이브러리**: Caffeine 3.x
- **모니터링**: Prometheus, Grafana
- **부하 테스트**: k6
- **인프라**: Docker Compose
- **빌드**: Gradle Kotlin DSL

---

## 도메인 모델

### Product (상품)
- **필드**: id, name, price, stock, category, description, createdAt, updatedAt
- **카테고리**: ELECTRONICS, FASHION, FOOD, BOOK, HOME, SPORTS
- **인덱스**: category, price, createdAt

### Coupon (쿠폰)
- **필드**: id, code(unique), name, discountType, discountValue, validFrom, validUntil, maxUsageCount, currentUsageCount, isActive
- **할인 타입**: PERCENTAGE, FIXED_AMOUNT
- **인덱스**: code(unique), validFrom+validUntil

### Order (주문)
- **필드**: id, userId, totalAmount, discountAmount, finalAmount, status, couponId, createdAt, updatedAt
- **상태**: PENDING, PAID, CANCELLED, REFUNDED
- **관계**: OrderItem과 1:N
- **인덱스**: userId, status, createdAt

### OrderItem (주문 항목)
- **필드**: id, orderId, productId, productName, price, quantity
- **관계**: Order와 N:1
- **인덱스**: orderId, productId

### Payment (결제)
- **필드**: id, orderId(unique), amount, method, status, failureReason, createdAt
- **결제 수단**: CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, MOBILE_PAYMENT
- **결제 상태**: PENDING, SUCCESS, FAILED
- **인덱스**: orderId(unique), status

---

## API 명세

### 조회 API

#### GET /api/products
상품 목록 조회 (페이징)
- **Query Parameters**: page(default: 0), size(default: 20), category(optional)
- **Response**: 상품 목록, 페이징 정보

#### GET /api/products/{id}
상품 상세 조회
- **Path Variable**: productId
- **Response**: 상품 상세 정보

#### GET /api/orders
주문 목록 조회 (사용자별)
- **Query Parameters**: userId, page(default: 0), size(default: 20)
- **Response**: 주문 목록, 페이징 정보

#### GET /api/orders/{id}
주문 상세 조회
- **Path Variable**: orderId
- **Response**: 주문 정보 + 주문 항목 목록

### 쓰기 API

#### POST /api/orders
주문 생성
- **Request Body**: userId, items(productId, quantity), couponCode(optional)
- **Response**: 생성된 주문 정보
- **처리 흐름**:
    1. 상품 재고 확인
    2. 쿠폰 검증 및 할인 계산
    3. 주문 생성
    4. 재고 차감
    5. 관련 캐시 무효화

---

## 기능 요구사항

### 1. 캐시 적용 대상

다음 조회 쿼리들에 로컬 캐시를 적용하여 MySQL 부하를 감소시켜야 합니다:

#### 고빈도 조회 (반드시 캐싱)
- 상품 단건 조회 (id로 조회) - 초당 1000+ QPS 예상
- 카테고리별 상품 목록 조회 - 초당 500+ QPS 예상
- 쿠폰 코드로 조회 - 주문 시 필수, 초당 300+ QPS 예상
- 사용자별 주문 목록 조회 - 초당 200+ QPS 예상
- 주문 상세 조회 (OrderItem 포함) - 초당 300+ QPS 예상

#### 캐시 설정
- 상품 상세: TTL 60초, 최대 10,000개
- 상품 목록 (카테고리별): TTL 30초, 최대 100개
- 쿠폰: TTL 120초, 최대 1,000개
- 주문 목록: TTL 30초, 최대 5,000개
- 주문 상세: TTL 60초, 최대 5,000개

### 2. Cache Penetration 방지 (PER 패턴)

**PER (Probabilistic Early Recomputation) 패턴 구현:**

**목적**:
- 캐시 만료 시점에 여러 요청이 동시에 DB에 몰리는 thundering herd 현상 방지
- 존재하지 않는 데이터에 대한 반복 조회로 인한 DB 부하 방지

**구현 요구사항**:
1. 캐시 만료 전(만료 시간의 90% 시점)에 확률적으로 데이터를 미리 재계산
2. 재계산 확률 공식: `probability = DELTA / (TTL * (1 - THRESHOLD))`
    - DELTA: 10초
    - THRESHOLD: 0.9 (90%)
    - 예: TTL=60초인 경우, 확률 = 10 / (60 * 0.1) = 약 1.67 → 최대 1.0으로 제한
3. null 값도 캐싱하여 존재하지 않는 데이터에 대한 반복 조회 방지
    - null 캐시 TTL: 5초 (짧게 설정)
4. 재계산은 비동기로 수행하여 응답 시간에 영향 없도록 구현

**적용 대상**: 모든 캐시 조회 메서드

### 3. 캐시 읽기/쓰기 전략

#### 읽기 전략: Cache-Aside + PER
- 애플리케이션이 캐시를 먼저 확인
- 캐시 미스 시 DB 조회 후 캐시에 저장
- PER 패턴으로 만료 전 재계산

**적용 지점**:
- 모든 조회 API (상품, 쿠폰, 주문)

#### 쓰기 전략: Write-Through (주문 생성 시)
- DB에 쓰기 후 즉시 관련 캐시 무효화
- 무효화 대상:
    - 주문 생성 시: 해당 상품의 캐시, 사용된 쿠폰 캐시 삭제
    - 재고 차감 반영을 위해 상품 캐시는 삭제 (업데이트 아님)

### 4. 패키지 구조

모든 예제 코드는 `com.example.springpractice.cache` 패키지에 작성:

```
com.example.springpractice.cache/
├── config/
│   ├── CacheConfig.kt              # Caffeine 캐시 빈 설정
│   └── MetricsConfig.kt            # Prometheus 메트릭 설정
├── domain/
│   ├── Product.kt                  # 엔티티
│   ├── Coupon.kt
│   ├── Order.kt
│   ├── OrderItem.kt
│   └── Payment.kt
├── repository/
│   ├── ProductRepository.kt        # JPA Repository
│   ├── CouponRepository.kt
│   ├── OrderRepository.kt
│   └── PaymentRepository.kt
├── service/
│   ├── ProductService.kt           # 캐시 적용된 서비스
│   ├── CouponService.kt
│   ├── OrderService.kt
│   └── cache/
│       ├── CacheWithPER.kt         # PER 패턴 구현
│       └── CacheMetrics.kt         # 캐시 메트릭 수집
└── controller/
    ├── ProductController.kt        # REST API
    ├── OrderController.kt
    └── MetricsController.kt        # 캐시 통계 API
```

### 5. 멀티 Pod 구성

**요구사항**:
- Docker Compose로 동일한 Spring Boot 애플리케이션을 3개의 Pod로 실행
- 각 Pod는 독립적인 로컬 캐시 보유
- Nginx를 로드 밸런서로 사용하여 라운드 로빈 분산
- 로컬 캐시의 불일치 문제를 관찰할 수 있도록 구성
    - 예: Pod A에서 주문 생성 시 Pod A의 상품 캐시만 무효화
    - Pod B, C는 여전히 이전 재고 정보를 캐시하고 있음
    - 이를 모니터링에서 확인 가능하도록 설정

**Pod 구성**:
- app-pod-1: 8081 포트
- app-pod-2: 8082 포트
- app-pod-3: 8083 포트
- nginx: 80 포트 (외부 진입점)

### 6. 테스트 데이터 생성

#### 데이터 생성 요구사항
- **상품(Product)**: 1,000만 건
    - 카테고리별 균등 분포 (각 카테고리당 약 166만 건)
    - 가격: 1,000원 ~ 1,000,000원 범위에서 랜덤
    - 재고: 0 ~ 1,000 범위에서 랜덤
    - description: 일부만 입력 (50% 확률)

- **쿠폰(Coupon)**: 10,000건
    - 할인 타입: PERCENTAGE 70%, FIXED_AMOUNT 30%
    - 할인율: 5% ~ 30% 범위
    - 할인 금액: 1,000원 ~ 50,000원 범위
    - 유효기간: 현재 시점 기준 -30일 ~ +90일
    - 사용 가능 수량: 100 ~ 10,000 범위

- **주문(Order)**: 100만 건
    - userId: 1 ~ 100,000 범위에서 랜덤
    - 주문당 OrderItem: 1~5개 랜덤
    - 쿠폰 사용: 30% 확률
    - 주문 상태: PAID 80%, PENDING 10%, CANCELLED 8%, REFUNDED 2%

- **결제(Payment)**: 주문 수와 동일 (100만 건)
    - SUCCESS 90%, FAILED 10%

#### SQL 스크립트 작성
- `init-schema.sql`: 테이블 스키마 정의 (인덱스 포함)
- `generate-data.sql`: 데이터 생성 프로시저 또는 대량 INSERT 문
    - 성능을 위해 LOAD DATA INFILE 또는 배치 INSERT 사용
    - 트랜잭션 단위로 나누어 처리 (예: 10만 건씩)

### 7. Docker Compose 구성

#### 서비스 구성
1. **MySQL**
    - 이미지: mysql:8.0
    - 포트: 3306
    - 볼륨:
        - `./sql/init-schema.sql` → `/docker-entrypoint-initdb.d/`
        - `./sql/generate-data.sql` → `/docker-entrypoint-initdb.d/`
        - `mysql-data` → `/var/lib/mysql`
    - 설정:
        - innodb_buffer_pool_size: 2G
        - max_connections: 500
        - slow_query_log: ON

2. **Spring Boot App (3개 Pod)**
    - app-pod-1, app-pod-2, app-pod-3
    - 각각 8081, 8082, 8083 포트
    - 환경 변수:
        - SPRING_PROFILES_ACTIVE: prod
        - POD_NAME: app-pod-1 (식별용)

3. **Nginx**
    - 이미지: nginx:alpine
    - 포트: 80
    - 설정: 라운드 로빈 로드 밸런싱

4. **Prometheus**
    - 이미지: prom/prometheus
    - 포트: 9090
    - 설정:
        - 3개 Pod에서 메트릭 수집
        - MySQL Exporter 메트릭 수집
        - 수집 주기: 5초

5. **MySQL Exporter**
    - 이미지: prom/mysqld-exporter
    - MySQL 성능 메트릭 수출

6. **Grafana**
    - 이미지: grafana/grafana
    - 포트: 3000
    - 데이터 소스: Prometheus 자동 설정
    - 대시보드: 자동 import

---

## 비기능 요구사항

### 1. 모니터링 시스템

#### Prometheus 메트릭 수집

**애플리케이션 메트릭** (각 Pod별로 수집):
- **캐시 메트릭**:
    - `cache_hit_total{cache_name}`: 캐시 히트 수
    - `cache_miss_total{cache_name}`: 캐시 미스 수
    - `cache_hit_rate{cache_name}`: 캐시 히트율 (%)
    - `cache_eviction_total{cache_name}`: 캐시 축출 수
    - `cache_size{cache_name}`: 현재 캐시 크기
    - `cache_load_duration_seconds{cache_name}`: 캐시 로드 시간
    - `per_early_recomputation_total{cache_name}`: PER 패턴에 의한 조기 재계산 횟수

- **API 메트릭**:
    - `http_server_requests_seconds{method, uri, status}`: API 응답 시간
    - `api_calls_total{endpoint, method}`: API 호출 수

- **JVM 메트릭**:
    - `jvm_memory_used_bytes{area}`: JVM 메모리 사용량
    - `jvm_gc_pause_seconds`: GC 일시 정지 시간
    - `jvm_threads_live`: 활성 스레드 수

**MySQL 메트릭** (MySQL Exporter):
- `mysql_global_status_threads_running`: 실행 중인 쿼리 스레드 수
- `mysql_global_status_queries`: 총 쿼리 수
- `mysql_global_status_slow_queries`: 느린 쿼리 수
- `mysql_global_status_connections`: 연결 수
- `mysql_global_status_bytes_received`: 수신 바이트
- `mysql_global_status_bytes_sent`: 송신 바이트
- `mysql_global_status_innodb_buffer_pool_read_requests`: InnoDB 버퍼 풀 읽기 요청
- `mysql_global_status_innodb_buffer_pool_reads`: 디스크 읽기 (캐시 미스)

#### Grafana 대시보드

**필수 대시보드 3개 작성**:

1. **캐시 성능 대시보드** (`cache-performance.json`)
    - 패널 구성:
        - 캐시 히트율 (시계열, 각 캐시별)
        - 캐시 히트/미스 비율 (파이 차트)
        - PER 조기 재계산 빈도 (시계열)
        - 캐시 크기 변화 (시계열)
        - 캐시 로드 시간 (히트맵)
        - Pod별 캐시 히트율 비교 (바 차트)

2. **MySQL 부하 대시보드** (`mysql-load.json`)
    - 패널 구성:
        - QPS (Queries Per Second) - 시계열
        - 느린 쿼리 수 - 시계열
        - 활성 연결 수 - 시계열
        - InnoDB 버퍼 풀 히트율 - 게이지
        - 디스크 I/O (읽기/쓰기) - 시계열
        - 네트워크 트래픽 (송수신) - 시계열

3. **비교 대시보드** (`comparison.json`)
    - 패널 구성:
        - 캐시 적용 전/후 API 응답 시간 비교 (듀얼 축 그래프)
        - 캐시 적용 전/후 MySQL QPS 비교 (듀얼 축 그래프)
        - 캐시 적용 전/후 MySQL CPU 사용률 비교
        - 캐시 적용 전/후 애플리케이션 메모리 사용량 비교
        - JVM Heap 사용량 (캐시로 인한 증가 확인)

#### 모니터링 가이드 문서

`MONITORING_GUIDE.md` 파일 작성:

**내용**:
1. **메트릭 해석 방법**
    - 각 메트릭의 의미와 정상 범위
    - 문제 징후를 나타내는 패턴

2. **대시보드 사용법**
    - 각 대시보드의 목적과 확인 사항
    - 시간 범위 설정 방법

3. **성능 분석 체크리스트**
    - 캐시 히트율이 70% 이상인가?
    - PER 패턴이 정상 작동하는가? (조기 재계산 발생 확인)
    - MySQL QPS가 감소했는가?
    - API 응답 시간이 개선되었는가?

4. **트러블슈팅**
    - 캐시 히트율이 낮은 경우
    - 메모리 부족 경고 시
    - MySQL 부하가 여전히 높은 경우

### 2. 성능 테스트 및 리포트

#### 테스트 시나리오

**k6 스크립트 작성** (`load-test.js`):

```javascript
// 시나리오 1: 캐시 워밍 (5분)
// - 상품 조회: 100 VU, duration: 5m
// - 목적: 캐시를 채우고 안정화

// 시나리오 2: 일반 트래픽 (10분)
// - 상품 목록: 50 VU
// - 상품 상세: 100 VU
// - 주문 목록: 30 VU
// - 주문 상세: 40 VU
// - 주문 생성: 20 VU
// - 목적: 실제 운영 부하 시뮬레이션

// 시나리오 3: 피크 트래픽 (5분)
// - 모든 API에 VU 2배 증가
// - 목적: 최대 부하 시 캐시 효과 측정

// 시나리오 4: Cache Penetration 테스트 (3분)
// - 존재하지 않는 상품 ID 반복 조회
// - 목적: PER 패턴의 null 캐싱 효과 확인
```

**측정 지표**:
- API 응답 시간 (p50, p95, p99)
- 요청 성공률
- RPS (Requests Per Second)

#### 성능 테스트 비교

**2단계 테스트**:

1. **캐시 비활성화 테스트**
    - 캐시 설정을 OFF로 변경
    - 위 시나리오 실행
    - 메트릭 수집

2. **캐시 활성화 테스트**
    - 캐시 설정을 ON으로 변경
    - 동일한 시나리오 실행
    - 메트릭 수집

#### 성능 리포트

`PERFORMANCE_REPORT.md` 파일 작성 (한글):

**필수 포함 내용**:

1. **테스트 환경**
    - 인프라 스펙 (MySQL, 애플리케이션 리소스)
    - 데이터 규모 (테이블별 레코드 수)
    - 테스트 도구 및 설정

2. **캐시 설정 요약**
    - 각 캐시의 TTL, 최대 크기
    - PER 패턴 설정값

3. **성능 비교 결과**

   **MySQL 부하 비교**:
    - QPS (Queries Per Second)
        - 캐시 OFF: X건/초
        - 캐시 ON: Y건/초
        - 감소율: Z%

    - CPU 사용률
        - 캐시 OFF: 평균 X%, 최대 Y%
        - 캐시 ON: 평균 A%, 최대 B%
        - 그래프 포함

    - 메모리 사용량
        - 캐시 OFF vs ON 비교
        - 그래프 포함

    - 디스크 I/O
        - IOPS 비교
        - 그래프 포함

   **애플리케이션 성능 비교**:
    - API 응답 시간 (각 엔드포인트별)
        - p50, p95, p99 비교 표
        - 개선율 계산

    - 처리량 (RPS)
        - 캐시 OFF vs ON
        - 증가율 계산

    - JVM 메트릭
        - Heap 메모리 사용량 증가 (캐시로 인한)
        - GC 빈도 및 시간 비교

4. **캐시 효율성 분석**
    - 캐시별 히트율
        - 상품 상세: X%
        - 상품 목록: Y%
        - 쿠폰: Z%
        - 주문 목록: A%
        - 주문 상세: B%

    - PER 패턴 효과
        - 조기 재계산 발생 횟수
        - Thundering herd 방지 효과 (그래프로 입증)

    - Cache Penetration 방지 효과
        - 존재하지 않는 ID 조회 시 DB 쿼리 수 비교
        - null 캐싱으로 인한 DB 부하 감소

5. **멀티 Pod 환경에서의 캐시 동작**
    - Pod별 캐시 히트율 차이
    - 캐시 불일치로 인한 문제 사례
    - 로컬 캐시의 한계점 분석

6. **결론 및 권장사항**
    - 캐시 적용으로 인한 전체 개선 효과 요약
    - 로컬 캐시 사용 시 주의사항
    - 추가 개선 방안 (Redis 도입 등)

**그래프 포함**:
- 모든 주요 비교 지표는 시계열 그래프 또는 막대 그래프로 시각화
- Grafana에서 export한 이미지 또는 스크린샷 포함

### 3. 자동화 스크립트

#### 전체 실행 스크립트

`run-test.sh` 파일 작성:

**기능**:
1. Docker Compose로 전체 인프라 시작
2. MySQL 데이터 생성 완료 대기 (헬스체크)
3. 애플리케이션 3개 Pod 시작 대기
4. Prometheus, Grafana 시작 확인
5. 캐시 OFF 상태로 k6 부하 테스트 실행
6. 5분 대기 (안정화)
7. 캐시 ON 상태로 k6 부하 테스트 실행
8. 테스트 완료 후 메트릭 수집
9. 리포트 생성을 위한 데이터 export
10. 종료 옵션 제공

**사용법**:
```bash
# 전체 실행
./run-test.sh

# 옵션
./run-test.sh --skip-data-generation  # 데이터 생성 스킵
./run-test.sh --cache-only           # 캐시 ON 테스트만
./run-test.sh --cleanup              # 테스트 후 리소스 정리
```

#### k6 부하 테스트 스크립트

`k6-load-test.js` 파일 작성:

**시나리오 구성**:
- executor: ramping-vus (점진적 사용자 증가)
- stages:
    1. 0→100 VU (1분) - 웜업
    2. 100 VU (10분) - 안정적인 부하
    3. 100→300 VU (2분) - 피크 부하
    4. 300 VU (5분) - 피크 유지
    5. 300→0 VU (2분) - 쿨다운

**API 호출 비율**:
- GET /api/products: 30%
- GET /api/products/{id}: 40%
- GET /api/orders: 10%
- GET /api/orders/{id}: 15%
- POST /api/orders: 5%

**임계값 설정**:
- http_req_duration p95 < 500ms
- http_req_failed < 1%
- checks > 95%

#### 결과 수집 스크립트

`collect-metrics.sh` 파일 작성:

**기능**:
1. Prometheus에서 메트릭 쿼리 및 저장
    - 캐시 히트율
    - MySQL QPS
    - API 응답 시간
    - JVM 메모리
2. Grafana 대시보드 스크린샷 자동 캡처
3. k6 결과 JSON 파일 저장
4. 모든 결과를 `results/` 디렉토리에 타임스탬프와 함께 저장

---

## 프로젝트 구조

최종 프로젝트 디렉토리 구조:

```
spring-local-cache/
├── src/
│   └── main/
│       ├── kotlin/
│       │   └── com/example/springpractice/cache/
│       │       ├── config/
│       │       ├── domain/
│       │       ├── repository/
│       │       ├── service/
│       │       └── controller/
│       └── resources/
│           └── application.yml
├── sql/
│   ├── init-schema.sql
│   └── generate-data.sql
├── docker/
│   ├── docker-compose.yml
│   ├── nginx.conf
│   ├── prometheus.yml
│   └── grafana/
│       ├── datasources/
│       │   └── prometheus.yml
│       └── dashboards/
│           ├── cache-performance.json
│           ├── mysql-load.json
│           └── comparison.json
├── k6/
│   └── load-test.js
├── scripts/
│   ├── run-test.sh
│   └── collect-metrics.sh
├── docs/
│   ├── MONITORING_GUIDE.md
│   └── PERFORMANCE_REPORT.md (테스트 후 생성)
├── build.gradle.kts
└── README.md
```

---

## 제약사항

### 반드시 지켜야 할 것 (MUST)
- [ ] Caffeine 캐시 라이브러리 사용
- [ ] PER 패턴 구현 (확률적 조기 재계산)
- [ ] null 값 캐싱으로 Cache Penetration 방지
- [ ] 모든 조회 API에 캐시 적용
- [ ] 주문 생성 시 관련 캐시 무효화
- [ ] 3개의 독립적인 Pod로 애플리케이션 실행
- [ ] MySQL 메트릭과 애플리케이션 메트릭 모두 수집
- [ ] 캐시 ON/OFF 비교 테스트 수행
- [ ] 한글로 작성된 성능 리포트 생성

### 권장사항 (SHOULD)
- [ ] Micrometer로 커스텀 메트릭 구현
- [ ] k6에서 Think Time 추가 (실제 사용자 시뮬레이션)
- [ ] Grafana 대시보드에 알림 설정 (캐시 히트율 < 50% 등)
- [ ] 스크립트에 에러 핸들링 추가

### 하지 말아야 할 것 (MUST NOT)
- [ ] Redis 같은 외부 캐시 사용 금지 (로컬 캐시만)
- [ ] 캐시 데이터를 DB에 저장하지 말 것
- [ ] 프로덕션 환경 설정 사용 금지 (개발/테스트용 설정만)

---

## 실행 가이드

`README.md`에 포함할 내용:

### 사전 요구사항
- Docker, Docker Compose 설치
- JDK 17 이상
- 최소 16GB RAM (MySQL 데이터 생성을 위해)

### 실행 방법

1. **전체 테스트 자동 실행**
```bash
./scripts/run-test.sh
```

2. **수동 실행**
```bash
# 1. 인프라 시작
docker-compose up -d

# 2. 데이터 생성 완료 대기 (약 30분 소요)
docker-compose logs -f mysql

# 3. 애플리케이션 빌드 및 시작
./gradlew bootJar
docker-compose up app-pod-1 app-pod-2 app-pod-3

# 4. k6 테스트 실행 (캐시 OFF)
k6 run k6/load-test.js

# 5. 캐시 활성화 후 재테스트
# application.yml에서 cache.enabled=true 설정
k6 run k6/load-test.js

# 6. 결과 수집
./scripts/collect-metrics.sh
```

3. **모니터링 접속**
- Grafana: http://localhost:3000 (admin/admin)
- Prometheus: http://localhost:9090
- 애플리케이션: http://localhost (Nginx를 통한 로드 밸런싱)

### 테스트 결과 확인
- `results/` 디렉토리에 타임스탬프별로 저장
- `docs/PERFORMANCE_REPORT.md`에서 상세 분석 확인

---

## 참고자료

### PER 패턴 관련
- Cache Penetration 문제와 해결 방법
- Thundering Herd Problem
- Probabilistic Early Recomputation 알고리즘

### Caffeine 캐시
- 공식 문서: https://github.com/ben-manes/caffeine
- Spring Boot와 통합 방법
- 캐시 통계 수집 방법

### 모니터링
- Micrometer 메트릭 수집
- Prometheus Query 작성법
- Grafana 대시보드 구성

---

이 요구사항 문서를 기반으로 Gemini CLI에게 코드 생성을 요청하면, 모든 구현이 포함된 완전한 프로젝트를 받을 수 있습니다.