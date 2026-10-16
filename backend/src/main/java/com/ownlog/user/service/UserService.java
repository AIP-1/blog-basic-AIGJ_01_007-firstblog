package com.ownlog.user.service;

import com.ownlog.auth.oauth.SocialAccountRepository;
import com.ownlog.blog.service.BlogService;
import com.ownlog.global.error.BusinessException;
import com.ownlog.global.error.ErrorCode;
import com.ownlog.global.storage.ImageDeleteEvent;
import com.ownlog.global.storage.ImageReferenceChecker;
import com.ownlog.global.storage.UnusedImageCleaner;
import com.ownlog.user.domain.User;
import com.ownlog.user.dto.MeResponse;
import com.ownlog.user.dto.PasswordChangeRequest;
import com.ownlog.user.dto.ProfileUpdateRequest;
import com.ownlog.user.dto.WithdrawRequest;
import com.ownlog.user.repository.UserRepository;
import com.ownlog.user.repository.WithdrawalCleaner;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BlogService blogService;
    private final PasswordEncoder passwordEncoder;
    private final SocialAccountRepository socialAccountRepository;
    private final UnusedImageCleaner unusedImageCleaner;
    private final WithdrawalCleaner withdrawalCleaner;
    private final ImageReferenceChecker imageReferenceChecker;
    private final ApplicationEventPublisher eventPublisher;

    /** API 4.4 내 정보 */
    @Transactional(readOnly = true)
    public MeResponse getMe(Long userId) {
        return MeResponse.of(getUser(userId), blogService.getMyBlog(userId));
    }

    /** API 4.5 프로필 수정. 사진을 바꾸거나 지우면 예전 파일은 커밋 후에 삭제해요 */
    @Transactional
    public MeResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = getUser(userId);
        String oldImage = user.getProfileImageUrl();

        user.updateProfile(request.nickname().strip(), request.normalizedBio(), request.profileImageUrl());

        if (oldImage != null && !oldImage.equals(request.profileImageUrl())) {
            deleteImageIfUnused(oldImage, userId);
        }
        return MeResponse.of(user, blogService.getMyBlog(userId));
    }

    /** API 4.6 비밀번호 변경. 현재 비밀번호가 틀리면 401이 아니라 400 (401이면 프론트엔드가 로그아웃시켜요) */
    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        User user = getUser(userId);
        if (user.isPasswordSet() && !matches(request.currentPassword(), user)) {
            throw new BusinessException(ErrorCode.WRONG_PASSWORD);
        }
        if (!request.newPassword().equals(request.newPasswordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    /**
     * API 4.7 탈퇴 (U-06). 전부 한 트랜잭션이라 중간에 실패하면 아무것도 지워지지 않아요.
     * 남의 글에 단 내 댓글과 내가 한 신고는 남아요 (댓글은 "탈퇴한 사용자"로 보여요).
     */
    @Transactional
    public void withdraw(Long userId, WithdrawRequest request) {
        User user = getUser(userId);
        if (user.isPasswordSet()) {
            if (!matches(request.password(), user)) {
                throw new BusinessException(ErrorCode.WRONG_PASSWORD);
            }
        } else if (!user.getUsername().equals(request.username())) {
            throw new BusinessException(ErrorCode.WRONG_CONFIRM_USERNAME);
        }
        String oldImage = user.getProfileImageUrl();
        var postImages = withdrawalCleaner.imageUrlsOfPosts(userId);   // ⓪ 지우기 전에 내 글의 이미지 주소

        withdrawalCleaner.deleteBlogsOf(userId);       // ① 내 블로그의 글(+ 딸린 것), 카테고리, 블로그
        withdrawalCleaner.deleteLikesOf(userId);       // ② 남의 글에 누른 내 좋아요 + like_count 보정
        withdrawalCleaner.deleteBookmarksOf(userId);   // ③ 내 북마크
        socialAccountRepository.deleteByUserId(userId);    // ③-1 소셜 계정 연결을 끊어요 (다시 오면 새 회원으로 가입돼요)
        user.withdraw(passwordEncoder.encode(UUID.randomUUID().toString()));   // ④ 개인정보 익명화 + DELETED

        if (oldImage != null) {
            deleteImageIfUnused(oldImage, userId);                            // ⑤ 커밋 후 프로필 사진 삭제
        }
        unusedImageCleaner.deleteIfUnused(postImages);                        // ⑥ 커밋 후 내 글 이미지 중 안 쓰는 것 삭제
    }

    /** 다른 회원이나 글이 같은 파일을 쓰고 있으면 지우지 않아요 (남의 파일 주소를 프로필로 넣었다가 바꾸는 경우) */
    private void deleteImageIfUnused(String url, Long userId) {
        if (!imageReferenceChecker.isUsedElsewhere(url, userId)) {
            eventPublisher.publishEvent(new ImageDeleteEvent(url));
        }
    }

    private boolean matches(String rawPassword, User user) {
        return rawPassword != null && !rawPassword.isBlank() && passwordEncoder.matches(rawPassword, user.getPassword());
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
