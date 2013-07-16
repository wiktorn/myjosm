// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.oauth;

import java.net.MalformedURLException;
import java.net.URL;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.basic.DefaultOAuthProvider;

import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.io.OsmApi;
import org.openstreetmap.josm.tools.CheckParameterUtil;

/**
 * This class manages a set of OAuth parameters.
 * @since 2747
 */
public class OAuthParameters {

    /**
     * The default JOSM OAuth consumer key (created by user josmeditor).
     */
    static public final String DEFAULT_JOSM_CONSUMER_KEY = "F7zPYlVCqE2BUH9Hr4SsWZSOnrKjpug1EgqkbsSb";
    /**
     * The default JOSM OAuth consumer secret (created by user josmeditor).
     */
    static public final String DEFAULT_JOSM_CONSUMER_SECRET = "rIkjpPcBNkMQxrqzcOvOC4RRuYupYr7k8mfP13H5";
    /**
     * The old default JOSM OAuth consumer key.
     */
    static public final String OLD_JOSM_CONSUMER_KEY = "AdCRxTpvnbmfV8aPqrTLyA";
    /**
     * The old default JOSM OAuth consumer secret.
     */
    static public final String OLD_JOSM_CONSUMER_SECRET = "XmYOiGY9hApytcBC3xCec3e28QBqOWz5g6DSb5UpE";
    /**
     * The default OSM OAuth request token URL.
     */
    static public final String DEFAULT_REQUEST_TOKEN_URL = "http://www.openstreetmap.org/oauth/request_token";
    /**
     * The default OSM OAuth access token URL.
     */
    static public final String DEFAULT_ACCESS_TOKEN_URL = "http://www.openstreetmap.org/oauth/access_token";
    /**
     * The default OSM OAuth authorize URL.
     */
    static public final String DEFAULT_AUTHORISE_URL = "http://www.openstreetmap.org/oauth/authorize";


    /**
     * Replies a set of default parameters for a consumer accessing the standard OSM server
     * at {@link OsmApi#DEFAULT_API_URL}.
     *
     * @return a set of default parameters
     */
    static public OAuthParameters createDefault() {
        return createDefault(null);
    }

    /**
     * Replies a set of default parameters for a consumer accessing an OSM server
     * at the given API url. URL parameters are only set if the URL equals {@link OsmApi#DEFAULT_API_URL}
     * or references the domain "dev.openstreetmap.org", otherwise they may be <code>null</code>.
     * 
     * @param apiUrl The API URL for which the OAuth default parameters are created. If null or empty, the default OSM API url is used.
     * @return a set of default parameters for the given {@code apiUrl}
     * @since 5422
     */
    static public OAuthParameters createDefault(String apiUrl) {
        OAuthParameters parameters = new OAuthParameters();
        parameters.setConsumerKey(OLD_JOSM_CONSUMER_KEY);
        parameters.setConsumerSecret(OLD_JOSM_CONSUMER_SECRET);
        if (apiUrl == null || apiUrl.isEmpty() || apiUrl.equals(OsmApi.DEFAULT_API_URL)) {
            parameters.setRequestTokenUrl(DEFAULT_REQUEST_TOKEN_URL);
            parameters.setAccessTokenUrl(DEFAULT_ACCESS_TOKEN_URL);
            parameters.setAuthoriseUrl(DEFAULT_AUTHORISE_URL);
        } else {
            try {
                String host = new URL(apiUrl).getHost();
                if (host.endsWith("dev.openstreetmap.org")) {
                    parameters.setRequestTokenUrl(DEFAULT_REQUEST_TOKEN_URL.replace("www.openstreetmap.org", host));
                    parameters.setAccessTokenUrl(DEFAULT_ACCESS_TOKEN_URL.replace("www.openstreetmap.org", host));
                    parameters.setAuthoriseUrl(DEFAULT_AUTHORISE_URL.replace("www.openstreetmap.org", host));
                }
            } catch (MalformedURLException e) {
                // Ignored
            }
        }
        return parameters;
    }

    /**
     * Replies a set of parameters as defined in the preferences.
     *
     * @param pref the preferences
     * @return the parameters
     */
    static public OAuthParameters createFromPreferences(Preferences pref) {
        boolean useDefault = pref.getBoolean("oauth.settings.use-default", true );
        if (useDefault)
            return createDefault(pref.get("osm-server.url"));
        OAuthParameters parameters = new OAuthParameters();
        parameters.setConsumerKey(pref.get("oauth.settings.consumer-key", ""));
        parameters.setConsumerSecret(pref.get("oauth.settings.consumer-secret", ""));
        parameters.setRequestTokenUrl(pref.get("oauth.settings.request-token-url", ""));
        parameters.setAccessTokenUrl(pref.get("oauth.settings.access-token-url", ""));
        parameters.setAuthoriseUrl(pref.get("oauth.settings.authorise-url", ""));
        return parameters;
    }

    /**
     * Clears the preferences for OAuth parameters
     *
     * @param pref the preferences in which keys related to OAuth parameters are
     * removed
     */
    static public void clearPreferences(Preferences pref) {
        pref.put("oauth.settings.consumer-key", null);
        pref.put("oauth.settings.consumer-secret", null);
        pref.put("oauth.settings.request-token-url", null);
        pref.put("oauth.settings.access-token-url", null);
        pref.put("oauth.settings.authorise-url", null);
    }

    private String consumerKey;
    private String consumerSecret;
    private String requestTokenUrl;
    private String accessTokenUrl;
    private String authoriseUrl;

