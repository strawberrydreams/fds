package kdt.fds.account.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountRequestDTO {
    private String userId;     // "hong123" (문자열 아이디)
    private String accountNum; // "111-222-333"
    private Double balance;    // 1000000.0
}