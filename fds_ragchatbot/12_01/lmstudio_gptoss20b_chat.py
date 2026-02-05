# =========================================
# 파일명: lmstudio_gptoss20b_chat.py
# =========================================
"""
[전체 코드 설명]
이 코드는 안심 거래 및 실시간 이상거래 탐지 관련 문서를 fds_docs.csv 파일에서 로드해
TF-IDF 기반 벡터 검색을 수행하고, 그 결과를 RAG 방식으로 OpenAI 호환 로컬 모델(GPT-OSS-20B)에
전달해 답변을 생성하는 Flask 웹 챗봇 예제다.

동작 흐름은 다음과 같다.
1) 서버 시작 시 fds_docs.csv(text,intent 컬럼)를 읽어 documents 리스트를 구성한다.
2) 각 문서의 title(text)과 content(intent)를 합친 문자열로 corpus를 만들고
   tfidfvectorizer(문자 단위 2~4-gram, 한글 공백 제거 전처리)를 사용해 doc_vectors 벡터 인덱스를 구축한다.
3) / 요청에서 templates/fds_chatbot.html 템플릿을 렌더링해 안심 거래 문의 챗봇 화면을 제공한다.
4) /chat 엔드포인트에서 json으로 전달된 사용자 입력(message)을 받아 tf-idf 기반 유사도 검색을 수행하고
   상위 문서들을 rag 프롬프트 문자열로 정리해 build_rag_prompt에서 하나의 prompt로 만든다.
5) stream_chat_completion 함수가 /chat/completions 스트리밍 응답을 읽어
   클라이언트에 토큰 단위로 스트림 전송한다.
6) 안심 거래/이상거래 탐지 문서가 없거나, 검색 결과가 없는 경우에는
   "안심 거래 및 실시간 이상거래 탐지 관련 내용이 아니라 답변을 드릴 수 없습니다."라고 바로 응답하고
   질문을 unanswered_questions.txt에 기록한다.
7) chat_history 리스트에 이전 user/assistant 메시지를 저장하고
   /chat/completions 요청 시 system 메시지와 함께 과거 히스토리를 전달해
   "방금 말한 탐지 기준"과 같은 맥락을 일정 범위 내에서 유지하도록 구현한다.
8) last_doc_title 전역 변수에 최근 tf-idf 검색에서 선택된 문서 제목을 저장하고
   이후 질문에서는 이 제목과 현재 질문 문장을 모두 포함한 쿼리로 검색을 수행해
   "다음 달은?"처럼 앞 대화를 전제로 하는 질문에서도 같은 주제를 이어갈 수 있도록 한다.

- 목적: tf-idf 기반 검색과 OpenAI 호환 /chat/completions 스트리밍을 결합하고
        간단한 대화 히스토리 기능과 주제 유지 힌트를 포함한 실습용
        "안심 거래 및 실시간 이상거래 탐지 문의 챗봇"을 구현한다.
- 입력:
  - http post /chat
  - json body 예: {"message": "이상거래 신고는 어떻게 접수하나요?"}
- 출력:
  - 성공 시 텍스트 스트림 응답(브라우저에서 타이핑처럼 출력)
  - 오류 시 {"error": "..."} 형태의 JSON 오류 메시지
- 실행 방법:
  1) 동일 경로 또는 지정 경로에 fds_docs.csv(text,intent 컬럼)를 준비한다.
  2) 로컬 LLM 서버(LM Studio 등)를 OpenAI 호환 모드로 실행한다.
  3) python lmstudio_gptoss20b_chat.py
  4) 브라우저에서 http://127.0.0.1:8088/chat 접속 후 fds_chatbot.html 화면에서 대화한다.
- 의존 라이브러리:
  - flask
  - requests
  - numpy
  - scikit-learn (sklearn)
  - OpenAI 호환 LLM 서버(GPT-OSS-20B)
  - csv 파일 fds_docs.csv (text,intent 컬럼 필수, 안심 거래/이상거래 탐지 관련 내용)
"""

