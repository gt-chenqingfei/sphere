package com.beastbikes.android.sphere.restful;

/**
 * Represent the target of RESTful service
 *
 * @author johnsonlee
 * @since 1.0.0
 */
public class InvocationTarget {

    public final String url;

    public final String method;

    public InvocationTarget(final String url) {
        this(url, "GET");
    }

    public InvocationTarget(final String url, final String method) {
        this.url = url;
        this.method = method;
    }

    @Override
    public String toString() {
        return this.method + " " + this.url;
    }

}
