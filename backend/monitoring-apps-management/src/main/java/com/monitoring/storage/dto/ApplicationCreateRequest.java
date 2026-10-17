package com.monitoring.storage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ApplicationCreateRequest {

    @NotBlank(message = "Название приложения обязательно")
    @Size(max = 100, message = "Максимальная длина названия: 100 символов")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
