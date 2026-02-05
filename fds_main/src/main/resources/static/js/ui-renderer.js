/**
 * UI 렌더링 모듈 v6.6 (Updated)
 * - JSON 피처 데이터 파싱 추가 (잔액, 위치 표시)
 * - 신고하기 버튼 추가
 * - 모듈화를 위해 export 키워드 추가
 */
export const UiRenderer = { // [변경] export를 붙여서 다른 파일에서 쓸 수 있게 함

    // 1. 탐지 이력 테이블 (전체 거래 내역)
    renderHistory(data) {
        const historyBody = document.getElementById('history-list');
        if (!historyBody || !data) return;

        if (data.length === 0) {
            historyBody.innerHTML = '<tr><td colspan="9" class="text-center py-4 text-muted">거래 내역이 없습니다.</td></tr>';
            return;
        }

        historyBody.innerHTML = data.map(item => {
            const isFraudStatus = Number(item.isFraud) === 1;
            const badgeClass = isFraudStatus ? 'bg-danger-subtle text-danger border border-danger' : 'bg-success-subtle text-success border border-success';
            const statusText = isFraudStatus ? '🚨 사기의심' : '✅ 정상거래';

            // 데이터 가공 시 txId가 최우선 순위
            const currentId = item.txId || item.id;

            // [추가] 1. vFeatures(JSON 문자열) 파싱
            let features = {};
            try {
                if (item.vFeatures) {
                    features = JSON.parse(item.vFeatures);
                }
            } catch (e) {
                console.warn("JSON 파싱 에러(무시됨):", e);
            }

            // [추가] 2. 엔진 정보에 따른 뱃지 색상 (Engine A: 파랑, B: 보라)
            const engineInfo = item.engine || '';
            const engineBadge = engineInfo.includes("Engine_A")
                ? '<span class="badge bg-primary bg-opacity-10 text-primary" style="font-size: 0.7em;">Rule</span>'
                : '<span class="badge bg-purple-subtle text-purple border-purple" style="font-size: 0.7em; color: #6f42c1; background-color: #e2d9f3;">AI</span>';

            return `
            <tr class="${isFraudStatus ? 'table-light' : ''}">
                <td class="ps-4 small text-muted">${Utils.formatDateTime(item.txTimestamp)}</td> 
                <td>
                    <div class="fw-bold">${item.userName || '미등록'}</div>
                    <div class="text-secondary small" style="font-size: 0.75rem;">${item.userId || '-'}</div>
                </td>
                <td>
                    <div class="small">${item.sourceValue || '-'}</div>
                    <div class="text-muted" style="font-size: 0.7rem;">
                        잔액: ${features.old_bal ? Utils.formatCurrency(features.old_bal) : '-'}
                    </div>
                </td>
                <td>
                    <div class="small">${item.targetValue || '-'}</div>
                    <div class="text-muted" style="font-size: 0.7rem;">
                        📍 ${features.loc || '위치정보 없음'}
                    </div>
                </td>
                <td class="fw-bold text-dark">${Utils.formatCurrency(item.txAmount)}</td> 
                <td>
                    <div class="progress" style="height: 6px; width: 50px; display: inline-block; vertical-align: middle; margin-right: 5px;">
                        <div class="progress-bar ${isFraudStatus ? 'bg-danger' : 'bg-success'}" 
                             role="progressbar" 
                             style="width: ${((item.probability || 0) * 100)}%"></div>
                    </div>
                    <span class="small">${((item.probability || 0) * 100).toFixed(1)}%</span>
                    <div class="mt-1">${engineBadge}</div>
                </td>
                <td>
                    <span class="badge rounded-pill ${badgeClass}" style="font-size: 0.75rem; padding: 5px 10px;">
                        ${statusText}
                    </span>
                    <div class="text-secondary small mt-1" style="font-size: 0.65rem;">
                        ${item.engine ? item.engine.replace(/\[.*?\]/, '').trim() : ''}
                    </div>
                </td>
                <td class="text-center">
                    <div class="btn-group">
                        <button class="btn btn-sm btn-outline-warning border-0 btn-report" 
                                title="사기 신고"
                                data-txid="${currentId}"
                                data-account="${item.targetValue}">
                            🚨
                        </button>
                        <button class="btn btn-sm btn-outline-danger border-0" 
                                title="삭제"
                                onclick="UiHandler.handleDeleteItem(${currentId})">
                            <i class="bi bi-trash"></i>
                        </button>
                    </div>
                </td>
            </tr>
        `;
        }).join('');
    },





    // 2. 승인 대기 목록 (사기 의심 거래 격리 구역)
    renderPending(data) {
        const pendingBody = document.getElementById('pending-list');
        if (!pendingBody) return;

        // [핵심 변경]
        // 1. isFraud가 1이어야 함 (사기 의심)
        // 2. 그리고(AND) engine 메시지에 "관리자" 또는 "거절"이라는 단어가 없어야 함
        //    (관리자가 이미 처리한 건은 목록에서 제외)
        const pendingData = data.filter(item => {
            const isFraud = Number(item.isFraud) === 1;
            const engineMsg = item.engine || "";
            // "관리자"가 포함되어 있으면 이미 처리된 것임 -> 제외 (!)
            const isProcessed = engineMsg.includes("관리자") || engineMsg.includes("거절");

            return isFraud && !isProcessed;
        });

        if (pendingData.length === 0) {
            pendingBody.innerHTML = `<tr><td colspan="5" class="text-center py-5 text-muted">심사 대기 중인 거래가 없습니다.</td></tr>`;
            return;
        }

        pendingBody.innerHTML = pendingData.map(item => {
            const currentId = item.txId || item.id;
            const displayReason = item.engine || "분석 완료 대기";
            const prob = (item.probability || 0) * 100;

            return `
            <tr class="table-warning">
                <td class="ps-4">#${currentId}</td>
                <td><strong class="text-danger">${displayReason}</strong></td>
                <td><span class="badge bg-danger">${prob.toFixed(1)}%</span></td>
                <td><span class="badge bg-warning text-dark">송금 격리중</span></td>
                <td class="text-center">
                    <div class="btn-group shadow-sm">
                        <button class="btn btn-sm btn-success px-3" onclick="UiHandler.approveTx(${currentId})">승인</button>
                        <button class="btn btn-sm btn-danger px-3" onclick="UiHandler.rejectTx(${currentId})">거절</button>
                    </div>
                </td>
            </tr>
            `;
        }).join('');
    },

    // 3. 차단 목록 (블랙리스트 관리)
    renderBlacklist(data) {
        const mgmtBody = document.getElementById('management-list');
        if (!mgmtBody) return;

        if (!data || data.length === 0) {
            mgmtBody.innerHTML = `<tr><td colspan="5" class="text-center py-5 text-muted">등록된 블랙리스트 내역이 없습니다.</td></tr>`;
            return;
        }

        mgmtBody.innerHTML = data.map(item => `
            <tr>
                <td class="ps-4">#${item.blacklistId || item.id}</td> 
                <td><span class="badge bg-danger">영구 차단</span></td>
                <td><strong class="text-danger">${item.accountNum}</strong></td>
                <td><span class="badge bg-dark">${item.reason || '관리자 수동 거절'}</span></td>
                <td class="text-center">
                    <button class="btn btn-sm btn-outline-success" onclick="UiHandler.removeFromBlacklist('${item.accountNum}')">차단 해제</button>
                </td>
            </tr>
        `).join('');
    },

    // 4. 계좌 현황 목록
    renderAccounts(accounts) {
        const accountBody = document.getElementById('account-list');
        if (!accountBody) return;

        accountBody.innerHTML = accounts.map(acc => `
            <tr>
                <td class="ps-4"><strong>${acc.user ? acc.user.userName : '미등록 사용자'}</strong></td>
                <td>${acc.accountNum}</td>
                <td class="text-primary fw-bold">${Utils.formatCurrency(acc.balance)}</td>
                <td class="text-center"><span class="badge bg-success">활성</span></td>
            </tr>
        `).join('');
    },
    renderReportRanking(reports) {
        // 1. HTML 요소 가져오기
        const emptyMsg = document.getElementById('ranking-empty-msg');
        const card = document.getElementById('ranking-card');
        const tbody = document.getElementById('ranking-table-body');

        // 요소가 없으면(다른 페이지 등) 중단
        if (!emptyMsg || !card || !tbody) return;

        // 2. 데이터가 없을 때 처리
        if (!reports || reports.length === 0) {
            emptyMsg.classList.remove('d-none'); // 메시지 보이기
            card.classList.add('d-none');        // 카드 숨기기
            return;
        }

        // 3. 데이터가 있을 때 처리
        emptyMsg.classList.add('d-none');    // 메시지 숨기기
        card.classList.remove('d-none');     // 카드 보이기

        // 행(Row) 생성 로직만 남음
        const rows = reports.map((item, index) => {
            const isRisky = item.reportCount >= 3;
            const badgeClass = isRisky ? 'bg-danger' : 'bg-warning text-dark';

            return `
            <tr>
                <td class="text-center fw-bold">${index + 1}</td>
                <td>
                    <span class="fw-bold text-primary">${item.reportedAccount}</span>
                    ${isRisky ? '<span class="badge bg-danger ms-1">위험</span>' : ''}
                </td>
                <td class="text-center">
                    <span class="badge ${badgeClass} rounded-pill">
                        🚨 ${item.reportCount}회
                    </span>
                </td>
                <td><small class="text-muted">${item.reason}</small></td>
                <td class="text-center">
                    <button class="btn btn-sm btn-dark btn-blacklist-add" 
                            data-account="${item.reportedAccount}">
                        ⛔ 차단
                    </button>
                </td>
            </tr>
            `;
        }).join('');

        // 4. <tbody> 안에 HTML 주입
        tbody.innerHTML = rows;
    }
};