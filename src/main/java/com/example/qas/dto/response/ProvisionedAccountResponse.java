package com.example.qas.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProvisionedAccountResponse {
    private Long userId;
    private Long profileId;
    private String email;
    private String role;
    private boolean enabled;
}
