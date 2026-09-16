package com.dharanijayachandran.clinicbooking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket for live slot availability.
 *
 * Uses the built-in simple broker rather than relaying to RabbitMQ or
 * ActiveMQ. That is a deliberate, and limiting, choice: the simple broker
 * keeps subscriptions in this JVM's memory, so with more than one backend
 * instance a client connected to instance A would never see events published
 * on instance B. Single instance today, so it's the right trade; the moment
 * this scales horizontally it needs a real broker relay (or Redis pub/sub in
 * front of it). Worth knowing before someone asks.
 *
 * Traffic is one-way: the server publishes to /topic/**, and clients only
 * subscribe. No @MessageMapping handlers exist, so there is no client-to-
 * server command surface to secure beyond the handshake — which does go
 * through the normal servlet filter chain, JWT cookie and all.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Raw WebSocket, no SockJS: the fallbacks only matter for browsers
        // without WebSocket support, which is no longer a meaningful group,
        // and they add a dependency plus extra HTTP chatter.
        //
        // Same-origin in both dev and production (proxy / nginx), so no
        // allowed-origins list is needed here.
        registry.addEndpoint("/ws");
    }
}
