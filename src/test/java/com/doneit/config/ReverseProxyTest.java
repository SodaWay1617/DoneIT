package com.doneit.config;

import com.doneit.user.application.RememberedDevicesService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.web.filter.ForwardedHeaderFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ReverseProxyTest {
    @Test
    void proxyHeadersProduceHttpsRedirectAndSecureRememberMeCookie() throws Exception {
        var request = new MockHttpServletRequest("GET", "/settings");
        request.setServerName("app");
        request.setServerPort(8080);
        request.addHeader("Host", "done-it-all.ru");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "done-it-all.ru");
        request.addHeader("X-Forwarded-Port", "443");
        request.addHeader("Authorization", "Bearer test-value");
        request.addHeader("MCP-Protocol-Version", "2025-03-26");
        var response = new MockHttpServletResponse();
        var remember = new DeviceRememberMeServices("test-key", mock(UserDetailsService.class),
                mock(PersistentTokenRepository.class), mock(RememberedDevicesService.class)) {
            void issue(HttpServletRequest req, HttpServletResponse res) {
                setCookie(new String[]{"series", "token"}, 3600, req, res);
            }
        };
        new ForwardedHeaderFilter().doFilter(request, response, (req, res) -> {
            var httpRequest = (HttpServletRequest) req;
            var httpResponse = (HttpServletResponse) res;
            assertThat(httpRequest.isSecure()).isTrue();
            assertThat(httpRequest.getHeader("Authorization")).isEqualTo("Bearer test-value");
            assertThat(httpRequest.getHeader("MCP-Protocol-Version")).isEqualTo("2025-03-26");
            remember.issue(httpRequest, httpResponse);
            httpResponse.sendRedirect("/login");
        });
        assertThat(response.getRedirectedUrl()).isEqualTo("https://done-it-all.ru/login");
        var cookie = response.getCookie("remember-me");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
    }
}
