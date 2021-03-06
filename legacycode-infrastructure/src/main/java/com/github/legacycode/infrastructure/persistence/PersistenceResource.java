package com.github.legacycode.infrastructure.persistence;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Singleton
public class PersistenceResource {

    @Produces
    @PersistenceContext
    private EntityManager em;

    public PersistenceResource() {
    }

}
