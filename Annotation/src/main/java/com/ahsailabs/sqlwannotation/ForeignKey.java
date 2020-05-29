package com.ahsailabs.sqlwannotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by ahmad s on 2020-05-29.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface ForeignKey {
    String parentTableName();
    String parentColumnName();
    String onUpdate() default "NO ACTION";
    String onDelete() default "NO ACTION";
}
