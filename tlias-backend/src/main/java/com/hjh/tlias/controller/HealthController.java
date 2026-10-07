package com.hjh.tlias.controller;

import com.hjh.tlias.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(Map.of(
                "status", "UP",
                "application", "tlias-backend",
                "time", LocalDateTime.now().toString()
        ));
    }

}
