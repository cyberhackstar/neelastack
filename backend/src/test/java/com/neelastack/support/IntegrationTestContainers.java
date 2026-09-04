package com.neelastack.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

public final class IntegrationTestContainers {

  private IntegrationTestContainers() {
  }

  public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  public static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
      .withExposedPorts(6379);

  static {
    POSTGRES.start();
    REDIS.start();

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        REDIS.stop();
      } finally {
        POSTGRES.stop();
      }
    }));
  }
}