package click.yinsb.icmtracing.config;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Optional;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Ensures HTTP server spans include OpenTelemetry semantic convention attributes:
 * <ul>
 *   <li>{@code http.request.method} – HTTP method (e.g. GET, POST)</li>
 *   <li>{@code http.route} – Matched route template (e.g. /hello, /users/{id})</li>
 * </ul>
 *
 * @see <a href="https://opentelemetry.io/docs/specs/semconv/http/http-spans/">HTTP spans semantic conventions</a>
 */
@Configuration
public class HttpSpanAttributesConfig implements WebMvcConfigurer {

    private static final AttributeKey<String> HTTP_REQUEST_METHOD = AttributeKey.stringKey("http.request.method");
    private static final AttributeKey<String> HTTP_ROUTE = AttributeKey.stringKey("http.route");

    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(new org.springframework.web.servlet.HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                Span span = Span.current();
                if (!span.getSpanContext().isValid()) {
                    return true;
                }
                span.setAttribute(HTTP_REQUEST_METHOD, request.getMethod());
                Optional.ofNullable(resolveRoute(request)).ifPresent(route -> span.setAttribute(HTTP_ROUTE, route));
                return true;
            }
        });
    }

    @Nullable
    private static String resolveRoute(HttpServletRequest request) {
        // Spring sets this when the handler is matched (RequestMappingHandlerMapping)
        String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null && !pattern.isEmpty()) {
            return pattern;
        }
        // Fallback: use path info so something is recorded (e.g. for non-@RequestMapping handlers)
        String pathInfo = request.getPathInfo();
        if (pathInfo != null && !pathInfo.isEmpty()) {
            return pathInfo;
        }
        String servletPath = request.getServletPath();
        return (servletPath != null && !servletPath.isEmpty()) ? servletPath : null;
    }
}
