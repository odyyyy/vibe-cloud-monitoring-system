package com.monitoring.auth.controller;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
    AuthController.class,
    AuthExceptionHandler.class
})
public class AuthTestApplication {
}