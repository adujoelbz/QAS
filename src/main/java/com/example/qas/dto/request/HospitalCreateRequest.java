package com.example.qas.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HospitalCreateRequest {

    @NotBlank
    private String name;

    private String address;

    private BigDecimal latitude;

    private BigDecimal longitude;

    @Size(max = 50)
    private String phone;

    @Size(max = 255)
    private String email;
}