"""
[사용되는 메서드, 클래스, 함수 목록]

● flask 클래스
flask 패키지에서 제공하며 wsgi 기반 웹 애플리케이션 객체를 생성하는 클래스다.
라우팅, 요청 처리, 템플릿 렌더링 등을 담당하는 웹 서버의 중심 객체다.
● flask(import 이름: Flask)
flask(__name__) 형태로 애플리케이션 인스턴스를 생성한다.

● @app.route 데코레이터
flask 애플리케이션에서 url 경로와 뷰 함수를 매핑하는 데 사용한다.
예를 들어 @app.route("/", methods=["GET"])는 index 함수를 루트 경로 get 요청에 연결한다.

● request.get_json(silent=True)
flask의 request 객체 메서드로 http 요청의 body를 json으로 파싱해 파이썬 딕셔너리로 반환한다.
silent=True이면 파싱 실패 시 예외 대신 None을 반환한다.

● jsonify
flask에서 파이썬 딕셔너리, 리스트 등을 json 응답으로 변환해 주는 헬퍼 함수다.
content-type을 application/json으로 설정해 반환한다.

● render_template
flask에서 templates 폴더 아래의 html 템플릿 파일을 찾아 렌더링하는 함수다.
템플릿 이름과 렌더링에 사용할 변수들을 넘겨 html 응답을 생성한다.

● csv.DictReader
csv 모듈에서 제공되며 csv 파일을 한 줄씩 읽어 각 행을 딕셔너리 형태로 반환한다.
헤더 라인을 키로 사용해 row["text"], row["intent"]처럼 접근할 수 있다.

● tfidfvectorizer 클래스
sklearn.feature_extraction.text 모듈에서 제공되며
문서 집합을 tf-idf(단어 빈도-역문서 빈도) 벡터로 변환하는 도구다.
여기서는 analyzer="char", ngram_range=(2,4)로 문자 단위 2~4-gram tf-idf를 계산한다.
● tfidfvectorizer(preprocessor=None, analyzer="word", ngram_range=(1,1), ...)
fit(corpus) / fit_transform(corpus)로 벡터화를 수행한다.

● cosine_similarity 함수
sklearn.metrics.pairwise 모듈에서 제공되며 두 벡터 집합 간 코사인 유사도를 계산한다.
여기서는 질문 벡터와 doc_vectors 간의 유사도 배열을 구하는 데 사용한다.

● numpy.argsort
numpy 배열에서 원소를 정렬했을 때의 인덱스를 반환하는 함수다.
여기서는 -sims를 argsort해 유사도 내림차순 순위를 구한다.

● requests.post
requests 패키지에서 제공되는 http post 요청 함수다.
requests.post(api_url, json=payload, timeout=120) 형태로 사용해 OpenAI 호환 /chat/completions에 요청을 보낸다.
● response.raise_for_status()
http 상태 코드가 4xx 또는 5xx일 때 예외를 발생시켜 오류를 빠르게 감지한다.
● response.json()
http 응답 본문을 json으로 파싱해 파이썬 딕셔너리로 반환한다.

● normalize_korean_text(text: str) -> str 함수
추가 전처리용 사용자 정의 함수로 문자열 양끝 공백을 제거한 뒤 내부 공백을 모두 삭제한다.
tfidfvectorizer의 preprocessor로 연결해 한글 공백을 통일된 방식으로 처리한다.

● load_documents_from_csv(csv_path: str) 함수
csv 파일에서 text,intent 컬럼을 읽어 {"title": text, "content": intent} 형태의 딕셔너리 리스트를 반환한다.

● build_tfidf_index(docs) 함수
문서 리스트를 받아 title과 content를 합친 텍스트 corpus를 만들고
tfidfvectorizer로 doc_vectors를 fit_transform해 tf-idf 인덱스를 구축한다.

● retrieve_top_docs(query: str, top_k: int) 함수
사용자 질문 query를 tf-idf 벡터로 변환하고 cosine_similarity로 각 문서와의 유사도를 계산한 뒤
상위 top_k개 문서를 (문서 딕셔너리, 유사도) 튜플 리스트로 반환한다.

● format_context(docs_with_scores, min_score: float) 함수
검색된 문서 리스트를 llm 프롬프트에 넣기 좋은 문자열 형태로 정리해 반환한다.
문서별로 [문서 i] 질문:, 내용: 형태로 포맷팅한다.

● build_rag_prompt(user_message: str, retrieval_query: str | None) 함수
retrieve_top_docs와 format_context로 구성된 안심 거래/이상거래 탐지 문서 컨텍스트를 포함해
rag용 시스템 안내 + 규칙 + 문서 요약 + 사용자 질문을 하나의 프롬프트 문자열로 만든다.

● stream_chat_completion(prompt: str) 함수
OpenAI 호환 /chat/completions 스트리밍 응답을 읽어 토큰 단위로 yield한다.

● app.run(host, port, debug) 메서드
flask 개발 서버를 실행하는 메서드다.
host="0.0.0.0"이면 외부 접속을 허용하고, port=5000은 포트 번호, debug=True는 디버그 모드를 의미한다.
"""

