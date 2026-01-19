# 🛡️ FDS (Fraud Detection System) Admin Project

**실시간 이상거래 탐지 및 관리자 대시보드 시스템**의 백엔드 API 프로젝트입니다.
Spring Boot를 기반으로 하여 대규모 거래 트래픽 처리, Rule-based 및 AI 기반 사기 탐지, 블랙리스트 동적 관리, 사기 의심 신고 접수 기능을 제공합니다.

---

## 🛠 Tech Stack
- **Language**: Java 17
- **Framework**: Spring Boot 3.x (Spring Data JPA, Spring Web)
- **Database**: H2 (Dev) / MySQL (Prod)
- **Architecture**: Layered Architecture (Controller - Service - Repository)
- **External Communication**: REST Template (Connecting to Flask AI Server)

---

## 🚀 Key Features

1. **실시간 탐지 (Real-time Detection)**: 거래 요청(`POST /transactions`) 시 즉시 FDS 엔진이 개입하여 블랙리스트, 고액 거래, 심야 거래 등을 0.1초 이내에 필터링합니다.
2. **동적 차단 (Dynamic Blocking)**: 신고가 누적된 계좌를 대시보드에서 관리자가 즉시 차단(`AdminController`)하여 추가 피해를 막습니다.
3. **유연한 정책 (Flexible Policy)**: 서버 재시작 없이 API를 통해 탐지 임계치(`Threshold`)나 자동 승인 한도(`Auto Limit`)를 실시간으로 변경할 수 있습니다.
4. **대시보드 최적화**: 복잡한 Join 쿼리와 Fetch Join을 활용하여 수십만 건의 데이터 조회 성능을 최적화했습니다.

---

## 🧱 Domain Layer (Entity)

본 시스템의 데이터 모델은 **JPA Entity**로 정의되어 있으며, 각 엔티티는 데이터베이스의 테이블과 1:1로 매핑됩니다.



### 1. `Transaction` (거래 원장)
> 모든 금융 거래의 기본 단위입니다.
* **Table**: `TRANSACTIONS`
* **주요 필드**:
    * `txId` (PK): 거래 고유 ID (Auto Increment)
    * `txAmount`: 거래 금액
    * `sourceValue` / `targetValue`: 송금인 / 수취인 계좌번호
    * `txType`: 거래 유형 (TRANSFER, CARD 등)
* **Relationships**: `User` (ManyToOne) - 거래 당사자 정보 (Lazy Loading)

### 2. `Account` (계좌)
> 사용자의 자산을 관리하는 계좌 정보입니다.
* **Table**: `ACCOUNTS`
* **주요 필드**: `accountNum` (Unique), `balance` (잔액), `status` (ACTIVE/BLOCKED)
* **Relationships**: `User` (ManyToOne)
* **비즈니스 로직**: `withdraw(amount)` - 잔액 차감 및 부족 시 예외 발생

### 3. `FraudDetectionResult` (탐지 결과)
> FDS 엔진(Rule + AI)의 분석 결과를 저장합니다.
* **Table**: `FRAUD_DETECTION_RESULTS`
* **주요 필드**:
    * `isFraud`: 사기 여부 (0: 정상, 1: 사기)
    * `probability`: AI가 예측한 사기 확률 (0.0 ~ 1.0)
    * `engine`: 판정에 사용된 엔진 이름 (예: `[Engine_B] AI Model`)

### 4. `BlacklistAccount` (차단 목록)
> 사기 의심으로 신고되거나 관리자에 의해 차단된 계좌 목록입니다.
* **Table**: `BLACKLIST_ACCOUNTS`
* **주요 필드**: `accountNum` (차단된 계좌번호), `reason` (차단 사유)

### 5. `FraudReport` (신고 내역)
> 사용자로부터 접수된 사기 의심 신고 데이터입니다.
* **Table**: `FRAUD_REPORTS`
* **주요 필드**: `reportedAccount` (피신고 계좌), `reportCount` (누적 신고 횟수)

### 6. `TransactionFeature` (AI 피처)
> AI 모델 학습 및 분석에 사용된 원시 데이터(Feature)를 보관합니다.
* **Table**: `TRANSACTION_FEATURES`
* **주요 필드**: `vFeatures` (JSON 형태의 피처 데이터)

---

## 💾 Data Access Layer (Repository & DTO)

**Spring Data JPA**를 사용하여 데이터베이스 접근을 추상화하고 있습니다.
복잡한 Join 쿼리가 필요한 경우 JPQL(`@Query`)을 사용하였으며, 단순 조회는 Method Query를 활용합니다.

### 1. `TransactionRepository`
* `findAllWithUserOrderByTxTimestampDesc()`: **[JPQL]** 거래 내역 조회 시 `User` 엔티티를 `FETCH JOIN`하여 **N+1 문제**를 방지하고 성능을 최적화했습니다.
* `findHighAmountTransactions(Double minAmount)`: 특정 금액 이상의 고액 거래 필터링.

