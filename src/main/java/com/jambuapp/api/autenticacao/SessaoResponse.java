package com.jambuapp.api.autenticacao;

import java.time.Instant;

public record SessaoResponse(String token, Instant expiraEm) {}