# =========================================
# 코드 본문 시작
# =========================================

import csv
import json
import os

import numpy as np
import requests
from flask import Flask, request, jsonify, Response, stream_with_context
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

# 모델 변경 시 모델 이름 수정
model_name = os.getenv("LLM_MODEL", "gpt-oss-20b")

# 모델 변경 시 서빙 서버/포트/엔드포인트 수정
api_url_env = os.getenv("LLM_API_URL")
api_url_candidates = [
    "http://127.0.0.1:1234/v1/chat/completions",
    "http://localhost:1234/v1/chat/completions",
]
api_urls = [api_url_env] if api_url_env else api_url_candidates

cors_env = os.getenv("CORS_ALLOW_ORIGINS")
if cors_env:
    allowed_origins = {origin.strip() for origin in cors_env.split(",") if origin.strip()}
else:
    allowed_origins = {
        "http://localhost:8088",
        "http://127.0.0.1:8088",
    }

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
csv_path = os.path.join(BASE_DIR, "fds_docs.csv")
unanswered_path = os.path.join(BASE_DIR, "unanswered_questions.txt")

app = Flask(__name__)

chat_history = []          # [{"role": "user", "content": ...}, {"role": "assistant", "content": ...}]
last_doc_title = None      # 최근 선택된 대표 문서 제목

@app.after_request
def add_cors_headers(response):
    origin = request.headers.get("Origin")
    if origin in allowed_origins:
        response.headers["Access-Control-Allow-Origin"] = origin
        response.headers["Vary"] = "Origin"
        response.headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
        response.headers["Access-Control-Allow-Headers"] = "Content-Type"
        response.headers["Access-Control-Max-Age"] = "3600"
    return response

# ----------------------------------------
# 0단계: 한글 전처리 함수
# ----------------------------------------

def normalize_korean_text(text: str) -> str:
    """
    tf-idf에 넣기 전에 한글 텍스트를 정규화한다.
    - 양쪽 공백을 제거한 뒤 모든 공백을 삭제한다.
    - 예) '이상 거래 신고' -> '이상거래신고'
    """
    if not text:
        return ""
    return text.strip().replace(" ", "")

def extract_core_keyword(text: str) -> str:
    """
    질문에서 핵심 한글 키워드를 추출한다.
    - 공백 제거 후 한글만 남긴다.
    - 첫 번째 조사(은,는,이,가,을,를,에,에서,으로,로,와,과,도,만) 앞까지 자른다.
    """
    norm = normalize_korean_text(text)
    hangul_only = "".join(ch for ch in norm if "가" <= ch <= "힣")
    if not hangul_only:
        return ""

    postpositions = {"은", "는", "이", "가", "을", "를", "에", "에서", "으로", "로", "와", "과", "도", "만"}
    cut_idx = None
    for i, ch in enumerate(hangul_only):
        if ch in postpositions:
            cut_idx = i
            break

    if cut_idx is not None and cut_idx >= 1:
        return hangul_only[:cut_idx]
    return hangul_only

