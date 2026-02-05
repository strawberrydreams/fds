/**
 * 실무형 공통 유틸리티 모듈
 * 브라우저 표준 Intl API를 사용하여 가독성과 정확성을 높였습니다.
 */
const Utils = {
    /**
     * 날짜 포맷팅 (YYYY-MM-DD HH:mm:ss)
     * Intl.DateTimeFormat을 사용하여 수동 문자열 계산을 제거했습니다.
     */
    formatDateTime(ts) {
        if (!ts) return "-";
        const date = new Date(ts);

        // 날짜가 유효하지 않을 경우 원본 반환
        if (isNaN(date.getTime())) return ts;

        return new Intl.DateTimeFormat('ko-KR', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hour12: false // 24시간 형식
        }).format(date).replace(/\. /g, '-').replace(/\./g, '');
        // 결과 예: 2024-05-20 14:30:05
    },

    /**
     * 금액 포맷팅 (Intl.NumberFormat 활용)
     * 단순히 toLocaleString만 쓰는 것보다 통화 단위 처리에 더 안전합니다.
     */
    formatCurrency(amount) {
        return new Intl.NumberFormat('ko-KR', {
            style: 'decimal', // 통화 기호 제외 시 decimal 사용
            maximumFractionDigits: 0
        }).format(amount || 0) + '원';
    }
};