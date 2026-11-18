package com.example.ty.admin;

import com.example.ty.auth.entity.User;
import com.example.ty.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时保证至少存在一名管理员：
 * 若数据库里没有 ADMIN（例如从旧版本升级上来），把最早注册的用户提升为管理员。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository users;

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByRole("ADMIN")) return;
        users.findTopByOrderByIdAsc().ifPresent(user -> {
            user.setRole("ADMIN");
            users.save(user);
            log.warn("未发现管理员账号，已将最早注册的用户 {} 提升为 ADMIN", user.getEmail());
        });
    }
}
