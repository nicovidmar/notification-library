package dev.challenge.notifications.adapters.sendgrid;

/** Minimal provider response: a 202 requires neither a body nor a remote identifier. */
record SendGridResponse(int statusCode) { }
