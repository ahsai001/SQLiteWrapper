package com.ahsailabs.sqlwannotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by ahmad s on 2020-05-23.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Table {
    String name() default "";
    boolean recordLog() default true;
    boolean softDelete() default false;
}
