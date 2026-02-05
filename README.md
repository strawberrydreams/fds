# FDS (Fraud Detection System)

실시간 이상거래 탐지 및 관리자 대시보드 시스템입니다. Spring Boot 백엔드, ML 기반 사기 탐지 엔진, RAG 기반 AI 챗봇으로 구성된 금융 사기 방지 통합 솔루션입니다.

> **참고**: `Hyun71/` 디렉터리는 Spring Boot + Thymeleaf + JPA(Oracle/H2) 기반의 고객 정보 CRUD 데모 프로젝트로, 본 FDS 시스템과는 별도의 독립 프로젝트입니다.

## 프로젝트 구성

| 프로젝트 | 설명 | 기술 스택 | 포트 |
|---------|------|----------|------|
| **FDS** (메인) | 백엔드 API 및 관리자 대시보드 | Spring Boot 4.0.1, Java 21, Oracle | 8088 |
| **fdsfraudengine** | ML 기반 사기 탐지 추론 API | Flask 3.1.2, XGBoost, LightGBM | 5001 |
| **fdsaichatbot** | RAG 기반 고객 지원 챗봇 | Flask 3.1.2, TF-IDF, 로컬 LLM | 5001 |

## 핵심 기능

### 1. 실시간 사기 탐지

거래 요청 시 3단계 필터링 파이프라인을 통해 실시간으로 사기 여부를 판정합니다.

```
거래 요청 → 블랙리스트 확인 → 자동 승인 한도 체크 → AI/Rule 기반 탐지 → 승인/보류
```

- **Rule Engine**: 고액 거래 (5,000만원 이상), 심야 고액 거래 (00~05시, 100만원 이상) 탐지
- **AI Engine**: XGBoost (카드 거래), LightGBM (송금 거래) 모델 기반 사기 확률 예측
- **동적 정책**: 서버 재시작 없이 임계치, 자동 승인 한도 실시간 변경

### 2. 관리자 대시보드

- 보류된 거래 승인/거절 처리
- 블랙리스트 계좌 관리 (등록/해제)
- 신고 누적 랭킹 조회
- 탐지 이력 및 통계 조회

### 3. AI 챗봇 지원

- TF-IDF 기반 FAQ 문서 검색
- 로컬 LLM(GPT-OSS-20B)을 활용한 RAG 방식 응답 생성
- 안심 거래 및 이상거래 탐지 관련 126개 FAQ 지원

## 기술 스택

### FDS (메인 백엔드)

| 항목 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 4.0.1 |
| ORM | Spring Data JPA, Hibernate |
| Database | Oracle (운영), H2 (개발) |
| View | Thymeleaf |
| Security | Spring Security 7, JWT (JJWT 0.11.5) |
| Build | Gradle 9.2.1 |

### fdsfraudengine (사기 탐지 엔진)

| 항목 | 기술 |
|------|------|
| Framework | Flask 3.1.2 |
| ML Engine | XGBoost 3.1.2 (카드), LightGBM 4.6.0 (송금) |
| Data Processing | pandas 2.3.3, scikit-learn 1.7.2 |
| Model Serialization | joblib 1.5.3 |

### fdsaichatbot (AI 챗봇)

| 항목 | 기술 |
|------|------|
| Framework | Flask 3.1.2 |
| Text Search | scikit-learn TF-IDF (문자 2~4-gram) |
| LLM | OpenAI 호환 API (LM Studio 등) |
| Data | NumPy 1.26.4, pandas 2.3.3 |

## 설치 방법

### 사전 요구사항

- Java 21+
- Python 3.10+
- Oracle Database (또는 H2)
- LM Studio (챗봇용 로컬 LLM)

### 1. FDS 메인 프로젝트

```bash
cd FDS

# 환경 변수 설정 (JWT 시크릿)
export FDS_SECURITY_JWT_SECRET="your-secret-key"

# 빌드 및 실행
./gradlew bootRun
```

서버 시작: `http://localhost:8088`

### 테스트

```bash
./gradlew test
```

- Mock 데이터 기반의 기본 스모크 테스트가 포함됩니다.

### 2. 사기 탐지 엔진

```bash
cd fdsfraudengine

# 의존성 설치
pip install -r requirements.txt

# 서버 실행
cd app
python app.py
```

서버 시작: `http://localhost:5001`

### 3. AI 챗봇

```bash
cd fdsaichatbot

# 의존성 설치
pip install -r requirements.txt

# LM Studio 실행 (http://127.0.0.1:1234에서 GPT-OSS-20B 모델 로드)

# 챗봇 서버 실행
cd 12_01
python lmstudio_gptoss20b_chat.py
```

서버 시작: `http://localhost:5001`

> **주의**: 사기 탐지 엔진과 챗봇이 동일한 포트(5001)를 사용합니다. 동시 운영 시 포트 변경이 필요합니다.

## 사용 예

### 송금 요청 및 사기 탐지

```bash
# 송금 요청 (FDS 탐지 자동 수행)
curl -X POST http://localhost:8088/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user123",
    "amount": 1000000,
    "sourceValue": "110-123-456789",
    "targetAccountNumber": "220-987-654321",
    "txType": "TRANSFER"
  }'
```

### 사기 탐지 엔진 직접 호출

```bash
# 송금 거래 사기 확률 예측
curl -X POST http://localhost:5001/api/predict \
  -H "Content-Type: application/json" \
  -d '{"tx_type": "TRANSFER", "amount": 5000000, "old_bal": 5000000}'

# 응답
# {"status": "success", "probability": 0.87, "engine": "ENGINE_B_TRANSFER"}
```

