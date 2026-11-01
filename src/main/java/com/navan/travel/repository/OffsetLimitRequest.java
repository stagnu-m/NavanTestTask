package com.navan.travel.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class OffsetLimitRequest implements Pageable {

    private final int limit;
    private final long offset;
    private final Sort sort;

    public OffsetLimitRequest(int limit, long offset, Sort sort) {
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be greater than zero");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset index must not be less than zero");
        }
        this.limit = limit;
        this.offset = offset;
        this.sort = sort != null ? sort : Sort.unsorted();
    }

    public OffsetLimitRequest(int limit, long offset) {
        this(limit, offset, Sort.by(Sort.Direction.ASC, "id"));
    }

    @Override
    public int getPageNumber() {
        return (int) (offset / limit);
    }

    @Override
    public int getPageSize() {
        return limit;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new OffsetLimitRequest(getPageSize(), getOffset() + getPageSize(), getSort());
    }

    public Pageable previous() {
        return hasPrevious() ? new OffsetLimitRequest(getPageSize(), getOffset() - getPageSize(), getSort()) : this;
    }

    @Override
    public Pageable previousOrFirst() {
        return hasPrevious() ? previous() : first();
    }

    @Override
    public Pageable first() {
        return new OffsetLimitRequest(getPageSize(), 0, getSort());
    }

    @Override
    public Pageable withPage(int pageNumber) {
        return new OffsetLimitRequest(getPageSize(), (long) pageNumber * getPageSize(), getSort());
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OffsetLimitRequest that)) return false;
        return limit == that.limit && offset == that.offset && sort.equals(that.sort);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(limit, offset, sort);
    }
}
