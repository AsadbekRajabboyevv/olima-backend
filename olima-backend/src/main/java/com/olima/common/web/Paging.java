package com.olima.common.web;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Paging {

  public static final String TOTAL_COUNT = "X-Total-Count";
  public static final String PAGE = "X-Page";
  public static final String PAGE_SIZE = "X-Page-Size";

  private final PaginationProperties properties;

  public Pageable of(Integer page, Integer size, Sort sort) {
    int p = page == null || page < 0 ? 0 : page;
    int s =
        size == null || size < 1 ? properties.defaultSize() : Math.min(size, properties.maxSize());
    return PageRequest.of(p, s, sort);
  }

  public static <T> ResponseEntity<List<T>> ok(Page<T> page) {
    return ResponseEntity.ok()
        .header(TOTAL_COUNT, String.valueOf(page.getTotalElements()))
        .header(PAGE, String.valueOf(page.getNumber()))
        .header(PAGE_SIZE, String.valueOf(page.getSize()))
        .body(page.getContent());
  }
}
