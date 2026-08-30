package com.foodlife.user.trigger.http;

import com.foodlife.auth.context.UserHolder;
import com.foodlife.auth.model.LoginUserDTO;
import com.foodlife.user.api.dto.UpdateUserProfileRequestDTO;
import com.foodlife.user.api.dto.UserProfileResponseDTO;
import com.foodlife.user.domain.profile.model.UserProfileEntity;
import com.foodlife.user.domain.profile.model.UserProfileUpdateCommand;
import com.foodlife.user.domain.profile.service.UserProfileDomainService;
import com.foodlife.user.types.constants.UserRedisConstants;
import com.foodlife.user.types.response.Response;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserProfileController {

    private final UserProfileDomainService userProfileDomainService;
    private final StringRedisTemplate stringRedisTemplate;

    public UserProfileController(UserProfileDomainService userProfileDomainService,
                                 StringRedisTemplate stringRedisTemplate) {
        this.userProfileDomainService = userProfileDomainService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @GetMapping("/profile/me")
    public Response<UserProfileResponseDTO> queryMyProfile() {
        try {
            return Response.success(toResponse(userProfileDomainService.queryMyProfile(UserHolder.getUserId()), true));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @PutMapping("/profile")
    public Response<UserProfileResponseDTO> updateMyProfile(@RequestBody UpdateUserProfileRequestDTO request,
                                                            @RequestHeader(value = "authorization", required = false) String token) {
        try {
            UserProfileEntity profile = userProfileDomainService.updateMyProfile(toCommand(request));
            refreshLoginUser(profile, token);
            return Response.success(toResponse(profile, true));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @GetMapping("/homepage/{userId}")
    public Response<UserProfileResponseDTO> queryUserHomepage(@PathVariable Long userId) {
        try {
            return Response.success(toResponse(userProfileDomainService.queryUserHomepage(UserHolder.getUserId(), userId), false));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    private UserProfileUpdateCommand toCommand(UpdateUserProfileRequestDTO request) {
        UserProfileUpdateCommand command = new UserProfileUpdateCommand();
        command.setUserId(UserHolder.getUserId());
        if (request != null) {
            command.setNickName(request.getNickName());
            command.setIcon(request.getIcon());
            command.setCity(request.getCity());
            command.setBio(request.getBio());
            command.setFoodPreference(request.getFoodPreference());
        }
        return command;
    }

    private UserProfileResponseDTO toResponse(UserProfileEntity entity, boolean exposePhone) {
        UserProfileResponseDTO response = new UserProfileResponseDTO();
        response.setUserId(entity.getUserId());
        response.setPhone(exposePhone ? entity.getPhone() : null);
        response.setNickName(entity.getNickName());
        response.setIcon(entity.getIcon());
        response.setCity(entity.getCity());
        response.setBio(entity.getBio());
        response.setFoodPreference(entity.getFoodPreference());
        response.setFollowingCount(entity.getFollowingCount());
        response.setFansCount(entity.getFansCount());
        response.setFollowing(entity.getFollowing());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

    private void refreshLoginUser(UserProfileEntity profile, String token) {
        LoginUserDTO loginUser = UserHolder.getUser();
        if (loginUser != null) {
            loginUser.setNickName(profile.getNickName());
            loginUser.setIcon(profile.getIcon());
            UserHolder.saveUser(loginUser);
        }
        if (token != null && token.length() > 0) {
            String key = UserRedisConstants.LOGIN_TOKEN_KEY + token;
            stringRedisTemplate.opsForHash().put(key, "nickName", profile.getNickName());
            stringRedisTemplate.opsForHash().put(key, "icon", profile.getIcon() == null ? "" : profile.getIcon());
        }
    }
}
