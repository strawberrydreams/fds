package min.boot.project.mapper;

import min.boot.project.thymeleaf.MemberDTO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {

    // 1. 회원가입 저장
    @Insert("INSERT INTO USERS (USER_ID, NAME, USER_PW, USER_EMAIL, BIRTH, GENDER, ROLE) " +
            "VALUES (#{userId}, #{name}, #{userPw}, #{userEmail}, #{birth}, #{gender}, 'USER')")
    void save(MemberDTO memberDTO);

    // 2. 로그인 및 정보 조회 (아이디로 찾기)
    @Select("SELECT USER_ID, NAME, USER_PW, USER_EMAIL, GENDER, BIRTH, ROLE FROM USERS WHERE USER_ID = #{userId}")
    MemberDTO findByUserId(String userId);

    // 3. 아이디 찾기 (이름과 이메일 일치 확인)
    @Select("SELECT USER_ID FROM USERS WHERE NAME = #{name} AND USER_EMAIL = #{userEmail}")
    String findIdByNameAndEmail(@Param("name") String name, @Param("userEmail") String userEmail);

    // 4. 비밀번호 재설정 전 본인 확인 (아이디와 이메일 일치 확인)
    @Select("SELECT COUNT(*) FROM USERS WHERE USER_ID = #{userId} AND USER_EMAIL = #{userEmail}")
    int checkUserExists(@Param("userId") String userId, @Param("userEmail") String userEmail);

    // 5. 비밀번호 업데이트 (비밀번호 재설정 및 마이페이지 변경 공용)
    @Update("UPDATE USERS SET USER_PW = #{userPw} WHERE USER_ID = #{userId}")
    void updatePassword(@Param("userId") String userId, @Param("userPw") String userPw);

    @Update("UPDATE USERS SET USER_EMAIL = #{userEmail} WHERE USER_ID = #{userId}")
    void updateEmail(@Param("userId") String userId, @Param("userEmail") String userEmail);
}