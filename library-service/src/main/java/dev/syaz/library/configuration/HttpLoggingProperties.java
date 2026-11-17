package dev.syaz.library.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("library.http-logging")
public record HttpLoggingProperties(@DefaultValue("true") boolean enabled) {
}
