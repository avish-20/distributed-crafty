package com.avish.distributed_craftly.common_lib.security;

import com.avish.distributed_craftly.common_lib.dto.UserDto;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;

@Component
public class AuthUtil {
    @Value("${jwt.secret-key}")
    private String jwtSecretKey;

    public SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(jwtSecretKey.getBytes(StandardCharsets.UTF_8));
    }
    public String generateAccessToken(JwtUserPrincipal user) {
        return Jwts.builder()
                .subject(user.username())
                .claim("userId", user.userId().toString())
                .claim("name", user.name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 1000*60*100))
                .signWith(getSecretKey())
                .compact();
    }

    public JwtUserPrincipal verifyAccessToken(String token){
        Claims claims = Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseClaimsJws(token)
                .getPayload();

        Long userId = Long.parseLong(claims.get("userId").toString());
        String username = claims.getSubject();
        String name = claims.get("name", String.class);
        return new JwtUserPrincipal(userId, name,username,null, new ArrayList<>());
    }

    public Long getCurrentUserId(){
        @Nullable Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication == null || !(authentication.getPrincipal() instanceof JwtUserPrincipal userPrincipal)){
            throw new AuthenticationCredentialsNotFoundException("No Jwt found");
        }
        return userPrincipal.userId();
    }
}
