package com.satmis.listing;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;

@RequiredArgsConstructor
class ListingRepositoryCustomImpl implements ListingRepositoryCustom {

    private static final String ID = "id";

    private final EntityManager entityManager;

    @Override
    public Page<Long> findIds(Specification<Listing> specification, Pageable pageable) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<Listing> root = query.from(Listing.class);
        query.select(root.get(ID))
                .where(specification.toPredicate(root, query, builder))
                .orderBy(QueryUtils.toOrders(pageable.getSort(), root, builder));

        List<Long> ids = entityManager.createQuery(query)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();
        return PageableExecutionUtils.getPage(ids, pageable, () -> count(specification));
    }

    @Override
    public void forceVersionIncrement(Listing listing) {
        entityManager.lock(listing, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    }

    private long count(Specification<Listing> specification) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<Listing> root = query.from(Listing.class);
        query.select(builder.count(root)).where(specification.toPredicate(root, query, builder));
        return entityManager.createQuery(query).getSingleResult();
    }
}
