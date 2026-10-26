package com.example.ddangddangddang.domain.auth.service;

import com.example.ddangddangddang.domain.auth.dto.SocialLoginResult;
import com.example.ddangddangddang.domain.auth.dto.SocialSignupRequest;
import com.example.ddangddangddang.domain.auth.dto.SocialSignupResult;
import com.example.ddangddangddang.domain.auth.oauth.OAuthProviderClients;
import com.example.ddangddangddang.domain.auth.oauth.SocialUser;
import com.example.ddangddangddang.domain.auth.oauth.OAuthErrorCode;
import com.example.ddangddangddang.domain.auth.oauth.PendingSocialSignup;
import com.example.ddangddangddang.domain.auth.oauth.PendingSocialSignupRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationPurpose;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.point.service.PointService;
import com.example.ddangddangddang.domain.user.entity.SocialAccount;
import com.example.ddangddangddang.domain.user.entity.SocialProvider;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.SocialAccountRepository;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인·가입 (카카오, 구글). 제공자마다 다른 것은 회원 정보를 받아 오는 부분(OAuthProviderClient)뿐이다.
 *
 * 1. login: 소셜 계정이 연결된 회원이면 로그인, 처음이면 가입 대기 토큰을 준다.
 * 2. signup: 추가 정보와 휴대폰 인증으로 가입한다.
 *    인증한 번호로 가입한 회원이 이미 있으면 새로 만들지 않고 그 계정에 소셜 계정을 연결한다.
 *
 * 계정 연결은 방금 문자로 인증한 휴대폰 번호로만 한다. 소셜 계정의 이메일이 같다는 이유로 연결하지 않는다.
 * (남의 이메일로 소셜 계정을 만들어 그 회원 계정을 가져가는 일을 막기 위해)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OAuthService {

    static final Duration SIGNUP_TOKEN_TTL = Duration.ofMinutes(10);

    private final OAuthProviderClients providerClients;
    private final PendingSocialSignupRepository pendingSocialSignupRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final VerificationService verificationService;
    private final PointService pointService;
    private final AuthService authService;
    private final SignupVerificationService signupVerificationService;

    public SocialLoginResult login(SocialProvider provider, String authorizationCode) {
        SocialUser socialUser = providerClients.get(provider).getUser(authorizationCode);

        Optional<SocialAccount> linked =
                socialAccountRepository.findByProviderAndProviderUserId(provider, socialUser.id());
        if (linked.isPresent()) {
            return SocialLoginResult.login(authService.issueTokens(findUser(linked.get().getUserId())));
        }

        String signupToken = pendingSocialSignupRepository.save(new PendingSocialSignup(
                provider, socialUser.id(), socialUser.email(), socialUser.nickname()), SIGNUP_TOKEN_TTL);
        return SocialLoginResult.signupRequired(signupToken, socialUser.email(), socialUser.nickname());
    }

    /**
     * 소셜 가입의 휴대폰 인증번호 발송. 일반 가입과 달리 이미 가입된 번호에도 보낸다. (그 계정에 소셜 계정을 연결하므로)
     * 같은 제공자로 소셜 로그인을 거친 사용자(가입 대기 토큰이 있는 사용자)만 쓸 수 있다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public long sendSignupPhoneCode(SocialProvider provider, String signupToken, String phoneNumber) {
        findPending(provider, signupToken);
        return signupVerificationService.sendCodeForSocialSignup(phoneNumber);
    }

    /**
     * 검사를 모두 마친 뒤에 1회용 토큰(휴대폰 인증 토큰 → 가입 대기 토큰)을 쓴다.
     * 닉네임 중복 같은 입력 실패로 토큰을 헛되이 쓰지 않게 하기 위함.
     * 같은 요청이 두 번 동시에 와도 휴대폰 인증 토큰은 한 번만 쓸 수 있으므로 하나만 통과한다.
     */
    @Transactional
    public SocialSignupResult signup(SocialProvider provider, SocialSignupRequest request) {
        PendingSocialSignup pending = findPending(provider, request.signupToken());

        // 다른 탭에서 이미 가입을 마쳤으면 그 회원으로 로그인시킨다
        Optional<SocialAccount> alreadyLinked = socialAccountRepository.findByProviderAndProviderUserId(
                pending.provider(), pending.providerUserId());
        if (alreadyLinked.isPresent()) {
            pendingSocialSignupRepository.delete(request.signupToken());
            return SocialSignupResult.of(false, authService.issueTokens(findUser(alreadyLinked.get().getUserId())));
        }

        Optional<User> samePhoneUser = userRepository.findByPhoneNumber(request.phoneNumber());
        User user = samePhoneUser.isPresent()
                ? linkToExistingUser(samePhoneUser.get(), pending, request)
                : createUser(pending, request);

        pendingSocialSignupRepository.delete(request.signupToken());
        return SocialSignupResult.of(samePhoneUser.isPresent(), authService.issueTokens(user));
    }

    private User linkToExistingUser(User user, PendingSocialSignup pending, SocialSignupRequest request) {
        if (socialAccountRepository.existsByUserIdAndProvider(user.getId(), pending.provider())) {
            throw new BusinessException(OAuthErrorCode.ALREADY_LINKED_OTHER_ACCOUNT);
        }
        consumePhoneVerification(request);

        socialAccountRepository.save(SocialAccount.link(user.getId(), pending.provider(), pending.providerUserId()));
        log.info("기존 회원에 소셜 계정 연결: userId={}, provider={}, providerUserId={}",
                user.getId(), pending.provider(), pending.providerUserId());
        return user;
    }

    private User createUser(PendingSocialSignup pending, SocialSignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_NICKNAME);
        }
        consumePhoneVerification(request);

        User user = userRepository.save(User.createSocial(request.email(), request.nickname(), request.phoneNumber()));
        pointService.createWallet(user.getId());
        socialAccountRepository.save(SocialAccount.link(user.getId(), pending.provider(), pending.providerUserId()));
        return user;
    }

    /** 요청한 번호로 인증한 토큰이어야 쓴다. 번호가 다르면 VERIFY_004이고 토큰은 남는다. */
    private void consumePhoneVerification(SocialSignupRequest request) {
        verificationService.consumeMatchingToken(
                VerificationPurpose.SIGNUP_PHONE, request.phoneVerificationToken(), request.phoneNumber());
    }

    /**
     * 가입 대기 정보. 없거나 만료됐으면 OAUTH_003.
     * 다른 제공자로 받은 토큰(예: 카카오 토큰으로 구글 가입 주소 호출)도 같은 오류로 거절한다.
     */
    private PendingSocialSignup findPending(SocialProvider provider, String signupToken) {
        return pendingSocialSignupRepository.find(signupToken)
                .filter(pending -> pending.provider() == provider)
                .orElseThrow(() -> new BusinessException(OAuthErrorCode.SIGNUP_TOKEN_EXPIRED));
    }

    private User findUser(Long userId) {
        // 연결 정보는 있는데 회원이 없는 경우는 데이터가 깨진 것이므로 처음부터 다시 로그인하게 한다
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(OAuthErrorCode.INVALID_AUTHORIZATION_CODE));
    }
}