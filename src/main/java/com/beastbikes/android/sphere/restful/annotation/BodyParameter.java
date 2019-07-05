package com.beastbikes.android.sphere.restful.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Represent the parameter in the request body
 *
 * @author johnsonlee
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface BodyParameter {

    /**
     * The parameter name
     */
    String value();

}
