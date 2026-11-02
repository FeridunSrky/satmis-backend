package com.satmis.listing;

import com.satmis.enums.CurrencyType;
import com.satmis.listing.dto.SalePriceSummaryResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.data.support.PageableExecutionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    @Override
    public List<SalePriceSummaryResponse.CurrencyStats> summarizeSalePrices(Specification<Listing> specification) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = builder.createTupleQuery();
        Root<Listing> root = query.from(Listing.class);
        Path<CurrencyType> currency = root.get("currency");
        Path<BigDecimal> salePrice = root.get("salePrice");
        Expression<Long> count = builder.count(root);
        Expression<Double> average = builder.avg(salePrice);
        Expression<BigDecimal> min = builder.min(salePrice);
        Expression<BigDecimal> max = builder.max(salePrice);
        query.multiselect(currency, count, average, min, max)
                .where(specification.toPredicate(root, query, builder))
                .groupBy(currency)
                .orderBy(builder.desc(count), builder.asc(currency));

        return entityManager.createQuery(query).getResultList().stream()
                .map(row -> new SalePriceSummaryResponse.CurrencyStats(row.get(currency), row.get(count),
                        BigDecimal.valueOf(row.get(average)).setScale(2, RoundingMode.HALF_UP),
                        row.get(min), row.get(max)))
                .toList();
    }

    private long count(Specification<Listing> specification) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<Listing> root = query.from(Listing.class);
        query.select(builder.count(root)).where(specification.toPredicate(root, query, builder));
        return entityManager.createQuery(query).getSingleResult();
    }
}
