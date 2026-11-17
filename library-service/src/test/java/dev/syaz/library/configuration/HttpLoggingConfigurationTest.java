package dev.syaz.library.configuration;

import dev.syaz.library.filter.HttpLoggingFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.assertj.core.api.Assertions.assertThat;

class HttpLoggingConfigurationTest {
    @Test
    void bindsLoggingSettingsAndRegistersTheApiFilter() {
        new ApplicationContextRunner()
                .withUserConfiguration(HttpLoggingConfiguration.class)
                .withPropertyValues("library.http-logging.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(HttpLoggingProperties.class)).isEqualTo(new HttpLoggingProperties(false));
                    FilterRegistrationBean<?> registration = context.getBean("httpLoggingFilter", FilterRegistrationBean.class);
                    assertThat(registration.getUrlPatterns()).containsExactly("/api/*");
                    assertThat(registration.getFilter()).isInstanceOf(HttpLoggingFilter.class);
                });
    }
}
