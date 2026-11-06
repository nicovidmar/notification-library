package dev.challenge.notifications.domain;

/**
 * An immutable, channel-specific notification. Implementations validate their inputs
 * before any sender is invoked and avoid exposing content in diagnostic strings.
 * This interface is open so consumers can define additional channels.
 */
public interface Notification { }
