package com.brixo.slidehub.gateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Preserva el X-Forwarded-Host del cliente original (slide.lat) para que
 * cuando Spring Cloud Gateway MVC haga proxy a los servicios downstream,
 * estos reciban el host público correcto en vez del hostname interno de Render.
 *
 * Render's load balancer ya pone X-Forwarded-Host cuando el request llega
 * al gateway, pero Spring's ForwardedHeaderFilter (framework strategy)
 * lo consume y lo aplica al request — luego cuando el gateway proxies
 * a ui-service, el header ya no está. Este filtro lo re-inyecta.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "slidehub.base-url")
public class ForwardedHeadersFilter extends OncePerRequestFilter {

    private final String host;
    private final String proto;

    public ForwardedHeadersFilter(@Value("${slidehub.base-url}") String baseUrl) {
        if (baseUrl.contains("://")) {
            this.proto = baseUrl.substring(0, baseUrl.indexOf("://"));
            this.host = baseUrl.substring(baseUrl.indexOf("://") + 3);
        } else {
            this.proto = "https";
            this.host = baseUrl;
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpServletRequest wrapped = new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                if ("X-Forwarded-Host".equalsIgnoreCase(name)) return host;
                if ("X-Forwarded-Proto".equalsIgnoreCase(name)) return proto;
                if ("X-Forwarded-Port".equalsIgnoreCase(name)) return "443";
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                if ("X-Forwarded-Host".equalsIgnoreCase(name)) return Collections.enumeration(List.of(host));
                if ("X-Forwarded-Proto".equalsIgnoreCase(name)) return Collections.enumeration(List.of(proto));
                if ("X-Forwarded-Port".equalsIgnoreCase(name)) return Collections.enumeration(List.of("443"));
                return super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                List<String> names = Collections.list(super.getHeaderNames());
                if (!names.contains("X-Forwarded-Host")) names.add("X-Forwarded-Host");
                if (!names.contains("X-Forwarded-Proto")) names.add("X-Forwarded-Proto");
                if (!names.contains("X-Forwarded-Port")) names.add("X-Forwarded-Port");
                return Collections.enumeration(names);
            }
        };
        filterChain.doFilter(wrapped, response);
    }
}
