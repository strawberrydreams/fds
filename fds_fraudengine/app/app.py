import joblib
import pandas as pd
from flask import Flask, request, jsonify

app = Flask(__name__)

# 1. 서버 시작 시 AI 모델 로드
model_a = None
model_b = None

try:
    # 경로가 정확한지 확인하세요 (models 폴더 안에 파일이 있어야 함)
    model_a = joblib.load('models/engine_a_card_fraud.pkl')
    model_b = joblib.load('models/engine_b_transfer_fraud.pkl')
    print("✅ AI 엔진 A(카드), B(송금) 로드 성공!")
except Exception as e:
    print(f"❌ 모델 로드 실패!! 에러 내용: {e}")

# 2. 통합 예측 API (스프링 부트에서 호출할 단 하나의 입구)
@app.route('/api/predict', methods=['POST'])
def predict():
    data = request.json
    tx_type = data.get('tx_type', 'TRANSFER') # 기본값 TRANSFER
    amount = data.get('amount', 0)
    old_bal = data.get('old_bal', 0)

    try:
        if tx_type == 'CARD':
            # --- [엔진 A: 카드 거래 탐지] ---
            # 모델 A의 학습 당시 컬럼 순서와 이름을 맞추세요 (예시 기반)
            input_df = pd.DataFrame([{
                'amount': amount,
                'oldbalanceOrg': old_bal,
                'type_CASH_OUT': 0, 'type_TRANSFER': 0, 'type_CARD': 1 # 예시
            }])
            # 필요한 경우 모델 A 전용 컬럼 리스트로 재정렬
            prob = model_a.predict_proba(input_df)[:, 1][0]
            engine_name = "ENGINE_A_CARD"

        else:
            # --- [엔진 B: 계좌 송금 탐지] ---
            input_df = pd.DataFrame([{
                'step': 1,
                'amount': amount,
                'oldbalanceOrg': old_bal,
                'newbalanceOrig': old_bal - amount,
                'oldbalanceDest': 0.0,
                'newbalanceDest': 0.0,
                'errorBalanceOrig': 0.0,
                'errorBalanceDest': 0.0,
                'type_CASH_IN': 0, 'type_CASH_OUT': 0, 'type_DEBIT': 0, 'type_PAYMENT': 0, 'type_TRANSFER': 1
            }])
            # 모델 B 요구 컬럼 순서
            cols = ['step', 'amount', 'oldbalanceOrg', 'newbalanceOrig', 'oldbalanceDest',
                    'newbalanceDest', 'errorBalanceOrig', 'errorBalanceDest', 'type_CASH_IN',
                    'type_CASH_OUT', 'type_DEBIT', 'type_PAYMENT', 'type_TRANSFER']
            input_df = input_df[cols]
            prob = model_b.predict_proba(input_df)[:, 1][0]
            engine_name = "ENGINE_B_TRANSFER"

        # 결과 리턴 (DB 저장은 스프링이 할 것이므로 값만 보냄)
        return jsonify({
            "status": "success",
            "probability": float(prob),
            "engine": engine_name
        })

    except Exception as e:
        print(f"❌ 예측 실패: {e}")
        return jsonify({"error": str(e)}), 500


if __name__ == '__main__':
    # 스프링 부트와 겹치지 않게 5000번 포트 유지
    app.run(port=5001, debug=True)