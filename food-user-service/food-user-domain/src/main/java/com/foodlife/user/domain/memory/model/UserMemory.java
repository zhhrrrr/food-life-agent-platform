package com.foodlife.user.domain.memory.model;

public record UserMemory(String memoryType, String memoryKey, String valueJson,
                         String sourceTurnId, long version) {
}
