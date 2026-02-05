document.addEventListener("DOMContentLoaded", function () {

    const form = document.getElementById("sign_client");
    if (!form) return;

    form.addEventListener("submit", function (e) {

        const mode = form.dataset.mode; // insert or update

        const userId = form.querySelector("[name='userId']");
        const userPw = form.querySelector("[name='userPw']");
        const name = form.querySelector("[name='name']");
        const email = form.querySelector("[name='email']");
        const gender = form.querySelector("input[name='gender']:checked");
        const birth = form.querySelector("[name='birth']");

        // =========================
        // 아이디
        // =========================
        if (!userId || userId.value.trim() === "") {
            alert("아이디를 입력하세요.");
            userId.focus();
            e.preventDefault();
            return;
        }

        // =========================
        // 비밀번호 (등록일 때만 필수)
        // =========================
        if (mode === "insert") {
            if (!userPw || userPw.value.trim() === "") {
                alert("비밀번호를 입력하세요.");
                userPw.focus();
                e.preventDefault();
                return;
            }
        }

        // =========================
        // 이름
        // =========================
        if (!name || name.value.trim() === "") {
            alert("이름을 입력하세요.");
            name.focus();
            e.preventDefault();
            return;
        }

        // =========================
        // 이메일
        // =========================
        if (!email || email.value.trim() === "") {
            alert("이메일을 입력하세요.");
            email.focus();
            e.preventDefault();
            return;
        }

        // =========================
        // 성별
        // =========================
        if (!gender) {
            alert("성별을 선택하세요.");
            e.preventDefault();
            return;
        }

        // =========================
        // 생년월일 (등록일 때만 필수)
        // =========================
        if (mode === "insert") {
            if (!birth || birth.valueAsDate === null) {
                alert("생년월일을 입력하세요.");
                birth.focus();
                e.preventDefault();
                return;
            }
        }

        // =========================
        // 아이디 중복 체크
        // =========================

        const clientNoInput = form.querySelector("[name='clientNo']");
        const clientNo = clientNoInput ? clientNoInput.value : "";

        let isDuplicated = false;

        const xhr = new XMLHttpRequest();
        xhr.open(
            "GET",
            "/ClientCheckUserId?userId=" + encodeURIComponent(userId.value) + "&clientNo=" + clientNo,
            false  // 동기 방식 (검사 끝날 때까지 submit 멈춤)
        );
        xhr.send(null);

        if (xhr.status === 200) {
            // =========================
            // 서버는 true / false 를 리턴함
            // true = 중복
            // =========================
            if (xhr.responseText === "true") {
                isDuplicated = true;
            }
        }

        if (isDuplicated) {
            alert("이미 사용 중인 아이디입니다.");
            userId.focus();
            userId.value = ""; // 입력한 아이디 삭제
            e.preventDefault();
            return;
        }
    });
});