package kdt.fds.project.mapper;

import kdt.fds.project.controller.MemberDTO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserMapper {

    // 1. 회원가입 저장 (정상 복구: 이제 가입하는 사람은 'USER' 권한을 가집니다)
    @Insert("INSERT INTO USERS (USER_ID, NAME, USER_PW, USER_EMAIL, BIRTH, GENDER, ROLE, PW_QUESTION, PW_ANSWER) " +
            "VALUES (#{userId}, #{name}, #{userPw}, #{userEmail}, #{birth}, #{gender}, 'USER', #{pwQuestion}, #{pwAnswer})")
    void save(MemberDTO memberDTO);

    // 2. 단일 회원 조회 (아이디 기반)
    @Select("SELECT * FROM USERS WHERE USER_ID = #{userId}")
    MemberDTO findByUserId(String userId);

    // 3. [아이디 찾기] 이름 + 이메일 + 생년월일 확인
    @Select("SELECT USER_ID FROM USERS WHERE NAME = #{name} AND USER_EMAIL = #{userEmail} AND BIRTH = #{birth}")
    String findIdByNameAndEmailAndBirth(@Param("name") String name, @Param("userEmail") String userEmail, @Param("birth") String birth);

    // 4. [비밀번호 찾기] 5단 교차 확인
    @Select("SELECT COUNT(*) FROM USERS WHERE USER_ID = #{userId} AND USER_EMAIL = #{userEmail} " +
            "AND BIRTH = #{birth} AND PW_QUESTION = #{pwQuestion} AND PW_ANSWER = #{pwAnswer}")
    int checkUserExists(@Param("userId") String userId,
                        @Param("userEmail") String userEmail,
                        @Param("birth") String birth,
                        @Param("pwQuestion") String pwQuestion,
                        @Param("pwAnswer") String pwAnswer);

    // 5. 일반 정보 업데이트 (비밀번호/이메일 개별 수정)
    @Update("UPDATE USERS SET USER_PW = #{userPw} WHERE USER_ID = #{userId}")
    void updatePassword(@Param("userId") String userId, @Param("userPw") String userPw);

    @Update("UPDATE USERS SET USER_EMAIL = #{userEmail} WHERE USER_ID = #{userId}")
    void updateEmail(@Param("userId") String userId, @Param("userEmail") String userEmail);

    // --- 관리자(ADMIN) 전용 기능 ---

    // 6. 전체 회원 목록 조회 (이름순 정렬)
    @Select("SELECT * FROM USERS ORDER BY NAME ASC")
    List<MemberDTO> findAllUsers();

    // 7. 관리자용 통합 회원 수정 (이름, 이메일, 생년월일, 성별, 권한 등을 한꺼번에 강제 수정)
    @Update("UPDATE USERS SET NAME=#{name}, USER_EMAIL=#{userEmail}, BIRTH=#{birth}, GENDER=#{gender}, ROLE=#{role} " +
            "WHERE USER_ID = #{userId}")
    void updateUserByAdmin(MemberDTO memberDTO);

    // 8. 권한(ROLE)만 강제 변경 (USER <-> ADMIN)
    @Update("UPDATE USERS SET ROLE = #{role} WHERE USER_ID = #{userId}")
    void updateUserRole(@Param("userId") String userId, @Param("role") String role);

    // 9. 회원 강제 삭제 (탈퇴 처리)
    @Delete("DELETE FROM USERS WHERE USER_ID = #{userId}")
    void deleteUser(String userId);
}