package com.github.legacycode.jakarta.persistence.common;


import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.CDI;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.SingularAttribute;


@ApplicationScoped
public class DynamicDAO implements Serializable {

    private transient final EntityManager em;

    @Inject
    public DynamicDAO(final EntityManager em) {
        this.em = em;
    }

    public static DynamicDAO get() {
        return CDI.current().select(DynamicDAO.class).get();
    }

    public <E> void remove(final E entity) {
        var util = this.em.getEntityManagerFactory().getPersistenceUnitUtil();
        var entityClass = entity.getClass();
        var id = util.getIdentifier(entity);
        var attachedEntity = this.em.getReference(entityClass, id);
        this.em.remove(attachedEntity);
    }

    public <E> void remove(final Class<E> entityClass, final Object id) {
        var attachedEntity = this.em.getReference(entityClass, id);
        this.em.remove(attachedEntity);
    }

    public <E> E save(final E entity) {
        E managedEntity;
        if (this.em.contains(entity)) {
            managedEntity = this.em.merge(entity);
        } else {
            this.em.persist(entity);
            managedEntity = entity;
        }
        return managedEntity;
    }

    public <E> Optional<E> find(final Class<E> entityClass, final Object id) {
        var entity = this.em.find(entityClass, id);
        return Optional.ofNullable(entity);
    }

    public <E> List<E> find(
            final Class<E> entityClass,
            final CriteriaPredicate<E, E> where) {

        return createQuery(this.em, entityClass, where)
                .getResultList();
    }

    public <E> List<E> find(
            final Class<E> entityClass,
            final Set<DynamicQuery> queries) {

        var distinct = Queries.isDistinct(queries);
        CriteriaPredicate<E, E> predicate = (b, r, q) -> {
            q.distinct(distinct);
            q.select(r);
            var orders = buildOrder(queries, b, r);
            q.orderBy(orders);
            return buildPredicate(em, entityClass, b, r, queries);
        };

        var pageSize = Queries.getPageSize(queries);
        var pageNumber = Queries.getPageNumber(queries);
        var startPosition = Math.max(0, (pageNumber - 1) * pageSize);

        return createQuery(this.em, entityClass, predicate)
                .setFirstResult(startPosition)
                .setMaxResults(pageSize)
                .getResultList();
    }

    public <E> long size(final Class<E> entityClass) {
        CriteriaPredicate<E, Long> predicate = (b, r, q) -> {
            q.select(b.count(r));
            return b.and();
        };

        return createQuery(this.em, entityClass, Long.class, predicate)
                .getSingleResult();
    }

    public <E> long size(
            final Class<E> entityClass,
            final Set<DynamicQuery> queries) {

        CriteriaPredicate<E, Long> predicate = (b, r, q) -> {
            q.select(b.count(r));
            return buildPredicate(em, entityClass, b, r, queries);
        };

        return createQuery(this.em, entityClass, Long.class, predicate)
                .getSingleResult();
    }

    public <E> boolean contains(E entity) {
        var entityClass = (Class<E>) entity.getClass();
        var cb = this.em.getCriteriaBuilder();
        var cq = cb.createQuery(Long.class);
        var root = cq.from(entityClass);
        cq.select(cb.count(root));
        var puu = this.em.getEntityManagerFactory().getPersistenceUnitUtil();
        var id = puu.getIdentifier(entity);
        var attribut = getPrimaryKeyAttribut(this.em, entityClass);
        var p0 = cb.equal(root.get(attribut), id);
        cq.where(p0);
        return this.em.createQuery(cq).getSingleResult() > 0;
    }

    private static <E> SingularAttribute<E, ?> getPrimaryKeyAttribut(
            final EntityManager em,
            final Class<E> entityClass) {

        return em.getMetamodel()
                .entity(entityClass)
                .getDeclaredSingularAttributes()
                .stream()
                .filter(SingularAttribute::isId)
                .findFirst()
                .orElseThrow(() -> new PersistenceException("No primary key attribut found in class: " + entityClass));
    }

