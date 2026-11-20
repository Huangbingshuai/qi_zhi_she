package com.tongji.search.index;

/** Published after a know post transaction commits so Elasticsearch reflects committed data. */
public record KnowPostIndexEvent(long postId, boolean deleted) {}
