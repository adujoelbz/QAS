package com.example.qas.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {

    @NotNull
    private String status;   // "APPROVED" or "REJECTED"

    private Long doctorId;   // required if status = APPROVED
}