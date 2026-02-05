package kdt.fds;

import kdt.fds.user.controller.FormController;
import kdt.fds.user.dto.MemberDTO;
import kdt.fds.transaction.entity.Transaction;
import kdt.fds.user.mapper.UserMapper;
import kdt.fds.account.service.AccountService;
import kdt.fds.card.service.CardService;
import kdt.fds.common.service.FdsRuleEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FdsApplicationTests {

    private PasswordEncoder passwordEncoder;
    private UserMapper userMapper;
    private AccountService accountService;
    private CardService cardService;

    private FormController formController;
    private FdsRuleEngine ruleEngine;

    @BeforeEach
    void setUp() {
        passwordEncoder = mock(PasswordEncoder.class);
        userMapper = mock(UserMapper.class);
        accountService = mock(AccountService.class);
        cardService = mock(CardService.class);

        formController = new FormController(passwordEncoder, userMapper, accountService, cardService);
        ruleEngine = new FdsRuleEngine();
    }

    @Test
    @DisplayName("홈 화면은 index 뷰로 응답한다")
    void homePageReturnsIndexView() {
        Model model = new ExtendedModelMap();
        String viewName = formController.index(null, model);
        assertEquals("index", viewName);
    }

    @Test
    @DisplayName("로그인 사용자명은 모델에 담긴다")
    void homePageAddsLoginUser() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("user123");

        Model model = new ExtendedModelMap();
        formController.index(authentication, model);

        assertEquals("user123", model.getAttribute("loginUser"));
    }

    @Test
    @DisplayName("회원가입은 비밀번호를 인코딩하고 저장한다")
    void joinEncodesPasswordAndSavesUser() {
        MemberDTO member = new MemberDTO();
        member.setUserId("user123");
        member.setUserPw("plainPw");

        when(passwordEncoder.encode("plainPw")).thenReturn("encodedPw");

        String viewName = formController.submitForm(member);
        assertEquals("login/join_result", viewName);

        ArgumentCaptor<MemberDTO> captor = ArgumentCaptor.forClass(MemberDTO.class);
        verify(userMapper).save(captor.capture());

        MemberDTO saved = captor.getValue();
        assertNotNull(saved);
        assertEquals("user123", saved.getUserId());
        assertEquals("encodedPw", saved.getUserPw());
    }

    @Test
    @DisplayName("룰 엔진은 고액 거래를 감지한다")
    void ruleEngineFlagsHighAmount() {
        Transaction tx = Transaction.builder()
                .amount(50_000_000L)
                .build();

        String result = ruleEngine.evaluateRules(tx);
        assertEquals("RULE: HIGH_AMOUNT_LIMIT", result);
    }
}
