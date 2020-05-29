package com.ahsailabs.sqlwannotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by ahmad s on 2020-05-25.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Index {
    String first() default "";
    String second() default "";
    String third() default "";
    String forth() default "";
    String fifth() default "";
}
