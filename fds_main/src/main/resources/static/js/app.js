import { UiMain } from './ui-main.js';
import { UiHandler } from './ui-handler.js';

/**
 * [수정] 자바스크립트 수준의 강제 리다이렉트를 제거했습니다.
 * 스프링 시큐리티 세션을 신뢰하며, 인증되지 않은 접근은 서버가 알아서 차단합니다.
 */

window.UiMain = UiMain;
window.UiHandler = UiHandler;

document.addEventListener('DOMContentLoaded', () => {
    console.log("🚀 FDS Admin App Initialized");

    // 이벤트 핸들러 등록
    UiHandler.initEventHandlers();

    // 메인 UI 초기화 (데이터 로드 시작)
    UiMain.init();
});