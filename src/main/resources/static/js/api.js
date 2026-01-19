import { API_URLS } from './config.js';

export const FdsApi = {

    // 1. 전체 거래 이력 조회 (오늘 총 거래 수 계산의 원천 데이터)
    async fetchHistory() {
        try {
            // 호출 주소: /api/v1/admin/history
            const response = await fetch(`${API_URLS.TRANSACTION}/history`);
            if (!response.ok) throw new Error('이력 로드 실패');
            return await response.json();
        } catch (error) {
            console.error('API Error (fetchHistory):', error);
            return [];
        }
    },

    // 2. 승인 대기 목록 조회
    async fetchFraudOnly() {
        try {
            // 호출 주소: /api/fraud/all (SecurityConfig에서 해당 경로 권한 확인 필요)
            const response = await fetch(`${API_URLS.FRAUD}/all`);
            if (!response.ok) return [];
            return await response.json();
        } catch (error) {
            console.error('API Error (fetchFraudOnly):', error);
            return [];
        }
    },

    // 3. 특정 거래 삭제
    async deleteHistory(id) {
        try {
            // 호출 주소: /api/v1/admin/{id} (AdminController에 DELETE 매핑이 없다면 오류 발생 가능)
            const res = await fetch(`${API_URLS.TRANSACTION}/${id}`, {
                method: 'DELETE'
            });
            if (!res.ok) throw new Error("삭제 실패");
            return await res.text();
        } catch (error) {
            console.error('API Error (deleteHistory):', error);
            throw error;
        }
    },

    // 4. 계좌 목록 조회
    async fetchAccounts() {
        try {
            // 호출 주소: /api/v1/admin/accounts
            const response = await fetch(API_URLS.ACCOUNT);
            if (!response.ok) throw new Error('계좌 로드 실패');
            return await response.json();
        } catch (error) {
            console.error('API Error (fetchAccounts):', error);
            return [];
        }
    },

    // 5. [관리자] 승인
    async approveTransaction(id) {
        try {
            // 호출 주소: /api/v1/admin/approve/{id}
            const response = await fetch(`${API_URLS.ADMIN}/approve/${id}`, {
                method: 'POST'
            });
            if (!response.ok) throw new Error(await response.text());
            return await response.text();
        } catch (error) {
            console.error('API Error (approveTransaction):', error);
            throw error;
        }
    },

    // 6. [관리자] 거절
    async rejectTransaction(id) {
        try {
            // 호출 주소: /api/v1/admin/reject/${id}
            const response = await fetch(`${API_URLS.ADMIN}/reject/${id}`, {
                method: 'POST'
            });
            if (!response.ok) throw new Error(await response.text());
            return await response.text();
        } catch (error) {
            console.error('API Error (rejectTransaction):', error);
            throw error;
        }
    },

    // 7. 블랙리스트 조회
    async fetchBlacklist() {
        try {
            // 호출 주소: /api/v1/admin/blacklist
            const response = await fetch(`${API_URLS.ADMIN}/blacklist`);
            if (!response.ok) throw new Error('블랙리스트 로드 실패');
            return await response.json();
        } catch (error) {
            console.error('API Error (fetchBlacklist):', error);
            return [];
        }
    },

    // 8. 블랙리스트 해제
    async removeBlacklist(accountNum) {
        try {
            // 호출 주소: /api/v1/admin/blacklist/${accountNum}
            const response = await fetch(`${API_URLS.ADMIN}/blacklist/${accountNum}`, {
                method: 'DELETE'
            });
            if (!response.ok) throw new Error('차단 해제 실패');
            return await response.text();
        } catch (error) {
            console.error('API Error (removeBlacklist):', error);
            throw error;
        }
    },

    // 9. 규약 설정 가져오기
    async getAllConfigs() {
        try {
            const response = await fetch(`${API_URLS.ADMIN}/config/all`);
            if (!response.ok) throw new Error('설정 로드 실패');
            return await response.json();
        } catch (error) {
            console.error('API Error (getAllConfigs):', error);
            return [];
        }
    },

    // 10. 규약 설정 업데이트
    async updateConfig(key, value) {
        try {
            const response = await fetch(`${API_URLS.ADMIN}/config/update`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    configKey: key,
                    configValue: value.toString()
                })
            });

            if (!response.ok) throw new Error(await response.text());
            return await response.text();
        } catch (error) {
            console.error('API Error (updateConfig):', error);
            throw error;
        }
    }
};

// [신고하기 함수]
export async function createReport(reportData) {
    try {
        const response = await fetch(API_URLS.REPORT, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(reportData)
        });

        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(errorText);
        }
        return await response.text();
    } catch (error) {
        console.error('신고 API 에러:', error);
        throw error;
    }
}