package com.beastbikes.android.sphere.restful;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.text.TextUtils;

import com.beastbikes.android.sphere.restful.annotation.BodyParameter;
import com.beastbikes.android.sphere.restful.annotation.HttpDelete;
import com.beastbikes.android.sphere.restful.annotation.HttpGet;
import com.beastbikes.android.sphere.restful.annotation.HttpPost;
import com.beastbikes.android.sphere.restful.annotation.HttpPut;
import com.beastbikes.android.sphere.restful.annotation.MatrixParameter;
import com.beastbikes.android.sphere.restful.annotation.Path;
import com.beastbikes.android.sphere.restful.annotation.PathParameter;
import com.beastbikes.android.sphere.restful.annotation.QueryParameter;
import com.beastbikes.android.sphere.restful.cache.APICache;
import com.beastbikes.framework.android.utils.ConnectivityUtils;

import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;


public class ServiceStubInvocation implements Invocation {

    public static final String ENCODING_GZIP = "gzip";
    private static final String PARAMETER_SEPARATOR = "&";
    private static final String NAME_VALUE_SEPARATOR = "=";
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger("ServiceStubInvocation");

    final Context context;

    final Class<?> iface;

    final Method method;

    final String baseUrl;

    final Map<String, String> headers;

    final InvocationExpireListener expireListener;

    ServiceStubInvocation(final Context context, final Class<?> iface, final Method method,
                          final String baseUrl, InvocationExpireListener expireListener) {
        this(context, iface, method, baseUrl, Collections.<String, String>emptyMap(), expireListener);
    }

    ServiceStubInvocation(final Context context, final Class<?> iface, final Method method,
                          final String baseUrl, final Map<String, String> headers,
                          InvocationExpireListener expireListener) {
        this.context = context;
        this.iface = iface;
        this.method = method;
        this.baseUrl = baseUrl;
        this.headers = null == headers ? Collections.<String, String>emptyMap() : headers;
        this.expireListener = expireListener;
    }

    @Override
    public Object invoke(final Object... args) throws InvocationException {
//        final AndroidHttpClient client = AndroidHttpClient.newInstance(buildUserAgent(context), context);
        final HttpClient client = getNewHttpClient();
        try {
            return this.invoke(client, args);
        } finally {

        }
    }

