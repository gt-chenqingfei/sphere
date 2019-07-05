package com.beastbikes.android.sphere.restful.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Represent the parameter in the URI path component
 *
 * @author johnsonlee
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface PathParameter {

    /**
     * The parameter name
     */
    String value();

}
