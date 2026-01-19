import { FdsApi } from './api.js';

export const UiMain = {
    // 화면 초기화 및 데이터 로드
    async init() {
        console.log("대시보드 UI 초기화 시작");
        await this.refreshAll();
    },

    // 모든 데이터를 새로고침하는 함수
    async refreshAll() {
        try {
            // 1. 전체 거래 이력 가져오기
            const allHistory = await FdsApi.fetchHistory();
            console.log("가져온 거래 데이터:", allHistory);

            // 2. 상단 요약 정보 업데이트 (오늘 총 거래 등)
            this.updateSummary(allHistory);

            // 3. 테이블이나 차트가 있다면 여기서 추가 업데이트
            // this.updateTable(allHistory);

        } catch (error) {
            console.error("데이터 갱신 중 오류 발생:", error);
        }
    },

    // 요약 카드 숫자 업데이트
    updateSummary(data) {
        // [핵심] 오늘 총 거래 수 업데이트
        const totalCountElement = document.getElementById('total-transactions'); // HTML의 ID와 일치해야 함

        if (totalCountElement) {
            // 데이터가 배열인지 확인 후 개수 반영
            const count = Array.isArray(data) ? data.length : 0;
            totalCountElement.innerText = count.toLocaleString();
            console.log("화면에 표시될 총 거래 수:", count);
        } else {
            console.error("ID가 'total-transactions'인 요소를 찾을 수 없습니다.");
        }
    }
};

// 페이지 로드 시 실행
document.addEventListener('DOMContentLoaded', () => {
    UiMain.init();
});