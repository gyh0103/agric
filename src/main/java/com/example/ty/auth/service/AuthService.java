package com.example.ty.auth.service;

import com.example.ty.auth.dto.AuthResponse;
import com.example.ty.auth.dto.LoginRequest;
import com.example.ty.auth.dto.RegisterRequest;
import com.example.ty.auth.dto.UserInfo;
import com.example.ty.auth.entity.User;
import com.example.ty.auth.exception.BizException;
import com.example.ty.auth.repository.UserRepository;
import com.example.ty.auth.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    @Value("${AUTH_REGISTRATION_ENABLED:true}") private boolean registrationEnabled;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (!registrationEnabled) throw new BizException("REGISTRATION_DISABLED","当前展示设备已关闭公开注册",403);
        String email = req.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw BizException.badRequest("该邮箱已被注册");
        }
        // 首个注册用户自动成为管理员，避免部署后没有任何后台入口
        String role = userRepository.count() == 0 ? "ADMIN" : "USER";
        User user = User.builder()
                .name(req.getName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(role)
                .build();
        userRepository.save(user);
        return new AuthResponse(jwtService.generateToken(user.getId()), UserInfo.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> BizException.unauthorized("邮箱或密码错误"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw BizException.unauthorized("邮箱或密码错误");
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw new BizException("ACCOUNT_DISABLED", "账号已被管理员禁用，请联系管理员", 403);
        }
        return new AuthResponse(jwtService.generateToken(user.getId()), UserInfo.from(user));
    }

    @Transactional(readOnly = true)
    public UserInfo me(User user) {
        return UserInfo.from(user);
    }
}
