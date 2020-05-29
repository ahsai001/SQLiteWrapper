package com.ahsailabs.sqlwannotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by ahmad s on 2020-05-23.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface Column {
    String name() default "";
    boolean index() default false;
    boolean unique() default false;
    boolean notNull() default false;
}
