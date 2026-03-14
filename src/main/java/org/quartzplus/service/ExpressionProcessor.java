package org.quartzplus.service;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * Processor that evaluates Spring Expression Language (SpEL)
 * and property placeholder expressions by delegating directly to the Spring environment.
 * <p>
 * This processor allows QuartzPlus to dynamically resolve configuration values from:
 * <ul>
 *     <li><b>SpEL Expressions:</b> Evaluated via {@link SpelExpressionParser}, with access to beans in the ApplicationContext.</li>
 *     <li><b>Property Placeholders:</b> Resolved directly via {@link ConfigurableEnvironment#resolveRequiredPlaceholders(String)},
 *     ensuring compatibility with all active Spring {@code PropertySources} and runtime updates.</li>
 * </ul>
 */
public interface ExpressionProcessor {

    /**
     * Evaluates the given expression string and returns the result as the specified type.
     *
     * @param expression the raw expression string (must start with {@code #{} or ${}}).
     * @param type       the expected class of the return value.
     * @param <T>        the target return type.
     * @return the evaluated and type-converted result.
     * @throws IllegalArgumentException if the expression is blank, malformed, or a property is not found.
     */
    <T> T processExpression(String expression, Class<T> type);

}
