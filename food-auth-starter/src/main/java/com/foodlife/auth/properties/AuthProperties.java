package com.foodlife.auth.properties;

import com.foodlife.auth.constants.AuthConstants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

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
}
