package com.foodlife.user.trigger.http;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.foodlife.user.api.IUserAuthService;
import com.foodlife.user.api.dto.LoginRequestDTO;
import com.foodlife.user.api.dto.LoginResponseDTO;
import com.foodlife.user.domain.auth.service.AuthDomainService;
import com.foodlife.user.domain.user.model.UserEntity;
import com.foodlife.user.domain.user.repository.IUserRepository;
import com.foodlife.user.types.constants.UserRedisConstants;
import com.foodlife.user.types.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/user")
public class UserAuthController implements IUserAuthService {

    private final StringRedisTemplate stringRedisTemplate;
    private final AuthDomainService authDomainService;
    private final IUserRepository userRepository;

    public UserAuthController(StringRedisTemplate stringRedisTemplate,
                              AuthDomainService authDomainService,
                              IUserRepository userRepository) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.authDomainService = authDomainService;
        this.userRepository = userRepository;
    }

    @Override
    @PostMapping("/code")
    public Response<Boolean> sendCode(@RequestParam String phone) {
        if (!authDomainService.isValidPhone(phone)) {
            return Response.fail("400", "手机号格式错误");
        }
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(
                UserRedisConstants.LOGIN_CODE_KEY + phone,
                code,
                UserRedisConstants.LOGIN_CODE_TTL_MINUTES,
                TimeUnit.MINUTES
        );
        log.info("send login code success, phone={}, code={}", phone, code);
        return Response.success(true);
    }

    @Override
    @PostMapping("/login")
    public Response<LoginResponseDTO> login(@RequestBody LoginRequestDTO request) {
        String phone = request == null ? null : request.getPhone();
        if (!authDomainService.isValidPhone(phone)) {
            return Response.fail("400", "手机号格式错误");
        }

        String cacheCode = stringRedisTemplate.opsForValue().get(UserRedisConstants.LOGIN_CODE_KEY + phone);
        if (cacheCode == null || !cacheCode.equals(request.getCode())) {
            return Response.fail("400", "验证码错误");
        }

        UserEntity user = userRepository.findByPhone(phone);
        if (user == null) {
            user = userRepository.save(authDomainService.createUserWithPhone(phone));
        }

        String token = UUID.randomUUID().toString(true);
        stringRedisTemplate.opsForHash().putAll(UserRedisConstants.LOGIN_TOKEN_KEY + token, toLoginMap(user));
        stringRedisTemplate.expire(UserRedisConstants.LOGIN_TOKEN_KEY + token,
                UserRedisConstants.LOGIN_TOKEN_TTL_MINUTES, TimeUnit.MINUTES);
        stringRedisTemplate.delete(UserRedisConstants.LOGIN_CODE_KEY + phone);

        LoginResponseDTO response = new LoginResponseDTO();
        response.setToken(token);
        return Response.success(response);
    }

    @Override
    @PostMapping("/logout")
    public Response<Boolean> logout(@RequestHeader(value = "authorization", required = false) String token) {
        if (token != null && token.length() > 0) {
            stringRedisTemplate.delete(UserRedisConstants.LOGIN_TOKEN_KEY + token);
        }
        return Response.success(true);
    }

    private Map<String, String> toLoginMap(UserEntity user) {
        Map<String, String> userMap = new HashMap<>();
        userMap.put("id", String.valueOf(user.getId()));
        userMap.put("nickName", user.getNickName());
        userMap.put("icon", user.getIcon() == null ? "" : user.getIcon());
        return userMap;
    }
}
