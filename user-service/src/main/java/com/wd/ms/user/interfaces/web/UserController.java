package com.wd.ms.user.interfaces.web;

import com.wd.ms.common.core.PageQuery;
import com.wd.ms.common.core.PageResult;
import com.wd.ms.common.core.Result;
import com.wd.ms.common.security.CurrentUser;
import com.wd.ms.user.application.UserApplicationService;
import com.wd.ms.user.domain.user.User;
import com.wd.ms.user.domain.user.UserQuery;
import com.wd.ms.user.domain.user.UserStatus;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口，路径、权限码和返回结构与前端 Mock（mock/routes/user.ts）一致。
 *
 * <p>接口级权限在服务里声明，而不是只靠网关：网关只管"是不是登录了"，"能不能做这件事"是业务规则，归服务自己。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserApplicationService userService;

    public UserController(UserApplicationService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("@perm.has('user:list')")
    public Result<PageResult<UserResponse>> page(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        UserStatus statusFilter = status == null || status.isBlank() ? null : UserStatus.fromCode(status);
        var query = new UserQuery(keyword, statusFilter, PageQuery.of(page, pageSize));
        return Result.ok(userService.page(query).map(UserResponse::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('user:list')")
    public Result<UserResponse> get(@PathVariable long id) {
        return Result.ok(UserResponse.from(userService.get(id)));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@perm.has('user:edit')")
    public Result<UserResponse> changeStatus(@PathVariable long id, @RequestBody StatusRequest request) {
        User user = userService.changeStatus(
                id, UserStatus.fromCode(request.status()), CurrentUser.get().id());
        return Result.ok(UserResponse.from(user));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('user:delete')")
    public Result<Void> delete(@PathVariable long id) {
        userService.delete(id, CurrentUser.get().id());
        return Result.ok();
    }

    public record StatusRequest(String status) {}

    /** 对应前端 src/types/user.ts 的 User：枚举对外用小写字符串，时间用 ISO-8601 */
    public record UserResponse(
            long id, String username, String nickname, String email, String role, String status, Instant createdAt) {

        static UserResponse from(User user) {
            return new UserResponse(
                    user.id(),
                    user.username(),
                    user.nickname(),
                    user.email(),
                    user.role(),
                    user.status().code(),
                    user.createdAt());
        }
    }
}
