package com.operion.messaging.ws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Live message delivery for #239 - a client subscribes to /topic/threads/{id} after
 * MessagingController opens/looks up that thread over plain REST, and
 * SimpMessagingBroadcaster pushes each new Message there as MessagingService.sendMessage
 * saves it. Auth happens per-STOMP-frame in StompAuthenticationInterceptor, not at the
 * HTTP handshake - see that class's doc for why. /ws isn't under /api/v1/**, so neither
 * JwtAuthenticationInterceptor nor PermissionInterceptor (both registered only against
 * that prefix in WebConfig) ever run against it.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final StompAuthenticationInterceptor stompAuthenticationInterceptor;

	/** Same default/source as WebConfig.allowedOrigins - kept as a separate @Value binding
	 * since StompEndpointRegistry's origin API is shaped differently from CorsRegistry's. */
	@Value("${app.cors.allowed-origins:http://localhost:5173}")
	private String[] allowedOrigins;

	public WebSocketConfig(StompAuthenticationInterceptor stompAuthenticationInterceptor) {
		this.stompAuthenticationInterceptor = stompAuthenticationInterceptor;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins).withSockJS();
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic");
		registry.setApplicationDestinationPrefixes("/app");
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(stompAuthenticationInterceptor);
	}
}
