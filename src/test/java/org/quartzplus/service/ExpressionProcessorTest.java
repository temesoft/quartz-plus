package org.quartzplus.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.quartzplus.test.TestCoreConfigImport;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConversionService;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ExpressionProcessorTest {

    private static ConfigurableApplicationContext ctx;
    private static ExpressionProcessor processor;

    @Test
    public void testExpressionProcessor() {
        processor = new ExpressionProcessorImpl(ctx, ctx.getBean(ConversionService.class));
        assertThat(processor.processExpression("${testing.test1}", String.class)).isEqualTo("foo");
        assertThat(processor.processExpression("${testing.test2}", Integer.class)).isEqualTo(123);
        assertThat(processor.processExpression("${testing.test3}", Boolean.class)).isTrue();
        assertThat(processor.processExpression("${testing.test4}", Duration.class)).isEqualTo(Duration.ofMinutes(18));
        assertThat(processor.processExpression("${testing.test5:default-value}", String.class)).isEqualTo("default-value");
        assertThat(processor.processExpression("#{'This is a test'}", String.class)).isEqualTo("This is a test");
        assertThat(processor.processExpression("#{(T(java.lang.Math).random() * 98.0) + 1}", Double.class)).isGreaterThan(0).isLessThan(100);
        assertThat(processor.processExpression("#{2 > 1 ? true : false}", Boolean.class)).isTrue();
    }

    @Test
    public void testExceptionThrowing() {
        assertThatThrownBy(() -> processor.processExpression("", String.class))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unable to process empty expression");
        assertThatThrownBy(() -> processor.processExpression("bla bla", String.class))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Expression should start with \"#{\" or \"${\"; and end with \"}\"");
        assertThatThrownBy(() -> processor.processExpression("${this.does.not.exist}", String.class))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Property not found by expression: ${this.does.not.exist}");
        assertThatThrownBy(() -> processor.processExpression("${some.where:default-value}", Integer.class))
                .isInstanceOf(ConversionFailedException.class).hasMessage("Failed to convert from type [java.lang.String] to type [java.lang.Integer] for value [default-value]");
    }

    @BeforeAll
    public static void startup() {
        final var app = new SpringApplication(TestCoreConfigImport.class, TestConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setDefaultProperties(Map.of(
                "testing.test1", "foo",
                "testing.test2", "123",
                "testing.test3", "true",
                "testing.test4", Duration.ofMinutes(18).toString()
        ));
        ctx = app.run();
        processor = ctx.getBean(ExpressionProcessor.class);
    }

    @AfterAll
    public static void shutdown() {
        if (ctx != null) {
            ctx.close();
        }
    }

    @Configuration
    static class TestConfig {
        @Bean
        ExpressionProcessor expressionProcessor(final ConfigurableApplicationContext ctx, final ConversionService conversionService) {
            return new ExpressionProcessorImpl(ctx, conversionService);
        }
    }
}