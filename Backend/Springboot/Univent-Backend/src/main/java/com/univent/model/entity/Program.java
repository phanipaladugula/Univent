package com.univent.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "programs")
@Getter
@Setter
public class Program extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String slug;

    @Column(nullable = false)
    private String category;

    private String degree;

    @Column(name = "duration_years")
    private Integer durationYears;

    private String icon;
}