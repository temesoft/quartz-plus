package org.quartzplus.service;

import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.expression.BeanExpressionContextAccessor;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Implementation of {@link ExpressionProcessor} that evaluates Spring Expression Language (SpEL)
 * and property placeholder expressions by delegating directly to the Spring environment.
 * <p>
 * This processor allows QuartzPlus to dynamically resolve configuration values from:
 * <ul>
 *     <li><b>SpEL Expressions:</b> Evaluated via {@link SpelExpressionParser}, with access to beans in the ApplicationContext.</li>
 *     <li><b>Property Placeholders:</b> Resolved directly via {@link ConfigurableEnvironment#resolveRequiredPlaceholders(String)},
 *     ensuring compatibility with all active Spring {@code PropertySources} and runtime updates.</li>
 * </ul>
 */
public class ExpressionProcessorImpl implements ExpressionProcessor {

    private static final String SPEL_PREFIX = "#{";
    private static final String SPEL_POSTFIX = "}";
    private static final String VALUE_PREFIX = "${";
    private static final String VALUE_POSTFIX = "}";

    private final ConfigurableApplicationContext ctx;
    private final ConversionService conversionService;

    /**
     * Constructs the processor using the provided Spring context and conversion infrastructure.
     *
     * @param ctx               the current {@link ConfigurableApplicationContext} for bean resolution and environment access.
     * @param conversionService the {@link ConversionService} used to cast resolved property strings to specific Java types.
     */
    public ExpressionProcessorImpl(final ConfigurableApplicationContext ctx,
                                   final ConversionService conversionService) {
        this.ctx = ctx;
        this.conversionService = conversionService;
    }

    /**
     * Evaluates the given expression string and returns the result as the specified type.
     *
     * @param expression the raw expression string (must start with {@code #{} or ${}}).
     * @param type       the expected class of the return value.
     * @param <T>        the target return type.
     * @return the evaluated and type-converted result.
     * @throws IllegalArgumentException if the expression is blank, malformed, or a property is not found.
     */
    @Override
    public <T> T processExpression(final String expression, final Class<T> type) {
        if (isBlank(expression)) {
            throw new IllegalArgumentException("Unable to process empty expression");
        }
        if (expression.startsWith(SPEL_PREFIX) && expression.endsWith(SPEL_POSTFIX)) {
            // do SpEL processing
            return parseSpELExpression(expression, type);
        } else if (expression.startsWith(VALUE_PREFIX) && expression.endsWith(VALUE_POSTFIX)) {
            // do @Value processing
            return conversionService.convert(parseValueExpression(expression), type);
        } else {
            throw new IllegalArgumentException("Expression should start with \"" + SPEL_PREFIX + "\" or \"" + VALUE_PREFIX + "\"; and end with \"" + VALUE_POSTFIX + "\"");
        }
    }

    /**
     * Parses a SpEL expression using the Spring {@link SpelExpressionParser}.
     * <p>
     * The evaluation context is configured with a {@link BeanFactoryResolver} to allow
     * access to beans within the application context.
     * </p>
     *
     * @param spelExpression the SpEL string to parse.
     * @param type           the target result type.
     * @param <T>            the return type.
     * @return the value of the SpEL expression.
     */
    private <T> T parseSpELExpression(final String spelExpression, final Class<T> type) {
        final var parser = new SpelExpressionParser();
        final var evaluationContext = new StandardEvaluationContext();
        evaluationContext.setBeanResolver(new BeanFactoryResolver(ctx.getBeanFactory()));
        evaluationContext.addPropertyAccessor(new BeanExpressionContextAccessor());
        final var expression = parser.parseExpression(spelExpression, new TemplateParserContext());
        final var rootObject = new BeanExpressionContext(ctx.getBeanFactory(), null);
        return expression.getValue(evaluationContext, rootObject, type);
    }

    /**
     * Resolves a property placeholder by delegating to the Spring {@link ConfigurableEnvironment}.
     * <p>
     * This method leverages the active Spring environment to find properties in application files,
     * environment variables, or system properties.
     * </p>
     *
     * @param valueExpression the placeholder string (e.g., "${app.timeout}").
     * @return the resolved property value as a String.
     * @throws IllegalArgumentException if the property cannot be resolved by the environment.
     */
    private String parseValueExpression(final String valueExpression) {
        try {
            // Directly delegates to Spring's environment and all registered PropertySources
            return ctx.getEnvironment().resolveRequiredPlaceholders(valueExpression);
        } catch (IllegalArgumentException e) {
            // Re-throw or handle if a property cannot be resolved
            throw new IllegalArgumentException("Property not found by expression: " + valueExpression, e);
        }
    }
}
