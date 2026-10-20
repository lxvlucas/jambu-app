package com.jambuapp.api.meal;

import java.util.List;

public record PaginaCursor<T>(List<T> itens, String proximoCursor) {}
