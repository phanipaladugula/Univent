package com.univent.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReviewVoteRequest {
    @NotNull(message = "Vote type is required")
    private VoteType voteType;

    public enum VoteType {
        UP, DOWN
    }
}