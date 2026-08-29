package com.yqz.openblog.user.service;

import com.yqz.openblog.common.BizException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yqz.openblog.security.JwtService;
import com.yqz.openblog.security.TokenHashUtil;
import com.yqz.openblog.user.dto.AuthResponse;
import com.yqz.openblog.user.dto.ChangePasswordRequest;
import com.yqz.openblog.user.dto.MeResponse;
import com.yqz.openblog.user.dto.RegisterRequest;
import com.yqz.openblog.user.dto.LoginRequest;
import com.yqz.openblog.user.dto.RefreshRequest;
import com.yqz.openblog.user.dto.UserUpdateRequest;
import com.yqz.openblog.user.entity.RefreshToken;
import com.yqz.openblog.user.entity.User;
import com.yqz.openblog.user.entity.UserRole;
import com.yqz.openblog.media.service.MediaService;
import com.yqz.openblog.user.repo.RefreshTokenMapper;
import com.yqz.openblog.user.repo.UserMapper;
import com.yqz.openblog.user.validator.EmailValidator;
import com.yqz.openblog.config.ClientIpResolver;
import com.yqz.openblog.security.CurrentUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final com.yqz.openblog.security.JwtProperties jwtProperties;
    private final CurrentUser currentUser;
    private final SliderVerificationService sliderVerificationService;
    private final LoginLockoutService loginLockoutService;
    private final MediaService mediaService;
    private final EmailValidator emailValidator;
    private final EmailCodeService emailCodeService;

    public AuthService(UserMapper userMapper,
                        RefreshTokenMapper refreshTokenMapper,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        com.yqz.openblog.security.JwtProperties jwtProperties,
                        CurrentUser currentUser,
                        SliderVerificationService sliderVerificationService,
                        LoginLockoutService loginLockoutService,
                        MediaService mediaService,
                        EmailValidator emailValidator,
                        EmailCodeService emailCodeService) {
        this.userMapper = userMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.currentUser = currentUser;
        this.sliderVerificationService = sliderVerificationService;
        this.loginLockoutService = loginLockoutService;
        this.mediaService = mediaService;
        this.emailValidator = emailValidator;
        this.emailCodeService = emailCodeService;
    }

    public AuthResponse register(RegisterRequest req) {
        sliderVerificationService.verifyAndConsume(req.getSliderChallengeId());
        // 用户名归一化：去掉首尾空白（DTO 层 @Pattern 已禁空白字符，此处为绕过校验的内部调用兜底）
        String username = req.getUsername() == null ? null : req.getUsername().trim();

        // 邮箱校验
        String emailError = emailValidator.validate(req.getEmail());
        if (emailError != null) {
            throw new BizException(clientErrorCode(), emailError);
        }

        // 校验邮箱验证码（code-first：先验证验证码，再建号）
        emailCodeService.verifyAndConsume(req.getEmail(), req.getCode());

        if (userMapper.selectCount(Wrappers.lambdaQuery(User.class)
                .eq(User::getUsername, username)) > 0) {
            throw new BizException(clientErrorCode(), "用户名已存在");
        }
        if (userMapper.selectCount(Wrappers.lambdaQuery(User.class)
                .eq(User::getEmail, req.getEmail())) > 0) {
            throw new BizException(clientErrorCode(), "邮箱已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(req.getEmail());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        // 昵称已弃用：对外展示统一使用用户名
        user.setNickname(null);
        // 前台自助注册：读者账号（与管理员/作者在库中共存；控制台与发文权限见接口鉴权）
        user.setRole(UserRole.READER);
        user.setStatus("ACTIVE");
        userMapper.insert(user);

        // 注册即登录，直接返回 token
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        Instant refreshExpireAt = Instant.now().plusSeconds(jwtProperties.getRefreshTokenExpireSeconds());
        RefreshToken token = new RefreshToken(user.getId(), TokenHashUtil.sha256Hex(refreshToken), refreshExpireAt);
        refreshTokenMapper.insert(token);

        AuthResponse resp = new AuthResponse();
        resp.setAccessToken(accessToken);
        resp.setRefreshToken(refreshToken);
        return resp;
    }

    public AuthResponse login(LoginRequest req) {
        String ipSeg = currentIpKeySegment();
        loginLockoutService.assertNotLocked(ipSeg);
        sliderVerificationService.verifyAndConsume(req.getSliderChallengeId());

        User user = userMapper.selectOne(Wrappers.lambdaQuery(User.class).eq(User::getUsername, req.getAccount()));
        if (user == null) {
            user = userMapper.selectOne(Wrappers.lambdaQuery(User.class).eq(User::getEmail, req.getAccount()));
        }
        if (user == null) {
            loginLockoutService.recordPasswordFailure(ipSeg);
            throw new BizException(clientErrorCode(), "账号或密码错误");
        }

        if ("PENDING".equals(user.getStatus())) {
            throw new BizException(4014, "账号待管理员审核通过后方可登录");
        }
        if ("BANNED".equals(user.getStatus())) {
            loginLockoutService.recordPasswordFailure(ipSeg);
            throw new BizException(4011, "账号已被封禁");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            loginLockoutService.recordPasswordFailure(ipSeg);
            throw new BizException(clientErrorCode(), "账号或密码错误");
        }

        loginLockoutService.clearFailures(ipSeg);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        Instant refreshExpireAt = Instant.now().plusSeconds(jwtProperties.getRefreshTokenExpireSeconds());
        RefreshToken token = new RefreshToken(user.getId(), TokenHashUtil.sha256Hex(refreshToken), refreshExpireAt);
        refreshTokenMapper.insert(token);

        AuthResponse resp = new AuthResponse();
        resp.setAccessToken(accessToken);
        resp.setRefreshToken(refreshToken);
        return resp;
    }

    public AuthResponse refresh(RefreshRequest req) {
        String tokenHash = TokenHashUtil.sha256Hex(req.getRefreshToken());
        RefreshToken rt = refreshTokenMapper.selectOne(
                Wrappers.lambdaQuery(RefreshToken.class).eq(RefreshToken::getTokenHash, tokenHash));
        if (rt == null) {
            throw new BizException(4012, "刷新令牌无效");
        }

        if (rt.getRevokedAt() != null) {
            throw new BizException(4012, "刷新令牌已撤销");
        }
        if (rt.getExpiresAt().isBefore(Instant.now())) {
            throw new BizException(4012, "刷新令牌已过期");
        }

        rt.revoke(Instant.now());
        refreshTokenMapper.updateById(rt);

        Long userId = rt.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(4041, "用户不存在");
        }
        assertUserAccountActive(user);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        Instant refreshExpireAt = Instant.now().plusSeconds(jwtProperties.getRefreshTokenExpireSeconds());
        RefreshToken newRt = new RefreshToken(user.getId(), TokenHashUtil.sha256Hex(refreshToken), refreshExpireAt);
        refreshTokenMapper.insert(newRt);

        AuthResponse resp = new AuthResponse();
        resp.setAccessToken(accessToken);
        resp.setRefreshToken(refreshToken);
        return resp;
    }

    public MeResponse me() {
        Long uid = currentUser.userId();
        if (uid == null) {
            throw new BizException(4010, "未登录");
        }
        User user = userMapper.selectById(uid);
        if (user == null) {
            throw new BizException(4041, "用户不存在");
        }
        assertUserAccountActive(user);
        MeResponse resp = new MeResponse();
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setAvatarUrl(mediaService.normalizePublicMediaUrl(user.getAvatarUrl()));
        resp.setBio(user.getBio());
        resp.setRole(user.getRole());
        return resp;
    }

    public void changePassword(ChangePasswordRequest req) {
        String email = req.getEmail().trim();
        User user = userMapper.selectOne(Wrappers.lambdaQuery(User.class).eq(User::getEmail, email));
        if (user == null) {
            throw new BizException(clientErrorCode(), "邮箱未注册");
        }
        if ("BANNED".equals(user.getStatus())) {
            throw new BizException(4011, "账号已被封禁");
        }

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(user);
        revokeAllRefreshTokens(user.getId());
    }

    public MeResponse updateMe(UserUpdateRequest req) {
        Long uid = currentUser.userId();
        if (uid == null) {
            throw new BizException(4010, "未登录");
        }
        User user = userMapper.selectById(uid);
        if (user == null) {
            throw new BizException(4041, "用户不存在");
        }
        assertUserAccountActive(user);

        if (req.getUsername() != null && !req.getUsername().trim().isEmpty()) {
            String newUsername = req.getUsername().trim();
            if (!newUsername.equals(user.getUsername()) &&
                    userMapper.selectCount(Wrappers.lambdaQuery(User.class).eq(User::getUsername, newUsername)) > 0) {
                throw new BizException(4090, "用户名已存在");
            }
            user.setUsername(newUsername);
        }

        if (req.getBio() != null) {
            user.setBio(req.getBio());
        }

        if (req.getAvatarUrl() != null) {
            user.setAvatarUrl(req.getAvatarUrl());
        }

        if (req.getEmail() != null && !req.getEmail().trim().isEmpty()) {
            String newEmail = req.getEmail().trim();
            if (!newEmail.equals(user.getEmail())) {
                String emailError = emailValidator.validate(newEmail);
                if (emailError != null) {
                    throw new BizException(4090, emailError);
                }
                if (userMapper.selectCount(Wrappers.lambdaQuery(User.class).eq(User::getEmail, newEmail)) > 0) {
                    throw new BizException(4090, "邮箱已存在");
                }
            }
            user.setEmail(newEmail);
        }

        userMapper.updateById(user);
        return me();
    }

    private void revokeAllRefreshTokens(Long userId) {
        Instant now = Instant.now();
        List<RefreshToken> tokens = refreshTokenMapper.selectList(
                Wrappers.lambdaQuery(RefreshToken.class)
                        .eq(RefreshToken::getUserId, userId)
                        .isNull(RefreshToken::getRevokedAt));
        for (RefreshToken token : tokens) {
            token.revoke(now);
            refreshTokenMapper.updateById(token);
        }
    }

    private void assertUserAccountActive(User user) {
        if ("PENDING".equals(user.getStatus())) {
            throw new BizException(4014, "账号待管理员审核通过后方可登录");
        }
        if ("BANNED".equals(user.getStatus())) {
            throw new BizException(4011, "账号已被封禁");
        }
    }

    // MVP：错误码占位（后续你可按文档替换成统一 code 表）
    private int clientErrorCode() {
        return 4000;
    }

    private String currentIpKeySegment() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            return "unknown";
        }
        HttpServletRequest request = sra.getRequest();
        String ip = ClientIpResolver.resolve(request);
        return ClientIpResolver.toRedisKeySegment(ip);
    }
}

