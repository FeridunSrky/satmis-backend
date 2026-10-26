package com.satmis.common;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;

@Getter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Compares persistent classes so a lazy proxy equals its initialized entity
    @Override
    public final boolean equals(Object other) {
        if (this == other) return true;
        if (other == null) return false;
        if (effectiveClass(this) != effectiveClass(other)) return false;
        BaseEntity that = (BaseEntity) other;
        // getId() rather than the field: on a proxy the field is unset and only the getter is delegated
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    // Constant per class so the hash does not change when the id is assigned on persist
    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }

    private static Class<?> effectiveClass(Object entity) {
        return entity instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : entity.getClass();
    }
}
