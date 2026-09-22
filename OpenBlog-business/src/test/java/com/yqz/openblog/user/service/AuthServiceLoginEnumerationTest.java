package com.yqz.openblog.user.service;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.media.service.MediaService;
import com.yqz.openblog.security.CurrentUser;
import com.yqz.openblog.security.JwtProperties;
import com.yqz.openblog.security.JwtService;
import com.yqz.openblog.user.dto.AuthResponse;
import com.yqz.openblog.user.dto.LoginRequest;
import com.yqz.openblog.user.entity.RefreshToken;
import com.yqz.openblog.user.entity.User;
import com.yqz.openblog.user.entity.UserRole;
import com.yqz.openblog.user.repo.RefreshTokenMapper;
import com.yqz.openblog.user.repo.UserMapper;
import com.yqz.openblog.user.validator.EmailValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录接口不泄露账号信息的回归测试（登录链路安全审计「先判状态、后验密码」的修复）。
 * <p>
 * 修复前 {@code AuthService.login} 在密码校验之前就判账号状态，于是不需要正确密码即可区分
 * 「账号不存在 / 待审核 / 已封禁」，等于白送账号枚举与状态探测。本用例锁三件事：
 * <ol>
 *   <li>账号不存在、密码错误：错误码与文案完全一致（含待审核/封禁账号输错密码时也不泄露状态）</li>
 *   <li>账号不存在时仍执行一次 BCrypt 比对（哨兵哈希），堵住按响应时间枚举的信道</li>
 *   <li>正确密码下的状态判定与正常登录路径未被破坏</li>
 * </ol>
 * 用真实 {@link BCryptPasswordEncoder}：等开销陪跑是本用例的验证对象，mock 掉编码器就没意义了。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceLoginEnumerationTest {

    /** 与 AuthService.clientErrorCode() 对应的通用错误码；文案用于断言两条失败路径不可区分。 */
    private static final int GENERIC_CODE = 4000;
    private static final String GENERIC_MESSAGE = "账号或密码错误";

    @Mock private UserMapper userMapper;
    @Mock private RefreshTokenMapper refreshTokenMapper;
    @Mock private JwtService jwtService;
    @Mock private JwtProperties jwtProperties;
    @Mock private CurrentUser currentUser;
    @Mock private LoginLockoutService loginLockoutService;
    @Mock private AccountDeviceService accountDeviceService;
    @Mock private MediaService mediaService;
    @Mock private EmailValidator emailValidator;
    @Mock private EmailCodeService emailCodeService;

    @Spy private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userMapper, refreshTokenMapper, passwordEncoder, jwtService,
                jwtProperties, currentUser, loginLockoutService,
                accountDeviceService, mediaService, emailValidator, emailCodeService);
    }

    // ==================== 1. 失败路径不可区分 ====================

    @Test
    void unknownAccount_genericError() {
        when(userMapper.selectOne(any())).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> authService.login(req("nobody@example.com", "whatever")));

        assertEquals(GENERIC_CODE, ex.getCode());
        assertEquals(GENERIC_MESSAGE, ex.getMessage());
    }

    @Test
    void wrongPassword_genericError() {
        User alice = activeUser("correct-horse");
        when(userMapper.selectOne(any())).thenReturn(alice);

        BizException ex = assertThrows(BizException.class,
                () -> authService.login(req("alice", "wrong-password")));

        assertEquals(GENERIC_CODE, ex.getCode());
        assertEquals(GENERIC_MESSAGE, ex.getMessage());
    }

    /** 修复点：待审核账号输错密码时，不得暴露「该账号存在且待审核」。 */
    @Test
    void pendingAccount_wrongPassword_genericError_notDisclosed() {
        User pending = user("PENDING", "correct-horse");
        when(userMapper.selectOne(any())).thenReturn(pending);

        BizException ex = assertThrows(BizException.class,
                () -> authService.login(req("alice", "wrong-password")));

        assertEquals(GENERIC_CODE, ex.getCode());
        assertEquals(GENERIC_MESSAGE, ex.getMessage());
    }

    /** 修复点：封禁账号同理，输错密码时不得暴露封禁态。 */
    @Test
    void bannedAccount_wrongPassword_genericError_notDisclosed() {
        User banned = user("BANNED", "correct-horse");
        when(userMapper.selectOne(any())).thenReturn(banned);

        BizException ex = assertThrows(BizException.class,
                () -> authService.login(req("alice", "wrong-password")));

        assertEquals(GENERIC_CODE, ex.getCode());
        assertEquals(GENERIC_MESSAGE, ex.getMessage());
    }

    // ==================== 2. 恒定开销（响应时间不泄露账号是否存在） ====================

    @Test
    void unknownAccount_stillRunsBcryptAgainstSentinelHash() {
        when(userMapper.selectOne(any())).thenReturn(null);
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);

        assertThrows(BizException.class, () -> authService.login(req("nobody", "whatever")));

        // 账号不存在也应发生一次真实 BCrypt 比对，且比对的是启动时生成的合法哈希（$2a$ 开头）
        verify(passwordEncoder).matches(eq("whatever"), hashCaptor.capture());
        assertTrue(hashCaptor.getValue().startsWith("$2"),
                "陪跑用的应为合法 BCrypt 哈希，否则编码器会走快速失败分支、起不到等开销作用");
    }

    // ==================== 3. 正确密码下：状态判定与正常登录未被破坏 ====================

    @Test
    void pendingAccount_correctPassword_4014() {
        User pending = user("PENDING", "correct-horse");
        when(userMapper.selectOne(any())).thenReturn(pending);

        BizException ex = assertThrows(BizException.class,
                () -> authService.login(req("alice", "correct-horse")));

        assertEquals(4014, ex.getCode());
    }

    @Test
    void bannedAccount_correctPassword_4011() {
        User banned = user("BANNED", "correct-horse");
        when(userMapper.selectOne(any())).thenReturn(banned);

        BizException ex = assertThrows(BizException.class,
                () -> authService.login(req("alice", "correct-horse")));

        assertEquals(4011, ex.getCode());
        // 密码已证明正确，不再计入「密码错误」的失败次数（误计会牵连同 NAT 出口上的无关用户）
        verify(loginLockoutService, never()).recordPasswordFailure(anyString());
    }

    @Test
    void activeAccount_correctPassword_issuesTokens() {
        User user = activeUser("correct-horse");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(jwtService.generateAccessToken(7L, "READER")).thenReturn("access-token");
        when(jwtService.generateRefreshToken(7L)).thenReturn("refresh-token");
        when(jwtProperties.getRefreshTokenExpireSeconds()).thenReturn(2592000L);

        AuthResponse resp = authService.login(req("alice", "correct-horse"));

        assertEquals("access-token", resp.getAccessToken());
        assertEquals("refresh-token", resp.getRefreshToken());
        verify(refreshTokenMapper).insert(any(RefreshToken.class));
        verify(loginLockoutService).clearFailures(anyString());
    }

    // ==================== 夹具 ====================

    private LoginRequest req(String account, String password) {
        LoginRequest r = new LoginRequest();
        r.setAccount(account);
        r.setPassword(password);
        return r;
    }

    private User activeUser(String rawPassword) {
        return user("ACTIVE", rawPassword);
    }

    private User user(String status, String rawPassword) {
        User u = new User();
        // User 只暴露 getId()（id 由 DB 自增），测试用反射补 id
        ReflectionTestUtils.setField(u, "id", 7L);
        u.setUsername("alice");
        u.setEmail("alice@example.com");
        u.setStatus(status);
        u.setRole(UserRole.READER);
        u.setPasswordHash(passwordEncoder.encode(rawPassword));
        assertNotNull(u.getPasswordHash());
        return u;
    }
}
