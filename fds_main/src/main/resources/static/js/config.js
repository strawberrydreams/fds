/**
 * 프로젝트 전역 설정 파일
 * API URL 및 상수 값을 여기서 통합 관리합니다.
 */

const API_BASE = "/api/v1/admin"; // [수정] AdminController의 매핑 경로와 일치시킴

export const API_URLS = {
    // 1. 거래 관련 (기존 /api/v1/transactions에서 변경)
    TRANSACTION: `${API_BASE}`,

    // 2. 관리자 기능 (승인/거절/설정)
    ADMIN: `${API_BASE}`,

    // 3. 계좌 조회
    ACCOUNT: `${API_BASE}/accounts`,

    // 4. 사기 탐지 및 기타
    FRAUD: "/api/fraud",
    REPORT: "/api/reports"
};

export const UI_CONSTANTS = {
    REFRESH_INTERVAL: 5000
};