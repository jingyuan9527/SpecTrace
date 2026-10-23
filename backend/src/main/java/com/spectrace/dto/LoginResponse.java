package com.spectrace.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private Long userId;
    private String username;
    private String nickname;
    private String role;
    /** true = 仍在使用初始密码或被管理员重置的密码，前端必须先把用户引到改密页 */
    private Boolean mustChangePassword;
}
