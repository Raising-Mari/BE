package com.aidisciple.global.health;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FE 와 BE 가 연결됐는지 확인하는 용도. DB 는 건드리지 않는다. */
@RestController
@RequestMapping("/api/health")
public class HealthController {

	public record HealthResponse(String status, Instant serverTime) {
	}

	@GetMapping
	public HealthResponse health() {
		return new HealthResponse("ok", Instant.now());
	}

}
