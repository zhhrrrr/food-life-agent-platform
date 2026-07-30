package com.foodlife.auth.context;

import com.foodlife.auth.model.LoginUserDTO;

public final class UserHolder {

    private static final ThreadLocal<LoginUserDTO> USER_LOCAL = new ThreadLocal<>();

    private UserHolder() {
    }

    public static void saveUser(LoginUserDTO user) {
        USER_LOCAL.set(user);
    }

    public static LoginUserDTO getUser() {
        return USER_LOCAL.get();
    }

    public static Long getUserId() {
        LoginUserDTO user = getUser();
        return user == null ? null : user.getId();
    }

    public static void removeUser() {
        USER_LOCAL.remove();
    }
}
