package com.coffee.order.controller;

import com.coffee.order.dto.AuthRequest;
import com.coffee.order.dto.AuthResponse;
import com.coffee.order.entity.CoffeeUser;
import com.coffee.order.repository.CoffeeUserRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {
    private final CoffeeUserRepository userRepository;

    public AuthController(CoffeeUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public AuthResponse register(@RequestBody AuthRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            return new AuthResponse(false, "用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().length() < 4) {
            return new AuthResponse(false, "密码至少4位");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            return new AuthResponse(false, "用户名已存在");
        }

        CoffeeUser user = new CoffeeUser();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user = userRepository.save(user);

        return AuthResponse.ok(user.getId(), user.getUsername(), user.getNickname(), user.getTotalSpent());
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody AuthRequest request) {
        return userRepository.findByUsername(request.getUsername())
                .filter(u -> u.getPassword().equals(request.getPassword()))
                .map(u -> AuthResponse.ok(u.getId(), u.getUsername(), u.getNickname(), u.getTotalSpent()))
                .orElse(new AuthResponse(false, "用户名或密码错误"));
    }

    @GetMapping("/user/{id}")
    public AuthResponse getUserInfo(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(u -> AuthResponse.ok(u.getId(), u.getUsername(), u.getNickname(), u.getTotalSpent()))
                .orElse(new AuthResponse(false, "用户不存在"));
    }
}
