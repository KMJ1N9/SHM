package com.shm.admin.security;

import com.shm.common.model.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security 用户主体（与 Node.js req.user 挂载的字段一致）
 *
 * <p>JwtAuthFilter 验证 Token 后，将 UserPrincipal 注入 SecurityContextHolder，
 * Controller 层通过 @CurrentUser 获取当前用户。
 */
public class UserPrincipal implements UserDetails {

    private final Long userId;
    private final String phone;
    private final String nickname;
    private final String avatar;
    private final String role;
    private final String status;
    private final Integer creditScore;

    public UserPrincipal(User user) {
        this.userId = user.getId();
        this.phone = user.getPhone();
        this.nickname = user.getNickname();
        this.avatar = user.getAvatar();
        this.role = user.getRole();
        this.status = user.getStatus();
        this.creditScore = user.getCreditScore();
    }

    // ===== 业务字段 =====

    public Long getUserId() { return userId; }
    public String getPhone() { return phone; }
    public String getNickname() { return nickname; }
    public String getAvatar() { return avatar; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public Integer getCreditScore() { return creditScore; }

    // ===== UserDetails 接口 =====

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() { return null; }

    @Override
    public String getUsername() { return String.valueOf(userId); }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return !"banned".equals(status); }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return "active".equals(status); }
}
