package com.minigit.dto;

/**
 * Repository response DTO.
 */
public class RepositoryResponse {

    private String name;

    public RepositoryResponse() {
    }

    public RepositoryResponse(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
