package com.doneit.user.web;

import com.doneit.config.DeviceRememberMeServices;
import com.doneit.support.IntegrationTestSupport;
import com.doneit.user.application.RememberedDevicesService;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.test.web.servlet.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RememberedLoginTest extends IntegrationTestSupport {
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwords;
    @Autowired SessionRegistry registry;
    @Autowired UserDetailsService users;
    @Autowired PersistentTokenRepository tokens;
    @Autowired RememberedDevicesService devices;
    @BeforeEach void setup() {
        db.execute("TRUNCATE users CASCADE");
        for(Object principal:registry.getAllPrincipals())
            for(var session:registry.getAllSessions(principal,true)) registry.removeSessionInformation(session.getSessionId());
        for(String name:new String[]{"owner","katya"}) db.update("INSERT INTO users(login,password_hash,created_at,updated_at) VALUES(?,?,NOW(),NOW())",name,passwords.encode("test-password"));
    }
    @Test void optInStoresRotatingTokenAndHttpOnlyLaxCookieFor90Days() throws Exception {
        var login=login("owner",true,false,null);
        Cookie cookie=login.getResponse().getCookie("remember-me");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getMaxAge()).isEqualTo(90*24*60*60);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isFalse();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(count("owner")).isEqualTo(1);
        var restored=mvc.perform(get("/settings/devices").cookie(cookie)).andExpect(authenticated().withUsername("owner"))
                .andExpect(status().isOk()).andReturn();
        Cookie rotated=restored.getResponse().getCookie("remember-me");
        assertThat(rotated.getValue()).isNotEqualTo(cookie.getValue());
        assertThat(count("owner")).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM remembered_device_sessions",Long.class)).isEqualTo(2);
    }
    @Test void httpsCookieIsSecureAndPlainLoginDoesNotCreateAToken() throws Exception {
        var secure=login("owner",true,true,null).getResponse().getCookie("remember-me");
        assertThat(secure.getSecure()).isTrue();
        var normal=login("katya",false,false,null);
        assertThat(count("katya")).isZero();
        assertThat(normal.getResponse().getCookie("remember-me").getMaxAge()).isZero();
    }
    @Test void freshServiceInstanceCanRestoreFromDatabaseWithoutAnOldSessionOrKey() throws Exception {
        Cookie cookie=login("owner",true,false,null).getResponse().getCookie("remember-me");
        var fresh=new DeviceRememberMeServices("new-instance-key",users,tokens,devices);
        var request=new MockHttpServletRequest();request.setCookies(cookie);
        var authentication=fresh.autoLogin(request,new MockHttpServletResponse());
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("owner");
    }
    @Test void expiredMalformedAndMissingTokensCannotRestoreLogin() throws Exception {
        Cookie cookie=login("owner",true,false,null).getResponse().getCookie("remember-me");
        db.update("UPDATE persistent_logins SET last_used=NOW()-INTERVAL '91 days'");
        mvc.perform(get("/").cookie(cookie)).andExpect(unauthenticated()).andExpect(status().is3xxRedirection());
        mvc.perform(get("/").cookie(new Cookie("remember-me","not-base64!"))).andExpect(unauthenticated());
        db.update("DELETE FROM persistent_logins");
        mvc.perform(get("/").cookie(cookie)).andExpect(unauthenticated());
    }
    @Test void logoutRevokesOnlyTheCurrentDevice() throws Exception {
        var first=login("owner",true,false,null);var second=login("owner",true,false,null);
        Cookie firstCookie=first.getResponse().getCookie("remember-me"),secondCookie=second.getResponse().getCookie("remember-me");
        mvc.perform(post("/logout").session(session(first)).cookie(firstCookie).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(count("owner")).isEqualTo(1);
        mvc.perform(get("/").cookie(firstCookie)).andExpect(unauthenticated());
        mvc.perform(get("/").cookie(secondCookie)).andExpect(authenticated().withUsername("owner"));
    }
    @Test void revokeOtherDevicesExpiresTheirLiveSessionsButKeepsCurrentAndOtherUsers() throws Exception {
        var current=login("owner",true,false,null);var other=login("owner",true,false,null);var katya=login("katya",true,false,null);
        var currentCookie=current.getResponse().getCookie("remember-me");
        mvc.perform(post("/settings/devices/revoke-others").session(session(current)).cookie(currentCookie).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(count("owner")).isEqualTo(1);
        assertThat(count("katya")).isEqualTo(1);
        assertThat(registry.getSessionInformation(session(other).getId()).isExpired()).isTrue();
        assertThat(registry.getSessionInformation(session(current).getId()).isExpired()).isFalse();
        assertThat(registry.getSessionInformation(session(katya).getId()).isExpired()).isFalse();
        mvc.perform(get("/settings/devices").session(session(other)).cookie(other.getResponse().getCookie("remember-me")))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?expired"));
        mvc.perform(get("/").cookie(other.getResponse().getCookie("remember-me"))).andExpect(unauthenticated());
    }
    @Test void deviceRevocationIsOwnedCsrfProtectedAndAlsoExpiresActiveSession() throws Exception {
        var owner=login("owner",true,false,null);var katya=login("katya",true,false,null);
        Long ownerId=id("owner"),katyaId=id("katya");
        mvc.perform(post("/settings/devices/"+katyaId+"/revoke").session(session(owner)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/settings/devices/"+ownerId+"/revoke").session(session(owner)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/settings/devices/"+ownerId+"/revoke").session(session(owner)).cookie(owner.getResponse().getCookie("remember-me")).with(csrf()))
                .andExpect(redirectedUrl("/login?logout"));
        assertThat(count("owner")).isZero();
        assertThat(count("katya")).isEqualTo(1);
    }
    @Test void replayedOldCookieRevokesAllRememberedTokensAndSessionsForThatUser() throws Exception {
        var login=login("owner",true,false,null);Cookie old=login.getResponse().getCookie("remember-me");
        mvc.perform(get("/settings/devices").cookie(old)).andExpect(authenticated());
        mvc.perform(get("/").cookie(old)).andExpect(unauthenticated());
        assertThat(count("owner")).isZero();
        assertThat(registry.getSessionInformation(session(login).getId()).isExpired()).isTrue();
    }
    @Test void devicesPageDoesNotExposeSecretsAndRepeatedPasswordLoginReplacesItsOldToken() throws Exception {
        var first=login("owner",true,false,null);Cookie cookie=first.getResponse().getCookie("remember-me");
        String token=db.queryForObject("SELECT token FROM persistent_logins WHERE username='owner'",String.class);
        var page=mvc.perform(get("/settings/devices").session(session(first)).cookie(cookie)).andExpect(status().isOk()).andReturn();
        assertThat(page.getResponse().getContentAsString()).doesNotContain(token,cookie.getValue());
        var second=login("owner",true,false,cookie);
        assertThat(count("owner")).isEqualTo(1);
        login("owner",false,false,second.getResponse().getCookie("remember-me"));
        assertThat(count("owner")).isZero();
    }
    @Test void passwordChangeRevokesRememberedTokensAndTheirExistingSessions() throws Exception {
        var login=login("owner",true,false,null);
        db.update("UPDATE users SET password_hash=? WHERE login='owner'",passwords.encode("new-password"));
        assertThat(count("owner")).isZero();
        mvc.perform(get("/settings/devices").session(session(login)).cookie(login.getResponse().getCookie("remember-me")))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?expired"));
    }
    @Test void logoutStillRevokesTheBoundDeviceWhenItsCookieIsMissing() throws Exception {
        var login=login("owner",true,false,null);
        mvc.perform(post("/logout").session(session(login)).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(count("owner")).isZero();
    }
    @Test void revokeOthersKeepsTheCurrentSessionWhenItsRememberCookieWasCleared() throws Exception {
        var current=login("owner",true,false,null);login("owner",true,false,null);
        mvc.perform(post("/settings/devices/revoke-others").session(session(current)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(count("owner")).isEqualTo(1);
        mvc.perform(get("/settings/devices").session(session(current))).andExpect(status().isOk());
    }
    private MvcResult login(String username,boolean remember,boolean secure,Cookie cookie) throws Exception {
        var request=post("/login").param("username",username).param("password","test-password").secure(secure)
                .header("User-Agent","Mozilla/5.0 (iPhone) Version/18.0 Safari/605.1.15").with(csrf());
        if(remember) request.param("remember-me","true");
        if(cookie!=null) request.cookie(cookie);
        return mvc.perform(request).andExpect(authenticated().withUsername(username)).andReturn();
    }
    private MockHttpSession session(MvcResult result) {return (MockHttpSession)result.getRequest().getSession(false);}
    private long count(String username) {return db.queryForObject("SELECT COUNT(*) FROM persistent_logins WHERE username=?",Long.class,username);}
    private Long id(String username) {return db.queryForObject("SELECT id FROM persistent_logins WHERE username=?",Long.class,username);}
}
