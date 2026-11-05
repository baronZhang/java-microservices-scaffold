package com.wd.ms.auth.interfaces.web;

import com.wd.ms.auth.application.AuthApplicationService;
import com.wd.ms.auth.application.TokenPair;
import com.wd.ms.auth.domain.account.Account;
import com.wd.ms.common.core.Result;
import com.wd.ms.common.security.CurrentUser;
import com.wd.ms.common.security.TokenClaims;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口，路径和返回结构与前端 Mock（mock/routes/auth.ts）一致。
 *
 * <p>login、refresh 是公开接口（配置在 ms.security.public-paths）；logout、profile 需要登录。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthApplicationService authService;

    public AuthController(AuthApplicationService authService) {
        this.authService = authService;
    }

    /** 不加 @NotBlank 校验：和 Mock 一致，缺参数也按"用户名或密码错误"处理，不暴露更多信息 */
    @PostMapping("/login")
    public Result<TokenPair> login(@RequestBody LoginRequest request) {
        return Result.ok(authService.login(request.username(), request.password()));
    }

    @PostMapping("/refresh")
    public Result<TokenPair> refresh(@RequestBody RefreshRequest request) {
        return Result.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        authService.logout(jwt.getClaimAsString(TokenClaims.SESSION_ID));
        return Result.ok();
    }

    /** 每次都查库而不是直接回显 token 里的声明：前端拿到的是最新的昵称和权限 */
    @GetMapping("/profile")
    public Result<UserProfile> profile() {
        Account account = authService.profile(CurrentUser.get().id());
        return Result.ok(new UserProfile(
                account.id(), account.username(), account.nickname(), account.roles(), account.permissions()));
    }

    public record LoginRequest(String username, String password) {}

    public record RefreshRequest(String refreshToken) {}

    /** 对应前端 src/types/auth.ts 的 UserProfile */
    public record UserProfile(
            long id, String username, String nickname, List<String> roles, List<String> permissions) {}
}
