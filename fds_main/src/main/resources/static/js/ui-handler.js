// [1] api.js에서 필요한 함수들을 가져옵니다.
import { FdsApi, createReport } from './api.js';

// [2] export를 붙여서 다른 파일(main.js 등)에서 사용할 수 있게 합니다.
export const UiHandler = {
    isInitialized: false, // 중복 초기화 방지 플래그

    // 1. 거래 승인 처리
    async approveTx(id) {
        if (!confirm("이 거래를 승인하시겠습니까?")) return;
        try {
            const msg = await FdsApi.approveTransaction(id);
            alert(msg);
            if (typeof UiMain !== 'undefined') UiMain.refreshAll();
        } catch (e) { alert("승인 오류: " + e.message); }
    },

    // 2. 거래 거절 및 블랙리스트 등록
    async rejectTx(id) {
        if (!confirm("거래를 거절하고 수취인 계좌를 블랙리스트에 등록하시겠습니까?")) return;
        try {
            const msg = await FdsApi.rejectTransaction(id);
            alert(msg);
            if (typeof UiMain !== 'undefined') UiMain.refreshAll();
        } catch (e) { alert("거절 오류: " + e.message); }
    },

    // 3. 블랙리스트 해제
    async removeFromBlacklist(accountNum) {
        if (!confirm(`계좌 [${accountNum}]의 차단을 해제하시겠습니까?`)) return;
        try {
            const msg = await FdsApi.removeBlacklist(accountNum);
            alert(msg);
            if (typeof UiMain !== 'undefined') UiMain.refreshAll();
        } catch (e) { alert("해제 오류: " + e.message); }
    },

    // 4. 정책 설정 통합 저장
    async saveAllConfigs() {
        const thresholdEl = document.getElementById('threshold-range');
        const amountEl = document.getElementById('auto-amount-input');

        if (!thresholdEl || !amountEl) {
            alert("설정 항목을 찾을 수 없습니다.");
            return;
        }

        const rawThreshold = thresholdEl.value;
        const thresholdVal = (rawThreshold / 100).toFixed(2);
        const amountVal = amountEl.value;

        if (!amountVal) {
            alert("자동 승인 기준 금액을 입력해주세요.");
            return;
        }

        const btn = document.getElementById('btn-save-policy');
        if(btn) {
            btn.disabled = true;
            btn.innerText = "저장 중...";
        }

        try {
            await Promise.all([
                FdsApi.updateConfig('THRESHOLD', thresholdVal),
                FdsApi.updateConfig('AUTO_LIMIT', amountVal)
            ]);

            alert(`✅ 모든 정책 설정이 성공적으로 저장되었습니다.\n(임계치: ${thresholdVal}, 금액: ${amountVal})`);
            if (typeof UiMain !== 'undefined' && UiMain.refreshAll) {
                UiMain.refreshAll();
            }
        } catch (e) {
            alert("❌ 저장 실패: " + e.message);
        } finally {
            if(btn) {
                btn.disabled = false;
                btn.innerText = "정책 설정 반영하기";
            }
        }
    },

    // 5. 기록 삭제 처리
    async handleDeleteItem(id) {
        if (!id) { alert("ID가 유효하지 않습니다."); return; }
        if (!confirm("정말 삭제하시겠습니까?")) return;

        try {
            const msg = await FdsApi.deleteHistory(id);
            alert(msg);
            if (typeof UiMain !== 'undefined') UiMain.refreshAll();
        } catch (e) {
            console.error(e);
            alert("삭제 실패: " + e.message);
        }
    },

    // 6. 이벤트 리스너 초기화
    initEventHandlers() {
        if (this.isInitialized) return;
        this.isInitialized = true;

        console.log("✅ UiHandler 이벤트 리스너 초기화됨");

        document.body.addEventListener('click', (e) => {
            // 1. [차단 버튼]
            const blockBtn = e.target.closest('.btn-blacklist-add');
            if (blockBtn) {
                const account = blockBtn.dataset.account;
                if (account) this.handleAddToBlacklist(account);
                return;
            }

            // 2. [로그아웃 버튼]
            const logoutBtn = e.target.closest('#btn-logout');
            if (logoutBtn) {
                if(confirm("로그아웃 하시겠습니까?")) {
                    // [수정] login.html이 아닌 /logout (Spring Security 경로)으로 보냅니다.
                    window.location.href = '/logout';
                }
            }

            // 3. [정책 저장 버튼]
            const savePolicyBtn = e.target.closest('#btn-save-policy');
            if (savePolicyBtn) {
                this.saveAllConfigs();
            }
        });

        document.body.addEventListener('input', (e) => {
            if (e.target.id === 'threshold-range') {
                const val = (e.target.value / 100).toFixed(2);
                const display = document.getElementById('threshold-value-display');
                if (display) display.innerText = val;
            }
        });
    },

    // 7. 블랙리스트 추가
    async handleAddToBlacklist(account) {
        if (!confirm(`[${account}] 계좌를 정말로 차단하시겠습니까?`)) return;

        try {
            const response = await fetch('/api/v1/admin/blacklist', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    accountNum: account,
                    reason: "신고 누적으로 인한 관리자 차단"
                })
            });

            if (response.ok) {
                alert(`✅ [${account}] 차단 완료!`);
                if (typeof UiMain !== 'undefined') UiMain.refreshAll();
            } else {
                const msg = await response.text();
                alert("⚠️ 실패: " + msg);
            }
        } catch (e) {
            alert("❌ 서버 통신 오류");
        }
    }
};