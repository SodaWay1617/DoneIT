package com.doneit.user.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import java.security.Principal;

@Controller
public class CsrfController {
    @GetMapping("/api/csrf")
    public ResponseEntity<Token> token(CsrfToken csrf, Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Token(csrf.getParameterName(), csrf.getToken(), principal.getName()));
    }

    public record Token(String parameterName, String token, String username) {}
}
