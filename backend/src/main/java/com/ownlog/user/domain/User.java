package com.ownlog.user.domain;

import com.ownlog.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원 (ERD 3.1). 탈퇴는 Soft Delete + 개인정보 익명화예요. */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    public static final String DELETED_NICKNAME = "탈퇴한 사용자";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 20, updatable = false)  // 아이디는 바꿀 수 없어요 (U-07)
    private String username;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(length = 100)
    private String bio;

    @Column(length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    private Instant deletedAt;

    /** 비밀번호를 직접 정했는지. 소셜 로그인으로만 가입하면 false예요 (V6) */
    @Column(nullable = false)
    private boolean passwordSet;

    /** 회원가입. 이메일은 소문자로 저장해요 (U-01). 비밀번호는 이미 BCrypt로 해시된 값을 받아요. */
    public static User create(String email, String encodedPassword, String username, String nickname) {
        User user = new User();
        user.email = normalizeEmail(email);
        user.password = encodedPassword;
        user.username = username;
        user.nickname = nickname;
        user.role = UserRole.USER;
        user.status = UserStatus.ACTIVE;
        user.passwordSet = true;
        return user;
    }

    /**
     * 소셜 로그인으로 가입. 비밀번호 칸은 비울 수 없어서 아무도 모르는 임의 값의 해시를 넣고,
     * passwordSet=false로 표시해요 → 이메일 로그인은 안 되고, 설정에서 비밀번호를 정하면 그때부터 돼요.
     */
    public static User createSocial(String email, String randomEncodedPassword, String username, String nickname) {
        User user = create(email, randomEncodedPassword, username, nickname);
        user.passwordSet = false;
        return user;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public void updateProfile(String nickname, String bio, String profileImageUrl) {
        this.nickname = nickname;
        this.bio = bio;
        this.profileImageUrl = profileImageUrl;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.passwordSet = true;
    }

    /** 탈퇴 (U-06): 개인정보를 지우고 아이디만 남겨요. */
    public void withdraw(String randomEncodedPassword) {
        this.status = UserStatus.DELETED;
        this.email = "deleted_" + id + "@ownlog.invalid";
        this.password = randomEncodedPassword;
        this.nickname = DELETED_NICKNAME;
        this.bio = null;
        this.profileImageUrl = null;
        this.deletedAt = Instant.now();
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
