package com.foodlife.auth.properties;

import com.foodlife.auth.constants.AuthConstants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "food.auth")
public class AuthProperties {

    private boolean enabled = true;
    private String tokenHeader = AuthConstants.DEFAULT_TOKEN_HEADER;
    private String tokenPrefix = AuthConstants.DEFAULT_TOKEN_PREFIX;
    private long tokenTtlMinutes = AuthConstants.DEFAULT_TOKEN_TTL_MINUTES;
    private List<String> includePaths = new ArrayList<String>() {{
        add("/**");
    }};
    private List<String> excludePaths = new ArrayList<String>();
    private InternalCall internalCall = new InternalCall();
    private RoleAccess roleAccess = new RoleAccess();

    @Data
    public static class InternalCall {

        private boolean enabled = true;
        private List<String> paths = new ArrayList<String>() {{
            add("/api/internal/**");
        }};
        private String headerName = "x-internal-call";
        private String headerValue = "food-life-agent";
        private String secretHeaderName = "x-internal-secret";
        private String secret = "local-internal-secret";
    }

    @Data
    public static class RoleAccess {

        private boolean enabled = true;
        private String defaultRole = "USER";
        private List<PathRole> paths = new ArrayList<>();
        private Map<Long, String> localUserRoles = new HashMap<>();
    }

    @Data
    public static class PathRole {

        private String pattern;
        private List<String> roles = new ArrayList<>();
    }
}