    private static <E, R> TypedQuery<R> createQuery(
            final EntityManager em,
            final Class<E> entityClass,
            final Class<R> targetClass,
            final CriteriaPredicate<E, R> where) {

        var builder = em.getCriteriaBuilder();
        var query = builder.createQuery(targetClass);
        var root = query.from(entityClass);
        var predicate = where.toPredicate(builder, root, query);
        query.where(predicate);
        return em.createQuery(query);
    }

    private static <E> TypedQuery<E> createQuery(
            final EntityManager em,
            final Class<E> entityClass,
            final CriteriaPredicate<E, E> where) {

        return createQuery(em, entityClass, entityClass, where);
    }


    private static <E> List<Order> buildOrder(
            final Set<DynamicQuery> queries,
            final CriteriaBuilder builder,
            final Root<E> root) {

        return queries
                .stream()
                .filter(DynamicQuery::isSortQuery)
                .map(DynamicQuery::getSortedValues)
                .map(Map::entrySet)
                .map(Set::stream)
                .map(e -> (Map.Entry<String, Boolean>) e)
                .map(e -> buildOrder(e, builder, root))
                .collect(Collectors.toList());
    }

    private static <E> Order buildOrder(
            final Map.Entry<String, Boolean> attribute,
            final CriteriaBuilder builder,
            final Root<E> root) {

        Order order;
        if (Objects.equals(attribute.getValue(), Boolean.TRUE)) {
            order = builder.asc(root.get(attribute.getKey()));
        } else {
            order = builder.desc(root.get(attribute.getKey()));
        }
        return order;
    }

    private static <E> Predicate buildPredicate(
            final EntityManager em,
            final Class<E> entityClass,
            final CriteriaBuilder builder,
            final Root<E> root,
            final Set<DynamicQuery> queries) {


        return builder.and(queries
                .stream()
                .filter(q -> isSafeSearchQuery(em, entityClass, q))
                .map(q -> buildPredicate(builder, root, q))
                .filter(Optional::isPresent)
                .flatMap(Optional::stream)
                .toArray(Predicate[]::new)
        );
    }

    private static <X> Optional<Predicate> buildPredicate(
            final CriteriaBuilder builder,
            final Root<X> root,
            final DynamicQuery query) {

        Predicate predicate;
        if (!query.isBasicQuery()) {
            switch (query.getOperator()) {
                case EQUAL:
                    predicate = DynamicPredicate.equal(builder, root, query);
                    break;
                case NOT_EQUAL:
                    predicate = DynamicPredicate.notEqual(builder, root, query);
                    break;
                case LIKE:
                    predicate = DynamicPredicate.like(builder, root, query);
                    break;
                case NOT_LIKE:
                    predicate = DynamicPredicate.notLike(builder, root, query);
                    break;
                case GREATER_THAN:
                    predicate = DynamicPredicate.greaterThan(builder, root, query);
                    break;
                case GREATER_THAN_OR_EQUAL:
                    predicate = DynamicPredicate.greaterThanOrEqual(builder, root, query);
                    break;
                case LESS_THAN:
                    predicate = DynamicPredicate.lessThan(builder, root, query);
                    break;
                case LESS_THAN_OR_EQUAL:
                    predicate = DynamicPredicate.lessThanOrEqual(builder, root, query);
                    break;
                case IN:
                    predicate = DynamicPredicate.in(builder, root, query);
                    break;
                case NOT_IN:
                    predicate = DynamicPredicate.notIn(builder, root, query);
                    break;
                case BETWEEN:
                    predicate = DynamicPredicate.between(builder, root, query);
                    break;
                case NOT_BETWEEN:
                    predicate = DynamicPredicate.notBetween(builder, root, query);
                    break;
                default:
                    predicate = null;
                    break;
            }
        } else if (query.isKeywordQuery()) {
            predicate = DynamicPredicate.keyword(builder, root, query);
        } else {
            predicate = null;
        }
        return Optional.ofNullable(predicate);
    }

    private static <E> boolean isSafeSearchQuery(
            final EntityManager em,
            final Class<E> entityClass,
            final DynamicQuery query) {

        return em
                .getMetamodel()
                .entity(entityClass)
                .getAttributes()
                .stream()
                .filter(a -> Objects.equals(a.getPersistentAttributeType(), Attribute.PersistentAttributeType.BASIC))
                .anyMatch(a -> Objects.equals(a.getName(), query.getName()));
    }

}
