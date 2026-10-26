package vn.edu.usth.online_music_player.backend.auth;

import java.io.IOException;
import java.security.Principal;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class BearerTokenFilter extends OncePerRequestFilter {
    private final TokenService tokens;
    private final AuthService auth;
    public BearerTokenFilter(TokenService tokens, AuthService auth) { this.tokens = tokens; this.auth = auth; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || request.getRequestURI().startsWith("/auth/")) { chain.doFilter(request, response); return; }
        TokenService.Identity identity;
        try {
            if (!header.startsWith("Bearer ")) throw new IllegalArgumentException();
            identity = tokens.verify(header.substring(7));
            if (!auth.validIdentity(identity)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"invalid_session\",\"message\":\"Phiên đã hết hạn. Vui lòng đăng nhập lại.\",\"details\":{},\"request_id\":\"" + java.util.UUID.randomUUID() + "\"}");
            return;
        }
        Principal principal = () -> identity.userId().toString();
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public Principal getUserPrincipal() { return principal; }
            @Override public String getRemoteUser() { return principal.getName(); }
        }, response);
    }
}
