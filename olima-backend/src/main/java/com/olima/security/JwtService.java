package com.olima.security;

import com.olima.security.model.IssuedToken;
import com.olima.security.model.ParsedToken;
import com.olima.user.UserEntity;
import io.jsonwebtoken.JwtException;

public interface JwtService {

  IssuedToken generateToken(UserEntity user);

  ParsedToken parseToken(String token) throws JwtException;
}
