package com.foodlife.user.trigger.http;

import com.foodlife.auth.context.UserHolder;
import com.foodlife.user.api.dto.FollowUserListResponseDTO;
import com.foodlife.user.api.dto.FollowUserResponseDTO;
import com.foodlife.user.api.dto.UserFollowResponseDTO;
import com.foodlife.user.api.dto.UserFollowStatusResponseDTO;
import com.foodlife.user.domain.follow.model.FollowUserEntity;
import com.foodlife.user.domain.follow.model.FollowUserListResult;
import com.foodlife.user.domain.follow.model.UserFollowCommand;
import com.foodlife.user.domain.follow.model.UserFollowEntity;
import com.foodlife.user.domain.follow.service.UserFollowDomainService;
import com.foodlife.user.types.response.Response;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
public class UserFollowController {

    private final UserFollowDomainService userFollowDomainService;

    public UserFollowController(UserFollowDomainService userFollowDomainService) {
        this.userFollowDomainService = userFollowDomainService;
    }

    @PostMapping("/follows/{followUserId}")
    public Response<UserFollowResponseDTO> followUser(@PathVariable Long followUserId) {
        try {
            return Response.success(toResponse(userFollowDomainService.followUser(toCommand(followUserId))));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @DeleteMapping("/follows/{followUserId}")
    public Response<UserFollowResponseDTO> unfollowUser(@PathVariable Long followUserId) {
        try {
            return Response.success(toResponse(userFollowDomainService.unfollowUser(toCommand(followUserId))));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @GetMapping("/follows/{followUserId}/status")
    public Response<UserFollowStatusResponseDTO> queryFollowStatus(@PathVariable Long followUserId) {
        try {
            UserFollowStatusResponseDTO response = new UserFollowStatusResponseDTO();
            response.setUserId(UserHolder.getUserId());
            response.setFollowUserId(followUserId);
            response.setFollowing(userFollowDomainService.isFollowing(UserHolder.getUserId(), followUserId));
            return Response.success(response);
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @GetMapping("/follows")
    public Response<FollowUserListResponseDTO> listFollowingUsers(@RequestParam(required = false) Long lastId,
                                                                  @RequestParam(required = false) Integer pageSize) {
        try {
            return Response.success(toListResponse(userFollowDomainService.listFollowingUsers(UserHolder.getUserId(), lastId, pageSize)));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @GetMapping("/fans")
    public Response<FollowUserListResponseDTO> listFans(@RequestParam(required = false) Long lastId,
                                                        @RequestParam(required = false) Integer pageSize) {
        try {
            return Response.success(toListResponse(userFollowDomainService.listFans(UserHolder.getUserId(), lastId, pageSize)));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @GetMapping("/follows/common/{targetUserId}")
    public Response<FollowUserListResponseDTO> listCommonFollowUsers(@PathVariable Long targetUserId,
                                                                     @RequestParam(required = false) Integer pageSize) {
        try {
            return Response.success(toListResponse(userFollowDomainService.listCommonFollowUsers(UserHolder.getUserId(), targetUserId, pageSize)));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    private UserFollowCommand toCommand(Long followUserId) {
        UserFollowCommand command = new UserFollowCommand();
        command.setUserId(UserHolder.getUserId());
        command.setFollowUserId(followUserId);
        return command;
    }

    private UserFollowResponseDTO toResponse(UserFollowEntity entity) {
        UserFollowResponseDTO response = new UserFollowResponseDTO();
        response.setFollowId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setFollowUserId(entity.getFollowUserId());
        response.setFollowing(entity.getFollowStatus() != null && entity.getFollowStatus() == 1);
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

    private FollowUserListResponseDTO toListResponse(FollowUserListResult result) {
        FollowUserListResponseDTO response = new FollowUserListResponseDTO();
        response.setUsers(result.getUsers().stream().map(this::toFollowUserResponse).collect(Collectors.toList()));
        response.setHasMore(result.getHasMore());
        response.setLastId(result.getLastId());
        return response;
    }

    private FollowUserResponseDTO toFollowUserResponse(FollowUserEntity entity) {
        FollowUserResponseDTO response = new FollowUserResponseDTO();
        response.setFollowId(entity.getFollowId());
        response.setUserId(entity.getUserId());
        response.setNickName(entity.getNickName());
        response.setIcon(entity.getIcon());
        response.setFollowTime(entity.getFollowTime());
        return response;
    }
}
