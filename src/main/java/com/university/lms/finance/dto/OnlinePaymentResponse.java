package com.university.lms.finance.dto;

import java.util.UUID;

/** E7: the hosted checkout page to send the browser to. Never a card field — see PaymentGateway's own javadoc. */
public record OnlinePaymentResponse(UUID pendingPaymentId, String redirectUrl) {}
