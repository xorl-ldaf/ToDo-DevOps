package com.example.todo.adapter.out.persistence.exception;

import org.springframework.dao.DataAccessException;

import java.util.function.Supplier;

public final class PersistenceAdapterFailures {
    private PersistenceAdapterFailures() {
    }

    public static <T> T execute(String operation, Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (PersistenceAdapterException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new PersistenceAdapterException(operation + " failed", exception);
        }
    }
}
