package kdt.fds.project.controller;

import kdt.fds.project.dto.MemberDTO;
import kdt.fds.project.entity.Account;
import kdt.fds.project.entity.BlacklistAccount;
import kdt.fds.project.entity.FraudDetectionResult;
import kdt.fds.project.entity.Transaction;
import kdt.fds.project.mapper.UserMapper;
import kdt.fds.project.repository.AccountRepository;
import kdt.fds.project.repository.BlacklistRepository;
import kdt.fds.project.repository.FraudRepository;
import kdt.fds.project.repository.TransactionRepository;
import kdt.fds.project.service.AdminService;
import kdt.fds.project.service.TransactionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final BlacklistRepository blacklistRepository;
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final FraudRepository fraudRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    // ==========================================
    // 1. 관리자 뷰(View) 페이지 - 주소: /admin/dashboard
    // ==========================================
    @GetMapping("/admin/dashboard")
    public String adminDashboard() {
        log.info("관리자 대시보드 뷰 호출");
        return "admin/dashboard"; // templates/admin/dashboard.html 호출
    }

    @GetMapping("/admin/users")
    public String userList(Model model) {
        List<MemberDTO> users = userMapper.findAllUsers();
        model.addAttribute("users", users);
        return "admin/user_list";
    }

    // ==========================================
    // 2. 관리자 데이터 API - 주소: /api/v1/admin/**
    // ==========================================
    @GetMapping("/api/v1/admin/history")
    @ResponseBody
    public ResponseEntity<List<Transaction>> getTransactionHistory() {
        return ResponseEntity.ok(transactionRepository.findAll());
    }

    @GetMapping("/api/v1/admin/blacklist")
    @ResponseBody
    public ResponseEntity<List<BlacklistAccount>> getBlacklist() {
        return ResponseEntity.ok(blacklistRepository.findAll());
    }

    @GetMapping("/api/v1/admin/accounts")
    @ResponseBody
    public ResponseEntity<List<Account>> getAllAccounts() {
        return ResponseEntity.ok(accountRepository.findAll());
    }

    @PostMapping("/api/v1/admin/approve/{id}")
    @ResponseBody
    public ResponseEntity<String> approveTransaction(@PathVariable("id") Long id) {
        FraudDetectionResult fraudResult = fraudRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("탐지 결과 없음"));
        fraudResult.setIsFraud(0);
        fraudRepository.save(fraudResult);

        // 실제 거래 승인 로직 포함 (기존 코드 유지)
        Transaction tx = transactionRepository.findById(fraudResult.getTxId()).orElseThrow();
        Account sender = accountRepository.findByAccountNumber(tx.getSourceValue()).orElseThrow();
        transactionService.executeTransfer(tx, sender);

        return ResponseEntity.ok("SUCCESS");
    }

    @PostMapping("/api/v1/admin/reject/{id}")
    @ResponseBody
    public ResponseEntity<String> reject(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.rejectTransaction(id));
    }

    @DeleteMapping("/api/v1/admin/blacklist/{accountNum}")
    @ResponseBody
    public ResponseEntity<?> removeBlacklist(@PathVariable String accountNum) {
        adminService.removeBlacklist(accountNum);
        return ResponseEntity.ok("해제 완료");
    }

    @PostMapping("/api/v1/admin/blacklist")
    @ResponseBody
    public ResponseEntity<?> addToBlacklist(@RequestBody Map<String, String> payload) {
        String result = adminService.addToBlacklist(payload.get("accountNum"), payload.get("reason"));
        return ResponseEntity.ok(result);
    }
}