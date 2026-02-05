# FDS AI Chatbot

안심 거래 및 실시간 이상거래 탐지(FDS) 관련 문의에 답변하는 RAG 기반 챗봇 서버입니다.

## 프로젝트 목적

`fds_docs.csv`에 저장된 안심 거래/이상거래 탐지 관련 FAQ 문서를 TF-IDF로 검색하고, 검색 결과를 컨텍스트로 활용해 로컬 LLM(현재 LM Studio에서 서빙되는 GPT-OSS-20B)에 RAG 방식으로 질의하여 사용자 친화적인 답변을 생성합니다.

## 핵심 기능

- **TF-IDF 기반 문서 검색**: 문자 단위 2~4-gram으로 한글 문서를 벡터화하고 코사인 유사도로 관련 문서를 검색합니다.
- **RAG 프롬프트 생성**: 검색된 문서를 프롬프트에 포함시켜 LLM이 문서 기반으로 답변하도록 유도합니다.
- **스트리밍 응답**: OpenAI 호환 `/chat/completions` 엔드포인트를 통해 토큰 단위 스트리밍 응답을 제공합니다.
- **대화 히스토리 유지**: 최근 대화 내역(최대 10개)을 저장해 맥락을 유지합니다.
- **주제 연속성**: 직전 검색에서 선택된 문서 제목을 기억해 "다음 달은?"과 같은 후속 질문에서도 주제를 이어갑니다.
- **답변 불가 질문 로깅**: 관련 문서가 없는 질문은 `unanswered_questions.txt`에 기록합니다.

## 프로젝트 구조

```
fds_ragchatbot/
├── 12_01/
│   ├── lmstudio_gptoss20b_chat.py   # Flask 챗봇 서버 메인 코드
│   ├── fds_docs.csv                 # 안심 거래/FDS FAQ 문서 (text, intent 컬럼)
│   └── __init__.py
├── main.py                          # (별도 용도 - TensorFlow 학습 예제)
├── requirements.txt                 # 의존성 목록
└── README.md
```

## 설치 방법

### 1. 의존성 설치

```bash
pip install flask requests numpy scikit-learn
```

또는 전체 의존성 설치:

```bash
pip install -r requirements.txt
```

### 2. 로컬 LLM 서버 실행

LM Studio에서 GPT-OSS-20B 모델을 로드하고 OpenAI 호환 서버를 실행합니다.
- 기본 주소: `http://127.0.0.1:1234/v1/chat/completions`

### 3. 챗봇 서버 실행

```bash
cd 12_01
python lmstudio_gptoss20b_chat.py
```

서버가 `http://0.0.0.0:5001`에서 시작됩니다.

## 사용 예시

### API 호출

```bash
curl -X POST http://127.0.0.1:5001/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "이상거래 신고는 어떻게 하나요?"}'
```

### 응답 형식

- 성공 시: `text/plain` 스트리밍 응답 (토큰 단위로 전송)
- 실패 시: `{"error": "..."}` JSON 응답

### 주제 외 질문 시

관련 문서가 없는 질문에는 다음 고정 응답을 반환합니다:
> "안심 거래 및 실시간 이상거래 탐지 관련 내용이 아니라 답변을 드릴 수 없습니다."

## 환경 변수

| 변수명 | 설명 | 기본값 |
|--------|------|--------|
| `LLM_MODEL` | 사용할 모델 이름 | `gpt-oss-20b` |
| `LLM_API_URL` | LLM 서버 엔드포인트 URL | `http://127.0.0.1:1234/v1/chat/completions` |
| `CORS_ALLOW_ORIGINS` | 허용할 Origin (쉼표 구분) | `http://localhost:8088,http://127.0.0.1:8088` |

## 로컬 모델 서빙 프로그램 및 모델 변경 방법

다른 로컬 모델 서빙 프로그램(Ollama, vLLM 등)이나 다른 모델을 사용하려면 다음 부분을 수정합니다.

### 1. 모델 이름 변경

`12_01/lmstudio_gptoss20b_chat.py`의 150번째 줄:

```python
model_name = os.getenv("LLM_MODEL", "gpt-oss-20b")
```

환경 변수로 설정하거나 기본값을 직접 수정합니다.

```bash
# 환경 변수 사용
export LLM_MODEL="llama-3-8b"
```

### 2. API 엔드포인트 변경

`12_01/lmstudio_gptoss20b_chat.py`의 153~158번째 줄:

```python
api_url_env = os.getenv("LLM_API_URL")
api_url_candidates = [
    "http://127.0.0.1:1234/v1/chat/completions",
    "http://localhost:1234/v1/chat/completions",
]
```

환경 변수로 설정하거나 `api_url_candidates` 리스트를 수정합니다.

```bash
# Ollama 사용 시 (OpenAI 호환 모드)
export LLM_API_URL="http://127.0.0.1:11434/v1/chat/completions"
```

### 3. 요청 파라미터 수정 (필요 시)

`12_01/lmstudio_gptoss20b_chat.py`의 419~423번째 줄에서 `temperature`, `top_p`, `max_tokens` 등을 추가할 수 있습니다:

```python
payload = {
    "model": model_name,
    "messages": messages,
    "stream": True,
    # 필요 시 추가
    # "temperature": 0.7,
    # "max_tokens": 1024,
}
```

### 4. 시스템 프롬프트 수정 (필요 시)

`12_01/lmstudio_gptoss20b_chat.py`의 401~409번째 줄에서 시스템 메시지를 수정할 수 있습니다.

## 의존 라이브러리

챗봇 서버 실행에 필요한 핵심 라이브러리:

- `flask` - 웹 서버 프레임워크
- `requests` - HTTP 클라이언트
- `numpy` - 수치 연산
- `scikit-learn` - TF-IDF 벡터화 및 코사인 유사도 계산

## 데이터 파일 형식

`fds_docs.csv`는 다음 컬럼을 포함해야 합니다:

| 컬럼명 | 설명 |
|--------|------|
| `text` | 사용자가 할 법한 질문 문장 |
| `intent` | 해당 질문에 대한 답변 문장 |

예시:
```csv
text,intent
계좌를 만들려면 어떻게 하나요,약관 동의 후 본인 인증과 정보 입력을 완료하면 계좌가 개설됩니다.
잔액이 부족하면 송금이 되나요,잔액이 부족하면 송금 또는 인출이 실패합니다.
```
