package com.devops.app.version;

import java.time.Instant;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reports which build of the app is running. The pipeline's smoke test and the
 * rollback demo use this to prove which version (from Nexus) is deployed.
 */
@RestController
public class VersionController {

	public record VersionInfo(String name, String version, Instant buildTime) {
	}

	private final ObjectProvider<BuildProperties> buildProperties;

	public VersionController(ObjectProvider<BuildProperties> buildProperties) {
		this.buildProperties = buildProperties;
	}

	@GetMapping("/api/version")
	public VersionInfo version() {
		// build-info.properties only exists when built by Maven (not when run from some IDEs).
		BuildProperties build = buildProperties.getIfAvailable();
		if (build == null) {
			return new VersionInfo("devops-app", "unknown", null);
		}
		return new VersionInfo(build.getName(), build.getVersion(), build.getTime());
	}
}
