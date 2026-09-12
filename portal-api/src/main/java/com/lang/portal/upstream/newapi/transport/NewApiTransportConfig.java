package com.lang.portal.upstream.newapi.transport;

import com.lang.portal.config.PortalCommonProperties;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
public class NewApiTransportConfig {

  @Bean(destroyMethod = "dispose")
  public ConnectionProvider newApiConnectionProvider(PortalCommonProperties properties) {
    var pool = properties.upstream().newApi().pool();
    return ConnectionProvider.builder("new-api")
        .maxConnections(pool.maxConnections())
        .pendingAcquireMaxCount(pool.maxPendingAcquire())
        .pendingAcquireTimeout(pool.acquireTimeout())
        .maxIdleTime(pool.maxIdleTime())
        .maxLifeTime(pool.maxLifeTime())
        .evictInBackground(Duration.ofSeconds(30))
        .build();
  }

  @Bean
  public HttpClient newApiHttpClient(PortalCommonProperties properties, ConnectionProvider provider) {
    var api = properties.upstream().newApi();
    return HttpClient.create(provider)
        .disableRetry(true)
        .option(
            io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS,
            (int) api.connectTimeout().toMillis())
        .responseTimeout(api.readTimeout())
        .doOnConnected(conn -> conn.addHandlerLast(
            new WriteTimeoutHandler(api.writeTimeout().toMillis(), TimeUnit.MILLISECONDS)));
  }

  @Bean
  public RestClient newApiRestClient(HttpClient httpClient) {
    ReactorClientHttpRequestFactory factory = new ReactorClientHttpRequestFactory(httpClient);
    return RestClient.builder().requestFactory(factory).build();
  }
}
