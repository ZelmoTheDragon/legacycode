package com.github.legacycode.jakarta.persistence;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;

import com.github.legacycode.core.LegacyCode;

interface EntityMapper<E, D> {

    E toEntity(D data);

    D fromEntity(E entity);

    void updateEntity(D data, E entity);

    default Class<E> getEntityClass() {
        var type = this.getClass().getGenericInterfaces()[0];
        var genericType = (ParameterizedType) type;
        return (Class<E>) genericType.getActualTypeArguments()[0];
    }

    default Class<D> getDataClass() {
        var type = this.getClass().getGenericInterfaces()[0];
        var genericType = (ParameterizedType) type;
        return (Class<D>) genericType.getActualTypeArguments()[1];
    }

    default E findReference(Object key) {
        var dao = LegacyCode.getCurrentContext().get(DynamicDAO.class);
        return dao
                .findReference(getEntityClass(), key)
                .orElseGet(this::newInstance);

    }

    private E newInstance() {
        var entityClass = getEntityClass();
        try {
            return entityClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | NoSuchMethodException | IllegalAccessException |
                 InvocationTargetException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