def log_unanswered_question(message: str) -> None:
    if not message:
        return
    try:
        with open(unanswered_path, "a", encoding="utf-8") as f:
            f.write(message + "\n")
    except Exception as e:
        print(f"[경고] 질문 저장 실패: {e}")

# ----------------------------------------
# 1단계: 안심 거래/이상거래 탐지 문서 (csv 사용)
# ----------------------------------------

documents = []      # [{ "title": ..., "content": ... }, ...]
corpus = []         # tf-idf 학습용 순수 텍스트 리스트
vectorizer = None   # tfidfvectorizer 인스턴스
doc_vectors = None  # 문서 벡터 행렬

def load_documents_from_csv(path: str):
    """fds_docs.csv(text,intent)에서 안심 거래/이상거래 탐지 문서를 읽어 documents 리스트를 만든다."""
    docs = []
    with open(path, newline="", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        for row in reader:
            text = (row.get("text") or "").strip()
            intent = (row.get("intent") or "").strip()
            if not text or not intent:
                continue

            normalized = normalize_korean_text(text + intent)
            docs.append({
                "title": text,
                "content": intent,
                "normalized": normalized,
            })

    if not docs:
        raise ValueError("CSV에서 유효한 문서를 하나도 읽지 못했다.")
    return docs

def build_tfidf_index(docs):
    """
    2단계: 벡터 검색 준비 단계다.
    """
    local_corpus = []
    for doc in docs:
        text = (doc["title"] + "\n" + doc["content"]).strip()
        local_corpus.append(text)

    local_vectorizer = TfidfVectorizer(
        preprocessor=normalize_korean_text,
        analyzer="char",
        ngram_range=(2, 4),
    )
    local_doc_vectors = local_vectorizer.fit_transform(local_corpus)
    return local_corpus, local_vectorizer, local_doc_vectors

try:
    documents = load_documents_from_csv(csv_path)
    corpus, vectorizer, doc_vectors = build_tfidf_index(documents)
    print(f"[정보] CSV에서 {len(documents)}개의 안심 거래/이상거래 탐지 문서를 불러왔다.")
except Exception as e:
    print(f"[경고] 지식 베이스 초기화 실패: {e}")
    documents = []
    corpus = []
    vectorizer = None
    doc_vectors = None

# ----------------------------------------
# 2단계: 벡터 검색
# ----------------------------------------

def has_keyword_in_docs(query: str) -> bool:
    keyword = extract_core_keyword(query)
    if not keyword:
        return True
    for doc in documents:
        if keyword in doc.get("normalized", ""):
            return True
    return False

def retrieve_top_docs(query: str, top_k: int = 3):
    """
    사용자 질문에 가장 가까운 문서 top_k개를 (문서, 유사도) 형태로 반환한다.
    """
    if not vectorizer or doc_vectors is None:
        return []
    if not has_keyword_in_docs(query):
        return []

    query_vec = vectorizer.transform([query])
    sims = cosine_similarity(query_vec, doc_vectors)[0]

    ranked_indices = np.argsort(-sims)
    results = []
    for idx in ranked_indices[:top_k]:
        results.append((documents[idx], float(sims[idx])))
    return results

def format_context(docs_with_scores, min_score: float = 0.0) -> tuple[str, bool]:
    """
    검색된 문서를 llm 프롬프트에 넣기 좋은 문자열로 변환한다.
    """
    if not docs_with_scores:
        return "관련된 안심 거래/이상거래 탐지 문서를 찾지 못했다.", False

    lines = []
    has_valid = False
    for i, (doc, score) in enumerate(docs_with_scores, start=1):
        if score < min_score:
            continue
        has_valid = True
        title = doc["title"] or f"문서 {i}"
        content = doc["content"] or ""
        lines.append(f"[문서 {i}] 질문: {title}")
        lines.append(f"내용: {content}")
        lines.append("")
    if not lines:
        return "관련된 안심 거래/이상거래 탐지 문서를 찾지 못했다.", False
    return "\n".join(lines), has_valid

def build_rag_prompt(user_message: str, retrieval_query: str | None = None) -> tuple[str, bool]:
    """
    tf-idf 기반 벡터 검색 결과를 이용해 rag용 프롬프트를 만든다.
    """
    global last_doc_title

    query_for_search = retrieval_query or user_message
    top_docs = retrieve_top_docs(query_for_search, top_k=5)

    context_text, has_valid_context = format_context(top_docs, min_score=0.05)

    if has_valid_context and top_docs:
        last_doc_title = top_docs[0][0].get("title") or last_doc_title

    # 현재 주제를 알려주는 한 줄을 추가한다.
    topic_line = ""
    if last_doc_title:
        topic_line = (
            f"현재 대화의 주요 주제는 '{last_doc_title}'에 관한 안심 거래·이상거래 탐지 안내다.\n"
            "짧은 질문이라도 이 주제를 기준으로 해석해야 한다.\n\n"
        )

    prompt = (
        "당신은 안심 거래 및 실시간 이상거래 탐지 관련 일반 사용자 문의에 답하는 챗봇이다.\n"
        "fds_docs.csv에 담긴 안내 문서 내용만 참고해 답변해야 한다.\n"
        "이 문서에는 거래 보류/승인 안내, 이상거래 탐지 기준, 신고·차단 절차, 사용자 유의사항이 정리되어 있다.\n\n"
        "CSV 파일 구조는 다음과 같다.\n"
        "- text   : 일반 사용자가 실제로 할 법한 질문이나 문장이다.\n"
        "- intent : 해당 질문에 대한 안내 답변 문장이다.\n\n"
        f"{topic_line}"
        "다음 규칙을 반드시 지킨다.\n"
        "1) 아래 문서에 있는 text,intent 내용만 바탕으로 한국어로 답한다.\n"
        "2) intent 컬럼이 라벨처럼 보이면 그 라벨 이름을 활용해 분류 결과를 설명한다.\n"
        "3) intent 컬럼이 전체 문장인 경우 그 내용을 기반으로 FAQ 답변처럼 친절하게 설명한다.\n"
        "4) 문서에 관련 정보가 없거나 주제와 무관한 질문이면 "
        "'안심 거래 및 실시간 이상거래 탐지 관련 내용이 아니라 답변을 드릴 수 없습니다.'라고만 말한다.\n"
        "5) 개인 계좌번호, 비밀번호, 인증번호 등 민감 정보 요청이나 실제 거래 실행/차단 해제 같은 조치 요청은 "
        "문서 기반 안내 범위를 벗어나므로 위 문장을 말한다.\n"
        "6) 문서에 있는 내용은 바꾸지 말고 의미를 유지하면서 필요하면 자연스럽게 정리해서 말한다.\n\n"
        "# 안심 거래 안내 문서에서 검색된 내용\n"
        f"{context_text}\n\n"
        "# 사용자 질문\n"
        f"{user_message}\n\n"
        "# 답변\n"
    )
    return prompt, has_valid_context

# ----------------------------------------
# OpenAI 호환 호출 부분
# ----------------------------------------

def stream_chat_completion(prompt: str):
    """
    OpenAI 호환 /chat/completions 스트리밍 응답을 토큰 단위로 yield한다.
    """
    messages = [
        {
            "role": "system",
            "content": (
                "너는 fds_docs.csv를 기반으로 안심 거래 및 실시간 이상거래 탐지에 대해 "
                "일반 사용자가 이해하기 쉽게 안내하는 챗봇이다. 개인정보를 요구하지 말고, "
                "주제와 무관한 일반 대화에는 '안심 거래 및 실시간 이상거래 탐지 관련 내용이 아니라 답변을 드릴 수 없습니다.'라고만 답해야 한다."
            ),
        }
    ]

    if chat_history:
        messages.extend(chat_history[-6:])

    messages.append({"role": "user", "content": prompt})

    # 모델별 요청 인터페이스 및 시스템 프롬프트 수정
    # temperature/top_p/max_tokens 등 필요 시 추가
    payload = {
        "model": model_name,
        "messages": messages,
        "stream": True,
    }

    last_error = None
    for url in api_urls:
        try:
            resp = requests.post(url, json=payload, stream=True, timeout=120)
            resp.raise_for_status()
        except Exception as exc:
            last_error = exc
            continue

        for line in resp.iter_lines(decode_unicode=False):
            if not line:
                continue

            # OpenAI 호환 스트림은 "data: {json}" 형태로 온다.
            if line.startswith(b"data:"):
                data_bytes = line[len(b"data:"):].strip()
            else:
                data_bytes = line.strip()

            if data_bytes == b"[DONE]":
                break

            try:
                data = json.loads(data_bytes.decode("utf-8"))
            except Exception:
                continue

            choices = data.get("choices", [])
            if not choices:
                continue

            delta = choices[0].get("delta", {}) or {}
            token = delta.get("content")
            if token:
                yield token
                continue

            message = choices[0].get("message", {}) or {}
            full_content = message.get("content")
            if full_content:
                yield full_content
                break

        return

    if last_error:
        raise last_error

# ----------------------------------------
# 라우트
# ----------------------------------------

@app.route("/chat", methods=["POST", "OPTIONS"])
def chat():
    global last_doc_title

    if request.method == "OPTIONS":
        return Response(status=204)

    data = request.get_json(silent=True) or {}
    user_message = data.get("message", "").strip()

    if not user_message:
        return jsonify({"error": "메시지가 비어 있다."}), 400

    if not documents:
        return jsonify({"error": "안심 거래/이상거래 탐지 문서 지식 베이스가 초기화되지 않았다."}), 500

    try:
        # 1) 항상 "현재 주제 제목 + 이번 질문"을 함께 검색 쿼리로 사용한다.
        #    첫 질문 전에는 last_doc_title이 없으므로 user_message만 사용된다.
        if last_doc_title:
            retrieval_query = f"{last_doc_title} {user_message}"
        else:
            retrieval_query = user_message

        # 2) rag 프롬프트 생성
        rag_prompt, has_valid_context = build_rag_prompt(user_message, retrieval_query=retrieval_query)

        if not has_valid_context:
            answer = "안심 거래 및 실시간 이상거래 탐지 관련 내용이 아니라 답변을 드릴 수 없습니다."
            log_unanswered_question(user_message)

            def generate_fixed():
                yield answer
                chat_history.append({"role": "user", "content": user_message})
                chat_history.append({"role": "assistant", "content": answer})
                if len(chat_history) > 10:
                    del chat_history[:-10]

            resp = Response(stream_with_context(generate_fixed()), mimetype="text/plain; charset=utf-8")
            resp.headers["Cache-Control"] = "no-cache"
            resp.headers["X-Accel-Buffering"] = "no"
            return resp

        def generate_stream():
            full_answer = ""
            try:
                for token in stream_chat_completion(rag_prompt):
                    full_answer += token
                    yield token
                if not full_answer:
                    full_answer = "서버 오류로 답변 생성에 실패했다."
                    yield full_answer
            except Exception:
                full_answer = "서버 오류로 답변 생성에 실패했다."
                yield full_answer
            finally:
                if full_answer:
                    chat_history.append({"role": "user", "content": user_message})
                    chat_history.append({"role": "assistant", "content": full_answer})
                    if len(chat_history) > 10:
                        del chat_history[:-10]

        resp = Response(stream_with_context(generate_stream()), mimetype="text/plain; charset=utf-8")
        resp.headers["Cache-Control"] = "no-cache"
        resp.headers["X-Accel-Buffering"] = "no"
        return resp
    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == "__main__":
    # Apple Airplay(5000)와의 충돌을 피하기 위해 5001번 포트 사용
    app.run(host="0.0.0.0", port=5001, debug=True)
