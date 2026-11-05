package com.wd.ms.common.security;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 每个 Servlet 服务都是 OAuth2 资源服务器：
 *
 * <ul>
 *   <li>/api/**：用户令牌，登录即可；具体权限用 {@code @PreAuthorize("@perm.has('...')")} 在方法上声明
 *   <li>/internal/**：只接受带 internal scope 的服务令牌，用户令牌访问会得到 403
 *   <li>/actuator/**：放行（演示用；生产应放到单独的管理端口并加鉴权）
 * </ul>
 *
 * <p>排在 Boot 默认的安全配置之前：我们先注册 SecurityFilterChain，Boot 的默认链（表单登录 + 随机密码）就会自动退让。
 */
@AutoConfiguration(
        beforeName = {
            "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
            "org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration"
        })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity
@EnableConfigurationProperties(MsSecurityProperties.class)
@Import(SecurityExceptionAdvice.class)
public class MsSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain msSecurityFilterChain(
            HttpSecurity http, MsSecurityProperties properties, JsonSecurityHandlers handlers) {
        http.csrf(csrf -> csrf.disable())
                // 无状态：不创建 HttpSession，每个请求都靠 token 认证，实例之间不需要会话复制
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/**").permitAll();
                    auth.requestMatchers("/internal/**").hasAuthority("SCOPE_" + TokenClaims.SCOPE_INTERNAL);
                    properties
                            .publicPaths()
                            .forEach(path -> auth.requestMatchers(path).permitAll());
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(handlers)
                        .accessDeniedHandler(handlers))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(handlers).accessDeniedHandler(handlers));
        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public JsonSecurityHandlers jsonSecurityHandlers(JsonMapper jsonMapper) {
        return new JsonSecurityHandlers(jsonMapper);
    }

    /** 带负载均衡的 RestTemplate：取 JWKS 公钥、换服务令牌，都按服务名访问认证服务 */
    @Bean
    @LoadBalanced
    @ConditionalOnMissingBean(name = "msLoadBalancedRestTemplate")
    public RestTemplate msLoadBalancedRestTemplate() {
        return new RestTemplate();
    }

    /**
     * 校验签名、过期时间和签发方。公钥从认证服务的 JWKS 端点按需拉取并缓存；
     * 遇到不认识的 kid（认证服务换了密钥）会自动重新拉取，所以密钥轮换不用重启各服务。
     */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder msJwtDecoder(
            MsSecurityProperties properties, @Qualifier("msLoadBalancedRestTemplate") RestTemplate restTemplate) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
                .restOperations(restTemplate)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    @Bean("perm")
    @ConditionalOnMissingBean(PermissionChecker.class)
    public PermissionChecker permissionChecker() {
        return new PermissionChecker();
    }

    @Bean
    @ConditionalOnProperty(prefix = "ms.security.client", name = "id")
    @ConditionalOnMissingBean
    public ServiceTokenProvider serviceTokenProvider(
            MsSecurityProperties properties,
            @Qualifier("msLoadBalancedRestTemplate") RestTemplate restTemplate,
            Clock clock) {
        return new ServiceTokenProvider(restTemplate, properties.client(), clock);
    }

    /** 本服务声明的所有 HTTP Interface 客户端分组都是调用内部接口，统一带上服务令牌 */
    @Bean
    @ConditionalOnBean(ServiceTokenProvider.class)
    public RestClientHttpServiceGroupConfigurer serviceTokenGroupConfigurer(ServiceTokenProvider tokenProvider) {
        ServiceTokenInterceptor interceptor = new ServiceTokenInterceptor(tokenProvider);
        return groups -> groups.forEachClient((group, builder) -> builder.requestInterceptor(interceptor));
    }

    /** 把 token 里的声明映射成 Spring Security 的权限：权限码原样、角色加 ROLE_、scope 加 SCOPE_ */
    static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(MsSecurityAutoConfiguration::authorities);
        return converter;
    }

    static Collection<GrantedAuthority> authorities(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        stringList(jwt, TokenClaims.PERMISSIONS).forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
        stringList(jwt, TokenClaims.ROLES).forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        stringList(jwt, TokenClaims.SCOPE)
                .forEach(scope -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope)));
        return authorities;
    }

    private static List<String> stringList(Jwt jwt, String claim) {
        Object value = jwt.getClaim(claim);
        if (value instanceof String text) {
            // OAuth2 规范里 scope 是空格分隔的字符串
            return List.of(text.split(" "));
        }
        List<String> list = jwt.getClaimAsStringList(claim);
        return list == null ? List.of() : list;
    }
}