    private Object invoke(final HttpClient client, final Object... args) throws InvocationException {
        logger.debug("Invoking " + this.iface.getName() + "#" + this.method.getName() + " " + Arrays.toString(args));

        String svcPath = "";
        if (this.iface.isAnnotationPresent(Path.class)) {
            svcPath = this.iface.getAnnotation(Path.class).value();
        }

        final String httpMethod;
        final String servicePath;
        String httpGetEncoding = null;
        if (this.method.isAnnotationPresent(HttpPost.class)) {
            httpMethod = "POST";
            servicePath = this.method.getAnnotation(HttpPost.class).value();
        } else if (this.method.isAnnotationPresent(HttpPut.class)) {
            httpMethod = "PUT";
            servicePath = this.method.getAnnotation(HttpPut.class).value();
        } else if (this.method.isAnnotationPresent(HttpDelete.class)) {
            httpMethod = "DELETE";
            servicePath = this.method.getAnnotation(HttpDelete.class).value();
        } else if (this.method.isAnnotationPresent(HttpGet.class)) {
            httpMethod = "GET";
            servicePath = this.method.getAnnotation(HttpGet.class).value();
            httpGetEncoding = this.method.getAnnotation(HttpGet.class).encoding();
        } else {
            throw new UnsupportedOperationException(this.method.getName()
                    + " does not annotated by any HTTP method");
        }

        final List<NameValuePair> bodyParams = new ArrayList<NameValuePair>();
        final List<NameValuePair> matrixParams = new ArrayList<NameValuePair>();
        final List<NameValuePair> pathParams = new ArrayList<NameValuePair>();
        final List<NameValuePair> queryParams = new ArrayList<NameValuePair>();
        final Annotation[][] paramAnnotations = method.getParameterAnnotations();
        final Map<String, Object> bodyCacheParams = new TreeMap<String, Object>();
        if (null != paramAnnotations && paramAnnotations.length > 0) {
            for (int i = 0; i < paramAnnotations.length; i++) {
                final String value = String.valueOf(args[i]);
                final Annotation[] annotations = paramAnnotations[i];
                if (TextUtils.isEmpty(value) || "null".equals(value)) {
                    continue;
                }
                if (null != annotations && annotations.length > 0) {
                    for (final Annotation annotation : annotations) {
                        final Class<?> annotationType = annotation.annotationType();

                        if (QueryParameter.class.equals(annotationType)) {
                            final String name = ((QueryParameter) annotation).value();
                            queryParams.add(new BasicNameValuePair(name, value));
                        } else if (BodyParameter.class.equals(annotationType)) {
                            final String name = ((BodyParameter) annotation).value();
                            bodyParams.add(new BasicNameValuePair(name, value));
                            bodyCacheParams.put(name, value);
                        } else if (MatrixParameter.class.equals(annotationType)) {
                            final String name = ((MatrixParameter) annotation).value();
                            matrixParams.add(new BasicNameValuePair(name, value));
                        } else if (PathParameter.class.equals(annotationType)) {
                            final String name = ((PathParameter) annotation).value();
                            pathParams.add(new BasicNameValuePair(name, value));
                        }
                    }
                }
            }
        }

        final StringBuilder queryString = new StringBuilder();
        if (queryParams.size() > 0) {
            try {
                queryString.append("?").append(
                        TextUtils.isEmpty(httpGetEncoding)
                                ? format(queryParams)
                                : EntityUtils.toString(
                                new UrlEncodedFormEntity(queryParams, httpGetEncoding)));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        final String apiPath;
        if (pathParams.size() > 0) {
            String path = servicePath;
            for (final NameValuePair nvp : pathParams) {
                path = path.replaceAll("\\{" + nvp.getName() + "\\}", nvp.getValue());
            }
            apiPath = path;
        } else {
            apiPath = servicePath;
        }

        final HttpRequestBase request;
        final String url = baseUrl + svcPath + apiPath + queryString.toString();
        final InvocationTarget target = new InvocationTarget(url, httpMethod);

        logger.debug(target.toString());

        try {
            final URI uri = new URI(url);

            if (this.method.isAnnotationPresent(HttpPost.class)) {
                request = new org.apache.http.client.methods.HttpPost(uri);
            } else if (this.method.isAnnotationPresent(HttpPut.class)) {
                request = new org.apache.http.client.methods.HttpPut(uri);
            } else if (this.method.isAnnotationPresent(HttpDelete.class)) {
                request = new org.apache.http.client.methods.HttpDelete(uri);
            } else {
                request = new org.apache.http.client.methods.HttpGet(uri);
            }
        } catch (final URISyntaxException uriSyntaxException) {
            throw new InvocationException(target, null, uriSyntaxException);
        }

        HttpResponse response = null;
        final NetworkInfo ni = ConnectivityUtils.getActiveNetwork(this.context);
        if (ni != null && ni.isConnected()) {
            try {
                request.setHeader("User-Agent", buildUserAgent(this.context));
                request.setHeader("Accept-Language", Locale.getDefault().getLanguage());
                request.setHeader("Accept-Encoding", ENCODING_GZIP);

                for (final Map.Entry<String, String> entry : this.headers.entrySet()) {
                    request.setHeader(entry.getKey(), entry.getValue());
                }

                if (request instanceof HttpEntityEnclosingRequest) {
                    MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
                    for (int i = 0; i < bodyParams.size(); i++) {
                        StringBody stringBody = new StringBody(bodyParams.get(i).getValue(),
                                ContentType.create("text/plain", Consts.UTF_8));
                        multipartEntityBuilder.addPart(bodyParams.get(i).getName(), stringBody);
                    }
                    for (int i = 0; i < matrixParams.size(); i++) {
                        //把文件转换成流对象FileBody
                        FileBody bin = new FileBody(new File(matrixParams.get(i).getValue()));
                        multipartEntityBuilder.addPart(matrixParams.get(i).getName(), bin);
                    }
                    final HttpEntity entity = multipartEntityBuilder.build();
                    ((HttpEntityEnclosingRequest) request).setEntity(entity);
                }


                if (null == (response = client.execute(request))) {
                    throw new InvocationException(target);
                }
            } catch (final Exception e) {
                throw new InvocationException(target, null, e);
            }

            final StatusLine status = response.getStatusLine();
            if (null == status) {
                throw new InvocationException(target);
            }

            switch (status.getStatusCode()) {
                case 200: {

                    HttpEntity entity = response.getEntity();

                    if (null == entity) {
                        throw new InvocationException(target, status);
                    }
                    final Header encoding = entity.getContentEncoding();
                    if (encoding != null) {
                        for (HeaderElement element : encoding.getElements()) {
                            if (element.getName().equalsIgnoreCase(ENCODING_GZIP)) {
                                response.setEntity(new InflatingEntity(entity));
                                break;
                            }
                        }
                    }

                    entity = response.getEntity();

                    try {
                        String resultEntity = EntityUtils.toString(entity, "UTF-8");
                        APICache.getInstance(this.context).setCachedResult(apiPath,
                                bodyCacheParams, resultEntity);

                        JSONObject result = null;

                        Object object = new JSONTokener(resultEntity).nextValue();
                        if (object != null) {
                            result = (JSONObject) object;

                            if (result.optInt("code") == 1002 && expireListener != null) {
                                expireListener.onInvokeTokenExpire();
                                result.put("message", "");//not tip anonymous on expire
                            }
                        }

                        return result;
                    } catch (final Exception e) {
                        throw new InvocationException(target, status, e);
                    }
                }
                default:
                    logger.debug(status.toString());
                    throw new InvocationException(target, status);
            }
        } else {
            JSONObject object = null;
            String entity = APICache.getInstance(this.context).
                    getCachedResult(apiPath, bodyCacheParams);
            try {
                if (!TextUtils.isEmpty(entity)) {
                    object = (JSONObject) new JSONTokener(entity).nextValue();
                } else {
                    object = new JSONObject();
                }
                object.put("message", context.getString(context.getResources().
                        getIdentifier("network_not_awesome", "string", context.getPackageName())));
                return object;
            } catch (final Exception e) {
                throw new InvocationException(target);
            }
        }
    }

    static String buildUserAgent(final Context context) {
        final String osVersion = "Android/" + Build.VERSION.RELEASE;
        final PackageManager pm = context.getPackageManager();
        final String packageName = context.getPackageName();

        try {
            final String versionName = pm.getPackageInfo(packageName, 0).versionName;
            return osVersion + " " + packageName + "/" + versionName;
        } catch (final Exception e) {
            return osVersion;
        }
    }

    private static HttpClient getNewHttpClient() {
        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);

            SSLSocketFactory sf = new MySSLSocketFactory(trustStore);
            sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            HttpParams params = new BasicHttpParams();

            HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
            HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);

            SchemeRegistry registry = new SchemeRegistry();
            registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            registry.register(new Scheme("https", sf, 443));

            ClientConnectionManager ccm = new ThreadSafeClientConnManager(params, registry);

            HttpConnectionParams.setConnectionTimeout(params, 60 * 1000);
            HttpConnectionParams.setSoTimeout(params, 60 * 1000);
            HttpConnectionParams.setSocketBufferSize(params, 8192);
            HttpClient client = new DefaultHttpClient(ccm, params);

            return client;
        } catch (Exception e) {
            return new DefaultHttpClient();
        }
    }


    private static class MySSLSocketFactory extends SSLSocketFactory {
        SSLContext sslContext = SSLContext.getInstance("TLS");

        public MySSLSocketFactory(KeyStore truststore) throws NoSuchAlgorithmException,
                KeyManagementException, KeyStoreException, UnrecoverableKeyException {
            super(truststore);

            TrustManager tm = new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                }

                public void checkServerTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            };

            sslContext.init(null, new TrustManager[]{tm}, null);
        }


        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose)
                throws IOException, UnknownHostException {
            return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
        }

        @Override
        public Socket createSocket() throws IOException {
            return sslContext.getSocketFactory().createSocket();
        }

    }

    /**
     * Enclosing entity to hold stream of gzip decoded data for accessing HttpEntity contents
     */
    private static class InflatingEntity extends HttpEntityWrapper {

        InputStream wrappedStream;
        PushbackInputStream pushbackStream;
        GZIPInputStream gzippedStream;

        public InflatingEntity(HttpEntity wrapped) {
            super(wrapped);
        }

        @Override
        public InputStream getContent() throws IOException {
            wrappedStream = wrappedEntity.getContent();
            pushbackStream = new PushbackInputStream(wrappedStream, 2);
            if (isInputStreamGZIPCompressed(pushbackStream)) {
                gzippedStream = new GZIPInputStream(pushbackStream);
                return gzippedStream;
            } else {
                return pushbackStream;
            }
        }

        @Override
        public long getContentLength() {
            return wrappedEntity == null ? 0 : wrappedEntity.getContentLength();
        }

        @Override
        public void consumeContent() throws IOException {
            silentCloseInputStream(wrappedStream);
            silentCloseInputStream(pushbackStream);
            silentCloseInputStream(gzippedStream);
            super.consumeContent();
        }

        /**
         * A utility function to close an input stream without raising an exception.
         *
         * @param is input stream to close safely
         */
        public static void silentCloseInputStream(InputStream is) {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                logger.error("Cannot close input stream " + e);
            }
        }
    }

    /**
     * Checks the InputStream if it contains  GZIP compressed data
     *
     * @param inputStream InputStream to be checked
     * @return true or false if the stream contains GZIP compressed data
     * @throws IOException if read from inputStream fails
     */
    private static boolean isInputStreamGZIPCompressed(final PushbackInputStream inputStream)
            throws IOException {
        if (inputStream == null)
            return false;

        byte[] signature = new byte[2];
        int count = 0;
        try {
            while (count < 2) {
                int readCount = inputStream.read(signature, count, 2 - count);
                if (readCount < 0) return false;
                count = count + readCount;
            }
        } finally {
            inputStream.unread(signature, 0, count);
        }
        int streamHeader = ((int) signature[0] & 0xff) | ((signature[1] << 8) & 0xff00);
        return GZIPInputStream.GZIP_MAGIC == streamHeader;
    }

    private static String format(
            final Iterable<? extends NameValuePair> parameters) {
        final StringBuilder result = new StringBuilder();
        for (final NameValuePair parameter : parameters) {
            final String encodedName = parameter.getName();
            final String encodedValue = parameter.getValue();
            if (result.length() > 0)
                result.append(PARAMETER_SEPARATOR);
            result.append(encodedName);
            if (encodedValue != null) {
                result.append(NAME_VALUE_SEPARATOR);
                result.append(encodedValue);
            }
        }
        return result.toString();
    }

}