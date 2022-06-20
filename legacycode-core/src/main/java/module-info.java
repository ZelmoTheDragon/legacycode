module legacy.core {

    requires java.base;
    requires java.sql;

    exports com.github.legacycode.core;
    exports com.github.legacycode.core.repository;
    exports com.github.legacycode.core.service;
    exports com.github.legacycode.core.customer;
    exports com.github.legacycode.core.gender;
    exports com.github.legacycode.core.util;
    exports com.github.legacycode.core.util.lang;
    exports com.github.legacycode.core.util.security;
    exports com.github.legacycode.core.util.validation;

    exports com.github.legacycode.internal.persistence.collection;
    exports com.github.legacycode.internal.persistence.jdbc;
    exports com.github.legacycode.internal.service;

}