### AI 챗봇 질의

```bash
curl -X POST http://localhost:5001/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "이상거래가 탐지되면 어떻게 되나요?"}'
```

### 관리자 API

```bash
# 보류된 거래 승인
curl -X POST http://localhost:8088/api/v1/admin/approve/123 \
  -H "Authorization: Bearer {JWT_TOKEN}"

# 계좌 블랙리스트 등록
curl -X POST http://localhost:8088/api/v1/admin/blacklist \
  -H "Authorization: Bearer {JWT_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"accountNum": "220-987-654321", "reason": "다수 신고 접수"}'
```

## API 엔드포인트

### FDS 메인 백엔드

| Method | 엔드포인트 | 설명 |
|--------|----------|------|
| POST | `/api/v1/transactions` | 신규 송금 요청 (사기 탐지 수행) |
| GET | `/api/v1/transactions/history` | 거래 이력 조회 |
| POST | `/api/v1/admin/approve/{id}` | 보류 거래 승인 |
| POST | `/api/v1/admin/reject/{id}` | 거래 거절 및 블랙리스트 등록 |
| GET | `/api/v1/admin/blacklist` | 블랙리스트 조회 |
| POST | `/api/v1/admin/blacklist` | 블랙리스트 등록 |
| DELETE | `/api/v1/admin/blacklist/{accountNum}` | 블랙리스트 해제 |
| GET | `/api/fraud/all` | 탐지 이력 조회 |
| GET | `/api/reports/ranking` | 신고 누적 랭킹 |
| GET | `/api/v1/admin/config/all` | 시스템 설정 조회 |
| POST | `/api/v1/admin/config/update` | 설정 동적 변경 |

### 사기 탐지 엔진

| Method | 엔드포인트 | 설명 |
|--------|----------|------|
| POST | `/api/predict` | 거래 사기 확률 예측 |

### AI 챗봇

| Method | 엔드포인트 | 설명 |
|--------|----------|------|
| POST | `/chat` | 챗봇 질의 (스트리밍 응답) |

## 디렉터리 구조

```
FDS/
├── src/main/java/kdt/fds/project/
│   ├── config/           # Spring Security, 필터 설정
│   ├── controller/       # REST/MVC 컨트롤러
│   ├── service/          # 비즈니스 로직
│   │   ├── TransactionService.java   # 거래 처리 핵심
│   │   ├── DetectionService.java     # 사기 탐지 엔진
│   │   ├── FdsRuleEngine.java        # 규칙 기반 탐지
│   │   └── AdminService.java         # 관리자 기능
│   ├── repository/       # 데이터 접근 계층
│   ├── entity/           # JPA 엔티티
│   ├── dto/              # 데이터 전송 객체
│   └── mapper/           # MyBatis 매퍼
├── src/main/resources/
│   ├── templates/        # Thymeleaf 템플릿
│   ├── static/           # 정적 리소스 (CSS, JS)
│   └── application.properties
├── build.gradle
└── docs/README.md

fdsfraudengine/
├── app/
│   ├── app.py            # Flask API 서버
│   └── models/
│       ├── engine_a_card_fraud.pkl      # XGBoost 카드 모델
│       └── engine_b_transfer_fraud.pkl  # LightGBM 송금 모델
└── requirements.txt

fdsaichatbot/
├── 12_01/
│   ├── lmstudio_gptoss20b_chat.py   # Flask 챗봇 서버
│   └── fds_docs.csv                 # FAQ 문서 (126개 Q&A)
└── requirements.txt
```

## 설정

### application.properties (FDS 메인)

```properties
server.port=8088
spring.profiles.active=ora
spring.jpa.hibernate.ddl-auto=update
fds.security.jwt.secret=${FDS_SECURITY_JWT_SECRET:}
fds.security.jwt.expiration-minutes=15
```

### 환경 변수 (챗봇)

| 변수명 | 설명 | 기본값 |
|--------|------|--------|
| `LLM_MODEL` | 로컬 LLM 모델명 | `gpt-oss-20b` |
| `LLM_API_URL` | LLM 서버 엔드포인트 | `http://127.0.0.1:1234/v1/chat/completions` |
| `CORS_ALLOW_ORIGINS` | 허용 CORS Origin | `http://localhost:8088,http://127.0.0.1:8088` |

## 기여 방법

1. 이 저장소를 포크합니다.
2. 기능 브랜치를 생성합니다. (`git checkout -b feature/new-feature`)
3. 변경 사항을 커밋합니다. (`git commit -m 'Add new feature'`)
4. 브랜치에 푸시합니다. (`git push origin feature/new-feature`)
5. Pull Request를 생성합니다.

### 개발 가이드라인

- **코드 스타일**: Java는 Google Java Style, Python은 PEP 8을 따릅니다.
- **커밋 메시지**: 명확하고 간결하게 작성합니다. (예: `feat: 블랙리스트 자동 등록 기능 추가`)
- **테스트**: 새 기능 추가 시 관련 테스트 코드를 함께 작성합니다.
- **문서화**: API 변경 시 README 업데이트를 포함합니다.

### 이슈 리포트

버그 발견이나 기능 제안은 GitHub Issues를 통해 등록해 주세요.

## 라이선스

이 프로젝트는 교육 및 학습 목적으로 제작되었습니다.
