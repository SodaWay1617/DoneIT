package com.doneit.mcp.security;

import com.doneit.user.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpBearerFilterTest {
    final Clock clock=Clock.fixed(McpTokenVerifierTest.NOW,ZoneOffset.UTC);
    final McpProperties properties=McpTokenVerifierTest.properties(true,McpTokenVerifierTest.NOW.plusSeconds(3600),true);
    final UserRepository users=mock(UserRepository.class);
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    MockHttpServletRequest request(String token) {
        var request=new MockHttpServletRequest("POST","/mcp");request.addHeader("Host","localhost:8080");
        if(token!=null)request.addHeader("Authorization","Bearer "+token);
        request.setContent("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));return request;
    }
    McpBearerFilter filter(){return new McpBearerFilter(properties,new McpTokenVerifier(properties,clock),users,clock);}
    @Test void authenticatedIdentityIsForwardedAndContextDoesNotLeak() throws Exception {
        when(users.findByLogin("owner")).thenReturn(Optional.of(mock(User.class)));
        var response=new MockHttpServletResponse();
        filter().doFilter(request(McpTokenVerifierTest.SECRET),response,(q,r)->{
            assertThat(q.getAttribute(McpIdentity.ATTRIBUTE)).isEqualTo(new McpIdentity("laptop","owner",true));
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("owner");
            assertThat(q.getInputStream().readAllBytes()).isEqualTo("{}".getBytes());
        });
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
    @Test void rejectsMissingTokensOriginsAndLargeBodiesBeforeToolExecution() throws Exception {
        var filter=filter();var response=new MockHttpServletResponse();
        filter.doFilter(request(null),response,(q,r)->fail("No token must not execute"));
        assertThat(response.getStatus()).isEqualTo(401);
        var origin=request(McpTokenVerifierTest.SECRET);origin.addHeader("Origin","https://attacker.example");
        response=new MockHttpServletResponse();filter.doFilter(origin,response,(q,r)->fail("Origin must be rejected"));
        assertThat(response.getStatus()).isEqualTo(403);
        when(users.findByLogin("owner")).thenReturn(Optional.of(mock(User.class)));
        var large=request(McpTokenVerifierTest.SECRET);large.setContent(new byte[65537]);
        response=new MockHttpServletResponse();filter.doFilter(large,response,(q,r)->fail("Body must be rejected"));
        assertThat(response.getStatus()).isEqualTo(413);
    }
}
