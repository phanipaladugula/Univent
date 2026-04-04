package com.univent.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CollegeRequest {
    @NotBlank(message = "College name is required")
    private String name;

    private String city;
    private String state;
    private String collegeType;
    private String website;
    private String emailDomain;
    private String logoUrl;
}