    /**
     * Constructs a new, unitialized, {@code OAuthParameters}.
     * 
     * @see #createDefault
     * @see #createFromPreferences
     */
    public OAuthParameters() {
    }

    /**
     * Creates a clone of the parameters in <code>other</code>.
     *
     * @param other the other parameters. Must not be null.
     * @throws IllegalArgumentException thrown if other is null
     */
    public OAuthParameters(OAuthParameters other) throws IllegalArgumentException{
        CheckParameterUtil.ensureParameterNotNull(other, "other");
        this.consumerKey = other.consumerKey;
        this.consumerSecret = other.consumerSecret;
        this.accessTokenUrl = other.accessTokenUrl;
        this.requestTokenUrl = other.requestTokenUrl;
        this.authoriseUrl = other.authoriseUrl;
    }

    /**
     * Gets the consumer key.
     * @return The consumer key
     */
    public String getConsumerKey() {
        return consumerKey;
    }
    
    /**
     * Sets the consumer key.
     * @param consumerKey The consumer key
     */
    public void setConsumerKey(String consumerKey) {
        this.consumerKey = consumerKey;
    }
    
    /**
     * Gets the consumer secret. 
     * @return The consumer secret
     */
    public String getConsumerSecret() {
        return consumerSecret;
    }
    
    /**
     * Sets the consumer secret.
     * @param consumerSecret The consumer secret
     */
    public void setConsumerSecret(String consumerSecret) {
        this.consumerSecret = consumerSecret;
    }
    
    /**
     * Gets the request token URL.
     * @return The request token URL
     */
    public String getRequestTokenUrl() {
        return requestTokenUrl;
    }
    
    /**
     * Sets the request token URL.
     * @param requestTokenUrl the request token URL
     */
    public void setRequestTokenUrl(String requestTokenUrl) {
        this.requestTokenUrl = requestTokenUrl;
    }
    
    /**
     * Gets the access token URL.
     * @return The access token URL
     */
    public String getAccessTokenUrl() {
        return accessTokenUrl;
    }
    
    /**
     * Sets the access token URL.
     * @param accessTokenUrl The access token URL
     */
    public void setAccessTokenUrl(String accessTokenUrl) {
        this.accessTokenUrl = accessTokenUrl;
    }
    
    /**
     * Gets the authorise URL.
     * @return The authorise URL
     */
    public String getAuthoriseUrl() {
        return authoriseUrl;
    }
    
    /**
     * Sets the authorise URL.
     * @param authoriseUrl The authorise URL
     */
    public void setAuthoriseUrl(String authoriseUrl) {
        this.authoriseUrl = authoriseUrl;
    }

    /**
     * Builds an {@link OAuthConsumer} based on these parameters.
     *
     * @return the consumer
     */
    public OAuthConsumer buildConsumer() {
        return new DefaultOAuthConsumer(consumerKey, consumerSecret);
    }

    /**
     * Builds an {@link OAuthProvider} based on these parameters and a OAuth consumer <code>consumer</code>.
     *
     * @param consumer the consumer. Must not be null.
     * @return the provider
     * @throws IllegalArgumentException if consumer is null
     */
    public OAuthProvider buildProvider(OAuthConsumer consumer) throws IllegalArgumentException {
        CheckParameterUtil.ensureParameterNotNull(consumer, "consumer");
        return new DefaultOAuthProvider(
                requestTokenUrl,
                accessTokenUrl,
                authoriseUrl
        );
    }

    /**
     * Saves these OAuth parameters to the given {@code Preferences}.
     * @param pref The Preferences into which are saved these OAuth parameters with the prefix "oauth.settings"
     */
    public void saveToPreferences(Preferences pref) {
        if (this.equals(createDefault(pref.get("osm-server.url")))) {
            pref.put("oauth.settings.use-default", true );
            clearPreferences(pref);
            return;
        }
        pref.put("oauth.settings.use-default", false);
        pref.put("oauth.settings.consumer-key", consumerKey);
        pref.put("oauth.settings.consumer-secret", consumerSecret);
        pref.put("oauth.settings.request-token-url", requestTokenUrl);
        pref.put("oauth.settings.access-token-url", accessTokenUrl);
        pref.put("oauth.settings.authorise-url", authoriseUrl);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((accessTokenUrl == null) ? 0 : accessTokenUrl.hashCode());
        result = prime * result + ((authoriseUrl == null) ? 0 : authoriseUrl.hashCode());
        result = prime * result + ((consumerKey == null) ? 0 : consumerKey.hashCode());
        result = prime * result + ((consumerSecret == null) ? 0 : consumerSecret.hashCode());
        result = prime * result + ((requestTokenUrl == null) ? 0 : requestTokenUrl.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        OAuthParameters other = (OAuthParameters) obj;
        if (accessTokenUrl == null) {
            if (other.accessTokenUrl != null)
                return false;
        } else if (!accessTokenUrl.equals(other.accessTokenUrl))
            return false;
        if (authoriseUrl == null) {
            if (other.authoriseUrl != null)
                return false;
        } else if (!authoriseUrl.equals(other.authoriseUrl))
            return false;
        if (consumerKey == null) {
            if (other.consumerKey != null)
                return false;
        } else if (!consumerKey.equals(other.consumerKey))
            return false;
        if (consumerSecret == null) {
            if (other.consumerSecret != null)
                return false;
        } else if (!consumerSecret.equals(other.consumerSecret))
            return false;
        if (requestTokenUrl == null) {
            if (other.requestTokenUrl != null)
                return false;
        } else if (!requestTokenUrl.equals(other.requestTokenUrl))
            return false;
        return true;
    }
}
