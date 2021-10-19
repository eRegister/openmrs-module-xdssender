package org.openmrs.module.xdssender.api.xds;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.openmrs.module.xdssender.XdsSenderConfig;
import org.openmrs.module.xdssender.api.service.impl.CcdHttpResult;
import org.openmrs.module.xdssender.api.service.impl.RestHttpResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

@Component("xdssender.RestRetriever")
public class RestRetriever {

	private static final Logger LOGGER = LoggerFactory.getLogger(XdsRetriever.class);
	private static final String customAttributes = "custom:(display,concept,person,obsDatetime,location,groupMembers,value)";

	@Autowired
	private XdsSenderConfig config;

	public RestHttpResult sendRetrieveObs(String patientIdentifier)  {
		try {
			HttpClient httpclient;
			if (config.getExportCcdIgnoreCerts()) {
				Scheme httpScheme = new Scheme("http", 80, PlainSocketFactory.getSocketFactory());
				SchemeRegistry schemeRegistry = new SchemeRegistry();
				schemeRegistry.register(httpScheme);

				ClientConnectionManager cm = new PoolingClientConnectionManager(schemeRegistry);
				httpclient = new DefaultHttpClient(cm);
			} else {
				httpclient = new DefaultHttpClient();
			}
			HttpGet httpGet = new HttpGet(config.getExportObsEndpoint() + "?patient=" + patientIdentifier
					+ "&identifierDomain=" + config.getCodeNationalRoot() + "&v=" + customAttributes);
			interceptAuthorization(httpGet);
			return new RestHttpResult(httpclient.execute(httpGet));
		} catch (Exception ex) {
			LOGGER.error("Error when fetching SHR observations", ex);
			return new RestHttpResult(ex);
		}
	}

	private SSLSocketFactory createSSLFactoryIgnoringCert() throws NoSuchAlgorithmException, KeyStoreException,
			KeyManagementException, UnrecoverableKeyException {
		return new SSLSocketFactory(new TrustStrategy() {
			@Override
			public boolean isTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
				return true;
			}
		}, new AllowAllHostnameVerifier());
	}

	private void interceptAuthorization(HttpGet httpGet) {
		Charset charset = Charset.forName("US-ASCII");
		// String auth = config.getOshrUsername() + ":" + config.getOshrPassword();
		String auth = config.getXdsRepositoryUsername() + ":" + config.getXdsRepositoryPassword();
		byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(charset));
		String authHeader = "Basic " + new String(encodedAuth, charset);
		httpGet.setHeader("Authorization", authHeader);
	}
}