### 2. `FraudRepository`
* `findAllWithDetails()`: **[JPQL]** `Transaction`, `User`, `TransactionFeature` 테이블을 한 번에 **Left Join**하여 대시보드용 상세 데이터(`FraudDetailDTO`)를 조회합니다.
* **DTO Projection**: 엔티티 전체를 가져오는 대신, 필요한 필드만 선택하여 DTO로 변환함으로써 메모리 사용량을 줄였습니다.

### 3. `FraudReportRepository`
* `findAllByOrderByReportCountDesc()`: 신고 횟수가 많은 악성 계좌순으로 정렬하여 대시보드 **Ranking** 데이터를 제공합니다.

### 4. 기타 Repositories
* `AccountRepository`: 사용자 이름 기반 계좌 존재 여부 확인 (`existsByUser_UserName`).
* `BlacklistRepository`: 계좌번호 기반 차단 여부 초고속 확인 (`existsByAccountNum`).
* `FdsConfigRepository`: 시스템 설정값 관리.

---

## 🧠 Service Layer Architecture

비즈니스 로직은 **책임의 분리(Separation of Concerns)** 원칙에 따라 구성되었습니다.

### 1. `TransactionService` (Core)
> 모든 거래 요청의 진입점(Entry Point)이자 전체 흐름을 제어합니다.
* 거래 요청 1차 저장 (로그 기록)
* **3단계 필터링 파이프라인** 실행:
    1. 블랙리스트 확인
    2. 자동 승인 한도 체크
    3. AI 및 룰 기반 사기 탐지 (`DetectionService` 위임)
* 최종 승인 시 잔액 이동 처리 (`executeTransfer`)

### 2. `DetectionService` (Engine)
> 거래의 위험도를 분석하고 판정(0: 정상, 1: 위험)을 내립니다.
1. **Rule Engine**: `FdsRuleEngine`을 통해 고정된 규칙(고액, 심야 등) 검사
2. **AI Model**: Flask API로 데이터를 전송하여 사기 확률(`probability`) 조회
3. **종합 판정**: AI 점수와 임계치(`THRESHOLD`)를 비교하여 최종 결정

### 3. `AdminService` (Management)
> 대시보드에서 관리자가 수행하는 조치를 담당합니다.
* **승인(`approve`)**: 보류된 거래를 강제로 승인하고 송금 실행
* **거절(`reject`)**: 거래를 거절하고 수취인 계좌를 블랙리스트에 등록
* **블랙리스트 관리**: 수동 등록(`addToBlacklist`) 및 해제

---

## 🔄 Transaction Flow



1. **User Request** (`POST /transactions`)
2. `TransactionService`: 거래 기록 생성 및 블랙리스트 검사
3. `DetectionService`: 위험도 분석 (Rule Engine + AI Server)
4. `FdsResultService`: 분석 결과 및 Feature 저장
5. **Decision**:
    * **Safe (0)**: `TransactionService`가 송금(`transfer`) 실행 -> 완료
    * **Fraud (1)**: 송금 보류 -> **Pending** 상태로 대시보드 노출

---

## 📡 API Specification

### 1. 👮 관리자 (Admin)
> Base URL: `/api/v1/admin`

| Method | URI | Description |
| :--- | :--- | :--- |
| `POST` | `/login` | 관리자 로그인 |
| `GET` | `/blacklist` | 차단된 계좌 목록 전체 조회 |
| `POST` | `/blacklist` | **[대시보드]** 계좌번호로 수동 차단 등록 |
| `DELETE` | `/blacklist/{accountNum}` | 차단 해제 (계좌번호 기준) |
| `POST` | `/approve/{id}` | 보류된 이상거래 승인 (송금 실행) |
| `POST` | `/reject/{id}` | 이상거래 거절 및 해당 계좌 차단 |

### 2. 💸 거래 (Transactions)
> Base URL: `/api/v1/transactions`

| Method | URI | Description |
| :--- | :--- | :--- |
| `POST` | `/` | 신규 이체/송금 요청 (FDS 탐지 수행) |
| `GET` | `/history` | 전체 거래 이력 조회 |
| `DELETE` | `/{id}` | 특정 거래 기록 삭제 |

### 3. 🚨 사기 탐지 및 신고 (Fraud & Reports)
> Base URL: `/api/fraud` / `/api/reports`

| Method | URI | Description |
| :--- | :--- | :--- |
| `GET` | `/api/fraud/all` | 상세 탐지 이력(조인 데이터) 조회 |
| `PATCH` | `/api/fraud/{id}/status` | 탐지 상태 변경 (정상/사기) |
| `GET` | `/api/reports/ranking` | **[대시보드]** 신고 누적 랭킹 조회 |
| `POST` | `/api/reports` | 사기 의심 계좌 신고 접수 |

### 4. ⚙️ 시스템 설정 (Config)
> Base URL: `/api/v1/admin/config`

| Method | URI | Description |
| :--- | :--- | :--- |
| `GET` | `/all` | 전체 설정값 조회 (임계치, 자동승인 금액 등) |
| `POST` | `/update` | 설정값 동적 변경 |